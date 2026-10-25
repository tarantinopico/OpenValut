package com.openvault.app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClipboardSecurityHelper(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    companion object {
        const val DEFAULT_CLEAR_SECONDS = 30
        private const val EXTRA_IS_SENSITIVE_COMPAT = "android.content.extra.IS_SENSITIVE"
    }

    private var clearJob: Job? = null
    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    fun copySensitiveText(
        label: String,
        secretText: String,
        autoClearEnabled: Boolean = true,
        clearAfterSeconds: Int = DEFAULT_CLEAR_SECONDS
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clipData = ClipData.newPlainText(label, secretText)
        val extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean(EXTRA_IS_SENSITIVE_COMPAT, true)
            }
        }
        clipData.description.extras = extras
        clipboard.setPrimaryClip(clipData)

        clearJob?.cancel()
        if (autoClearEnabled) {
            clearJob = scope.launch {
                for (sec in clearAfterSeconds downTo 1) {
                    _remainingSeconds.value = sec
                    delay(1_000L)
                }
                _remainingSeconds.value = 0
                clearClipboardNow()
            }
        } else {
            _remainingSeconds.value = 0
        }
    }

    fun clearClipboardNow() {
        clearJob?.cancel()
        _remainingSeconds.value = 0
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {
        }
    }
}
