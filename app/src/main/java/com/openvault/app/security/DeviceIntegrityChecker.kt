package com.openvault.app.security

import android.content.Context
import android.os.Build
import java.io.File

data class DeviceIntegrityStatus(
    val isRootDetected: Boolean,
    val isHookFrameworkDetected: Boolean,
    val isTestKeysBuild: Boolean,
    val isStrongBoxAvailable: Boolean,
    val isBiometricReady: Boolean,
    val findings: List<String>
) {
    val isCompromised: Boolean
        get() = isRootDetected || isHookFrameworkDetected || isTestKeysBuild
}

class DeviceIntegrityChecker(
    private val context: Context,
    private val biometricKeyManager: BiometricKeyManager = BiometricKeyManager(context)
) {
    private val suPaths = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/sbin/.magisk",
        "/cache/.disable_magisk"
    )

    fun evaluateDeviceIntegrity(): DeviceIntegrityStatus {
        val findings = mutableListOf<String>()

        val foundSuPaths = suPaths.filter { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
        val isRoot = foundSuPaths.isNotEmpty()
        if (isRoot) {
            findings.add("Detekovány binární soubory su/Magisk (${foundSuPaths.first()})")
        }

        val buildTags = Build.TAGS ?: ""
        val isTestKeys = buildTags.contains("test-keys")
        if (isTestKeys) {
            findings.add("Systémový obraz je podepsán testovacími klíči (test-keys)")
        }

        val isHooked = detectHooksInProcMaps()
        if (isHooked) {
            findings.add("V paměti procesu (/proc/self/maps) detekován framework Frida nebo Xposed")
        }

        val strongBox = biometricKeyManager.isStrongBoxSupported()
        val bioReady = biometricKeyManager.isBiometricHardwareAvailable()

        return DeviceIntegrityStatus(
            isRootDetected = isRoot,
            isHookFrameworkDetected = isHooked,
            isTestKeysBuild = isTestKeys,
            isStrongBoxAvailable = strongBox,
            isBiometricReady = bioReady,
            findings = findings
        )
    }

    private fun detectHooksInProcMaps(): Boolean {
        return try {
            val mapsFile = File("/proc/self/maps")
            if (!mapsFile.exists() || !mapsFile.canRead()) return false
            mapsFile.useLines { lines ->
                lines.any { line ->
                    val lower = line.lowercase()
                    lower.contains("frida") ||
                        lower.contains("xposed") ||
                        lower.contains("substrate") ||
                        lower.contains("lsposed")
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
