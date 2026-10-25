package com.openvault.app.ui

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openvault.app.data.local.SyncQueueEntity
import com.openvault.app.di.AppContainer
import com.openvault.app.domain.model.AuditCategory
import com.openvault.app.domain.model.AuditEntry
import com.openvault.app.domain.model.CalendarEvent
import com.openvault.app.domain.model.CollectionItem
import com.openvault.app.domain.model.DatabaseFieldSchema
import com.openvault.app.domain.model.DatabaseRecord
import com.openvault.app.domain.model.Note
import com.openvault.app.domain.model.NoteRevision
import com.openvault.app.domain.model.PasswordItem
import com.openvault.app.domain.model.RecurrenceRule
import com.openvault.app.domain.model.SecurityReport
import com.openvault.app.domain.model.UserDatabase
import com.openvault.app.domain.model.VaultFile
import com.openvault.app.domain.model.VaultSettings
import com.openvault.app.domain.usecase.SystemCalendarBridge
import com.openvault.app.notification.QuickCaptureNotificationManager
import com.openvault.app.security.DeviceIntegrityStatus
import com.openvault.app.security.DuressAction
import com.openvault.app.security.OvaultImportMode
import com.openvault.app.security.UnlockResult
import com.openvault.app.security.VaultLockState
import com.openvault.app.sync.SyncStatus
import com.openvault.app.sync.SyncWorker
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import java.util.UUID
import javax.crypto.Cipher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OpenVaultViewModel(
    val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    val lockState: StateFlow<VaultLockState> = container.securityGate.lockState
    val syncStatus: StateFlow<SyncStatus> = container.syncEngine.syncState
    val clipboardRemainingSeconds: StateFlow<Int> = container.clipboardHelper.remainingSeconds

    val settings: StateFlow<VaultSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultSettings())

    val notes: StateFlow<List<Note>> = container.repository.activeNotesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val passwords: StateFlow<List<PasswordItem>> = container.repository.activePasswordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<CalendarEvent>> = container.repository.activeEventsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<VaultFile>> = container.repository.activeFilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val databases: StateFlow<List<UserDatabase>> = container.repository.activeDatabasesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val records: StateFlow<List<DatabaseRecord>> = container.repository.activeRecordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<CollectionItem>> = container.repository.activeCollectionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditEntries: StateFlow<List<AuditEntry>> = container.repository.auditEntriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _navigationStack = MutableStateFlow<List<VaultScreen>>(
        listOf(determineStartScreen(container.securityGate.lockState.value))
    )
    val currentScreen: StateFlow<VaultScreen> = MutableStateFlow<VaultScreen>(
        determineStartScreen(container.securityGate.lockState.value)
    ).also { current ->
        viewModelScope.launch {
            _navigationStack.collect { stack ->
                current.value = stack.lastOrNull() ?: VaultScreen.Dashboard
            }
        }
    }

    private val _activeModalSheet = MutableStateFlow<VaultModalSheet?>(null)
    val activeModalSheet: StateFlow<VaultModalSheet?> = _activeModalSheet.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isBusyOperation = MutableStateFlow(false)
    val isBusyOperation: StateFlow<Boolean> = _isBusyOperation.asStateFlow()

    private val _busyOperationStep = MutableStateFlow("")
    val busyOperationStep: StateFlow<String> = _busyOperationStep.asStateFlow()

    private val _securityReport = MutableStateFlow<SecurityReport?>(null)
    val securityReport: StateFlow<SecurityReport?> = _securityReport.asStateFlow()

    private val _deviceIntegrity = MutableStateFlow(
        DeviceIntegrityStatus(
            isRootDetected = false,
            isHookFrameworkDetected = false,
            isTestKeysBuild = false,
            isStrongBoxAvailable = false,
            isBiometricReady = false,
            findings = emptyList()
        )
    )
    val deviceIntegrity: StateFlow<DeviceIntegrityStatus> = _deviceIntegrity.asStateFlow()

    private val _supabaseDiagnosticText = MutableStateFlow<String?>(null)
    val supabaseDiagnosticText: StateFlow<String?> = _supabaseDiagnosticText.asStateFlow()

    private val _lastExportedOvaultBytes = MutableStateFlow<ByteArray?>(null)
    val lastExportedOvaultBytes: StateFlow<ByteArray?> = _lastExportedOvaultBytes.asStateFlow()

    private var inactivityCheckJob: Job? = null

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                _deviceIntegrity.value = container.deviceIntegrityChecker.evaluateDeviceIntegrity()
            }
        }
        // React to lockState changes automatically
        viewModelScope.launch {
            lockState.collect { state ->
                when (state) {
                    is VaultLockState.Uninitialized -> {
                        _navigationStack.value = listOf(VaultScreen.OnboardingWelcome)
                        container.remoteDataSource.stopRealtimeSubscription()
                        QuickCaptureNotificationManager.dismiss(appContext)
                    }
                    is VaultLockState.Locked -> {
                        val current = _navigationStack.value.lastOrNull()
                        if (current !is VaultScreen.UnlockBiometric && current !is VaultScreen.UnlockPassword) {
                            val target = if (state.biometricEnabled && !state.requirePasswordAfterReboot) {
                                VaultScreen.UnlockBiometric
                            } else {
                                VaultScreen.UnlockPassword
                            }
                            _navigationStack.value = listOf(target)
                        }
                        container.remoteDataSource.stopRealtimeSubscription()
                        QuickCaptureNotificationManager.dismiss(appContext)
                    }
                    is VaultLockState.Unlocked -> {
                        container.repository.refreshDatabaseSessionForUnlockState()
                        val cur = _navigationStack.value.lastOrNull()
                        if (cur is VaultScreen.UnlockBiometric ||
                            cur is VaultScreen.UnlockPassword ||
                            cur is VaultScreen.OnboardingWelcome ||
                            cur is VaultScreen.OnboardingMasterPassword
                        ) {
                            if (cur !is VaultScreen.OnboardingBiometrics) {
                                _navigationStack.value = listOf(VaultScreen.Dashboard)
                            }
                        }
                        val currentSettings = settings.value
                        QuickCaptureNotificationManager.showIfEnabled(
                            context = appContext,
                            enabled = currentSettings.persistentNotificationEnabled,
                            isUnlocked = true
                        )
                        if (currentSettings.realtimeSyncEnabled && !state.isDecoyMode) {
                            container.remoteDataSource.startRealtimeSubscription(state.vaultId)
                        }
                        if (!state.isDecoyMode) {
                            SyncWorker.schedulePeriodicSync(
                                context = appContext,
                                intervalMinutes = currentSettings.syncIntervalMinutes,
                                wifiOnly = currentSettings.syncWifiOnly
                            )
                            launch {
                                container.syncEngine.performSync(
                                    conflictStrategy = currentSettings.conflictStrategy
                                )
                            }
                        }
                    }
                }
            }
        }

        // Listen for Realtime WebSocket events and trigger delta sync
        viewModelScope.launch {
            container.remoteDataSource.realtimeEvents.collect {
                if (container.securityGate.isUnlocked() && !container.securityGate.isDecoyModeActive()) {
                    container.syncEngine.performSync(
                        conflictStrategy = settings.value.conflictStrategy
                    )
                }
            }
        }

        // Auto-lock inactivity watchdog
        inactivityCheckJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                if (container.securityGate.isUnlocked()) {
                    val timeoutMin = settings.value.autoLockTimeoutMinutes.coerceIn(1, 30)
                    val idleMs = System.currentTimeMillis() - container.securityGate.lastActivityTimestampMs.value
                    if (idleMs >= timeoutMin * 60_000L) {
                        lockVaultImmediately(reason = "Automatické uzamčení po $timeoutMin min nečinnosti")
                    }
                }
            }
        }
    }

    private fun determineStartScreen(state: VaultLockState): VaultScreen = when (state) {
        is VaultLockState.Uninitialized -> VaultScreen.OnboardingWelcome
        is VaultLockState.Locked -> {
            if (state.biometricEnabled && !state.requirePasswordAfterReboot) {
                VaultScreen.UnlockBiometric
            } else {
                VaultScreen.UnlockPassword
            }
        }
        is VaultLockState.Unlocked -> VaultScreen.Dashboard
    }

    fun recordUserInteraction() {
        container.securityGate.recordUserActivity()
    }

    fun showStatusMessage(message: String?) {
        _statusBannerMessage.value = message
    }

    fun navigateTo(screen: VaultScreen, clearStack: Boolean = false) {
        recordUserInteraction()
        if (clearStack) {
            _navigationStack.value = listOf(screen)
        } else {
            _navigationStack.value = (_navigationStack.value + screen).takeLast(15)
        }
    }

    fun navigateBack(): Boolean {
        recordUserInteraction()
        if (_activeModalSheet.value != null) {
            _activeModalSheet.value = null
            return true
        }
        val currentStack = _navigationStack.value
        return if (currentStack.size > 1) {
            _navigationStack.value = currentStack.dropLast(1)
            true
        } else {
            false
        }
    }

    fun openModalSheet(sheet: VaultModalSheet?) {
        recordUserInteraction()
        _activeModalSheet.value = sheet
    }

    fun handleIncomingActionIntent(action: String?) {
        if (action.isNullOrBlank()) return
        if (!container.securityGate.isUnlocked()) return
        when (action) {
            QuickCaptureNotificationManager.ACTION_QUICK_NOTE -> navigateTo(VaultScreen.NoteEditor())
            QuickCaptureNotificationManager.ACTION_QUICK_EVENT -> navigateTo(VaultScreen.EventEditor())
            QuickCaptureNotificationManager.ACTION_QUICK_PASSWORD -> navigateTo(VaultScreen.PasswordDetailEditor())
            QuickCaptureNotificationManager.ACTION_OPEN_AUDIT -> navigateTo(VaultScreen.AuditLog)
            QuickCaptureNotificationManager.ACTION_OPEN_CALENDAR -> navigateTo(VaultScreen.CalendarMonth)
            QuickCaptureNotificationManager.ACTION_OPEN_NOTES -> navigateTo(VaultScreen.NotesList)
        }
    }

    // --- ONBOARDING & AUTHENTICATION ---
    fun createNewVault(
        masterPassword: String,
        customVaultId: String? = null,
        proceedToBiometrics: Boolean = true
    ) {
        viewModelScope.launch {
            _isBusyOperation.value = true
            _busyOperationStep.value = "Odvozování 256-bit Master Key (Argon2id + PBKDF2 210k)…"
            try {
                val vaultId = container.securityGate.initializeNewVault(
                    masterPassword = masterPassword,
                    customVaultId = customVaultId?.trim()?.takeIf { it.isNotEmpty() }
                        ?: UUID.randomUUID().toString()
                )
                container.repository.refreshDatabaseSessionForUnlockState()
                _busyOperationStep.value = "Registrace šifrovaného trezoru v Supabase…"
                val integrityHash = container.securityGate.computeIntegrityHash(vaultId.encodeToByteArray())
                container.syncEngine.syncVaultHeader(settings.value, integrityHash)
                container.repository.logAuditEvent(
                    category = AuditCategory.AUTH,
                    actionType = "VAULT_INITIALIZED",
                    summary = "Inicializován nový E2E šifrovaný trezor (ID: ${vaultId.take(8)}…)"
                )
                if (proceedToBiometrics) {
                    navigateTo(VaultScreen.OnboardingBiometrics, clearStack = true)
                } else {
                    navigateTo(VaultScreen.Dashboard, clearStack = true)
                }
            } finally {
                _isBusyOperation.value = false
            }
        }
    }

    fun finishOnboardingToDashboard() {
        navigateTo(VaultScreen.Dashboard, clearStack = true)
    }

    fun unlockWithMasterPassword(password: String, onResult: (UnlockResult) -> Unit = {}) {
        viewModelScope.launch {
            _isBusyOperation.value = true
            _busyOperationStep.value = "Ověřování Master hesla a rozbalování Vault Key…"
            try {
                val result = container.securityGate.unlockWithPassword(password)
                when (result) {
                    is UnlockResult.Success -> {
                        container.repository.refreshDatabaseSessionForUnlockState()
                        container.repository.logAuditEvent(
                            category = AuditCategory.AUTH,
                            actionType = if (result.isDecoyMode) "VAULT_UNLOCKED_DECOY" else "VAULT_UNLOCKED_PASSWORD",
                            summary = "Trezor úspěšně odemčen pomocí Master hesla"
                        )
                        navigateTo(VaultScreen.Dashboard, clearStack = true)
                    }
                    is UnlockResult.InvalidCredentials -> {
                        container.repository.logAuditEvent(
                            category = AuditCategory.AUTH,
                            actionType = "VAULT_UNLOCK_FAILED",
                            summary = "Neúspěšný pokus o odemknutí trezoru (neplatný GCM tag)"
                        )
                    }
                    is UnlockResult.RateLimited -> Unit
                    is UnlockResult.BiometricRequiresPasswordAfterReboot -> Unit
                    is UnlockResult.DuressWipeTriggered -> {
                        showStatusMessage("Nouzové vymazání lokálních klíčů provedeno.")
                    }
                }
                onResult(result)
            } finally {
                _isBusyOperation.value = false
            }
        }
    }

    fun getBiometricEncryptCryptoObject(): BiometricPrompt.CryptoObject? =
        container.securityGate.getBiometricEncryptCryptoObject()

    fun completeBiometricEnrollment(cipher: Cipher) {
        viewModelScope.launch {
            val ok = container.securityGate.enableBiometricWithAuthenticatedCipher(cipher)
            if (ok) {
                container.repository.logAuditEvent(
                    category = AuditCategory.SECURITY,
                    actionType = "BIOMETRIC_ENABLED",
                    summary = "Aktivováno biometrické odemykání (Android Keystore CryptoObject)"
                )
                showStatusMessage("Biometrické odemykání bylo úspěšně aktivováno.")
            }
        }
    }

    fun getBiometricDecryptCryptoObject(): BiometricPrompt.CryptoObject? =
        container.securityGate.getBiometricDecryptCryptoObject()

    fun unlockWithBiometricAuthenticatedCipher(cipher: Cipher, onFailure: () -> Unit) {
        viewModelScope.launch {
            val result = container.securityGate.unlockWithBiometricCipher(cipher)
            if (result is UnlockResult.Success) {
                container.repository.refreshDatabaseSessionForUnlockState()
                container.repository.logAuditEvent(
                    category = AuditCategory.AUTH,
                    actionType = "VAULT_UNLOCKED_BIOMETRIC",
                    summary = "Trezor odemčen hardwarově vázanou biometrií (CryptoObject)"
                )
                navigateTo(VaultScreen.Dashboard, clearStack = true)
            } else {
                onFailure()
                navigateTo(VaultScreen.UnlockPassword, clearStack = true)
            }
        }
    }

    fun lockVaultImmediately(reason: String = "Ruční uzamčení uživatelem") {
        viewModelScope.launch {
            if (container.securityGate.isUnlocked()) {
                runCatching {
                    container.repository.logAuditEvent(
                        category = AuditCategory.AUTH,
                        actionType = "VAULT_LOCKED",
                        summary = "$reason – Vault Key v RAM přepsán nulami"
                    )
                }
            }
            _activeModalSheet.value = null
            container.securityGate.lock()
            container.repository.refreshDatabaseSessionForUnlockState()
            QuickCaptureNotificationManager.dismiss(appContext)
        }
    }

    // --- CLIPBOARD ---
    fun copySecretToClipboard(label: String, secret: String) {
        recordUserInteraction()
        val autoClear = settings.value.clipboardAutoClear30s
        container.clipboardHelper.copySensitiveText(
            label = label,
            secretText = secret,
            autoClearEnabled = autoClear,
            clearAfterSeconds = 30
        )
        viewModelScope.launch {
            container.repository.logAuditEvent(
                category = AuditCategory.SECURITY,
                actionType = "SECRET_COPIED_TO_CLIPBOARD",
                summary = "Zkopírována citlivá položka '$label' (automatické vymazání za 30 s: $autoClear)"
            )
        }
        showStatusMessage("$label zkopírováno do schránky (automatické smazání za 30 s).")
    }

    // --- SYNC ---
    fun triggerManualSync(forceFull: Boolean = false) {
        viewModelScope.launch {
            val status = container.syncEngine.performSync(
                forceFull = forceFull,
                conflictStrategy = settings.value.conflictStrategy
            )
            when (status) {
                is SyncStatus.Synced -> showStatusMessage("Synchronizace se Supabase dokončena.")
                is SyncStatus.ConflictOrWarning -> showStatusMessage(status.message)
                is SyncStatus.Offline -> showStatusMessage("Režim offline – změny jsou bezpečně uloženy ve frontě SyncQueue.")
                else -> Unit
            }
        }
    }

    suspend fun getPendingSyncQueueItems(): List<SyncQueueEntity> =
        runCatching { container.repository.getActiveDao().getPendingSyncQueue() }.getOrDefault(emptyList())

    // --- CRUD ACTIONS ---
    fun saveNote(
        id: String? = null,
        title: String,
        body: String,
        tags: List<String> = emptyList(),
        collectionId: String? = null,
        attachmentFileIds: List<String> = emptyList(),
        linkedEventIds: List<String> = emptyList(),
        isPinned: Boolean = false,
        isFavorite: Boolean = false,
        colorHex: String? = null,
        templateName: String? = null,
        onSaved: (Note) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = container.repository.saveNote(
                id = id,
                title = title,
                body = body,
                tags = tags,
                collectionId = collectionId,
                attachmentFileIds = attachmentFileIds,
                linkedEventIds = linkedEventIds,
                isPinned = isPinned,
                isFavorite = isFavorite,
                colorHex = colorHex,
                templateName = templateName
            )
            onSaved(saved)
        }
    }

    fun toggleNotePinned(noteId: String) {
        viewModelScope.launch { container.repository.toggleNotePinned(noteId) }
    }

    fun toggleNoteFavorite(noteId: String) {
        viewModelScope.launch { container.repository.toggleNoteFavorite(noteId) }
    }

    fun restoreNoteRevision(noteId: String, revision: NoteRevision) {
        viewModelScope.launch {
            container.repository.restoreNoteRevision(noteId, revision)
            showStatusMessage("Obnovena revize #${revision.rev}.")
        }
    }

    fun duplicateNote(noteId: String) {
        viewModelScope.launch {
            container.repository.duplicateNote(noteId)
            showStatusMessage("Poznámka byla duplikována.")
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            container.repository.softDeleteNote(noteId)
            showStatusMessage("Poznámka smazána.")
        }
    }

    fun savePassword(
        id: String? = null,
        title: String,
        username: String,
        password: String,
        url: String = "",
        totpSecret: String? = null,
        notes: String = "",
        category: String = "Ostatní",
        collectionId: String? = null,
        isFavorite: Boolean = false,
        expiresAt: Long? = null,
        onSaved: (PasswordItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = container.repository.savePassword(
                id = id,
                title = title,
                username = username,
                password = password,
                url = url,
                totpSecret = totpSecret,
                notes = notes,
                category = category,
                collectionId = collectionId,
                isFavorite = isFavorite,
                expiresAt = expiresAt
            )
            onSaved(saved)
        }
    }

    fun togglePasswordFavorite(passwordId: String) {
        viewModelScope.launch { container.repository.togglePasswordFavorite(passwordId) }
    }

    fun deletePassword(passwordId: String) {
        viewModelScope.launch {
            container.repository.softDeletePassword(passwordId)
            showStatusMessage("Heslo smazáno.")
        }
    }

    fun saveEvent(
        id: String? = null,
        title: String,
        description: String = "",
        startAt: Long,
        endAt: Long,
        allDay: Boolean = false,
        remindersMinutesBefore: List<Int> = listOf(15),
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        linkedNoteIds: List<String> = emptyList(),
        linkedRecordIds: List<String> = emptyList(),
        colorHex: String? = "#10B981",
        onSaved: (CalendarEvent) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = container.repository.saveCalendarEvent(
                id = id,
                title = title,
                description = description,
                startAt = startAt,
                endAt = endAt,
                allDay = allDay,
                remindersMinutesBefore = remindersMinutesBefore,
                recurrenceRule = recurrenceRule,
                linkedNoteIds = linkedNoteIds,
                linkedRecordIds = linkedRecordIds,
                colorHex = colorHex
            )
            if (settings.value.systemCalendarSyncEnabled) {
                exportEventToSystemCalendar(saved)
            }
            onSaved(saved)
        }
    }

    fun exportEventToSystemCalendar(event: CalendarEvent) {
        viewModelScope.launch {
            val curSettings = settings.value
            val sysId = SystemCalendarBridge.exportEventToSystemCalendar(
                context = appContext,
                event = event,
                calendarId = curSettings.targetSystemCalendarId,
                anonymizeTitle = curSettings.anonymizeExportedEvents
            )
            if (sysId != null) {
                container.repository.saveCalendarEvent(
                    id = event.id,
                    title = event.title,
                    description = event.description,
                    startAt = event.startAt,
                    endAt = event.endAt,
                    allDay = event.allDay,
                    remindersMinutesBefore = event.remindersMinutesBefore,
                    recurrenceRule = event.recurrenceRule,
                    linkedNoteIds = event.linkedNoteIds,
                    linkedRecordIds = event.linkedRecordIds,
                    systemCalendarEventId = sysId,
                    colorHex = event.colorHex
                )
                showStatusMessage("Událost exportována do systémového kalendáře.")
            } else {
                showStatusMessage("Pro export povolte oprávnění kalendáře v Nastavení → Kalendář.")
            }
        }
    }

    fun importEventsFromSystemCalendar() {
        viewModelScope.launch {
            val vaultId = container.securityGate.getVaultIdOrNull() ?: return@launch
            val imported = SystemCalendarBridge.importUpcomingEventsFromSystemCalendar(appContext, vaultId)
            for (ev in imported) {
                container.repository.saveCalendarEvent(
                    title = ev.title,
                    description = ev.description,
                    startAt = ev.startAt,
                    endAt = ev.endAt,
                    allDay = ev.allDay,
                    systemCalendarEventId = ev.systemCalendarEventId
                )
            }
            showStatusMessage("Importováno ${imported.size} událostí ze systémového kalendáře.")
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            container.repository.softDeleteCalendarEvent(eventId)
            showStatusMessage("Událost smazána.")
        }
    }

    fun uploadFileToVfs(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        folderId: String? = null
    ) {
        viewModelScope.launch {
            _isBusyOperation.value = true
            _busyOperationStep.value = "Šifrování souboru pomocí AES-256-GCM a ukládání do VFS…"
            try {
                container.repository.uploadAndEncryptFile(
                    fileName = fileName,
                    mimeType = mimeType,
                    plainBytes = bytes,
                    folderId = folderId
                )
                showStatusMessage("Soubor '$fileName' zašifrován a uložen.")
            } finally {
                _isBusyOperation.value = false
            }
        }
    }

    fun renameOrMoveFile(fileId: String, newName: String, newFolderId: String?) {
        viewModelScope.launch {
            container.repository.renameOrMoveVaultFile(fileId, newName, newFolderId)
            showStatusMessage("Soubor aktualizován.")
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            container.repository.softDeleteVaultFile(fileId)
            showStatusMessage("Šifrovaný soubor smazán.")
        }
    }

    fun saveUserDatabase(
        id: String? = null,
        name: String,
        description: String,
        icon: String = "database",
        schema: List<DatabaseFieldSchema>,
        onSaved: (UserDatabase) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = container.repository.saveUserDatabase(id, name, description, icon, schema)
            onSaved(saved)
        }
    }

    fun deleteUserDatabase(databaseId: String) {
        viewModelScope.launch {
            container.repository.softDeleteUserDatabase(databaseId)
            showStatusMessage("Databáze smazána.")
        }
    }

    fun saveDatabaseRecord(
        id: String? = null,
        databaseId: String,
        values: Map<String, String>,
        isFavorite: Boolean = false,
        onSaved: (DatabaseRecord) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = container.repository.saveDatabaseRecord(id, databaseId, values, isFavorite)
            onSaved(saved)
        }
    }

    fun deleteDatabaseRecord(recordId: String) {
        viewModelScope.launch {
            container.repository.softDeleteDatabaseRecord(recordId)
            showStatusMessage("Záznam smazán.")
        }
    }

    fun saveCollection(
        id: String? = null,
        name: String,
        parentId: String? = null,
        icon: String = "folder",
        colorHex: String? = "#10B981",
        sortOrder: Int = 0
    ) {
        viewModelScope.launch {
            container.repository.saveCollection(id, name, parentId, icon, colorHex, sortOrder)
            showStatusMessage("Kolekce '$name' uložena.")
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            container.repository.softDeleteCollection(collectionId)
            showStatusMessage("Kolekce smazána.")
        }
    }

    // --- SECURITY CENTER, INTEGRITY, SETTINGS, BACKUP ---
    fun runFullSecurityIntegrityCheck() {
        viewModelScope.launch {
            _isBusyOperation.value = true
            try {
                _deviceIntegrity.value = container.deviceIntegrityChecker.evaluateDeviceIntegrity()
                val report = container.repository.runVaultIntegrityCheck { pct, step ->
                    _busyOperationStep.value = "$step ($pct %)"
                }
                _securityReport.value = report
                showStatusMessage("Kontrola integrity dokončena: Skóre ${report.overallScore} %")
            } finally {
                _isBusyOperation.value = false
            }
        }
    }

    fun changeMasterPassword(currentPassword: String, newPassword: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBusyOperation.value = true
            _busyOperationStep.value = "Přešifrování obálky Vault Key novým Master Key…"
            try {
                val ok = container.securityGate.changeMasterPassword(currentPassword, newPassword)
                if (ok) {
                    container.repository.logAuditEvent(
                        category = AuditCategory.SECURITY,
                        actionType = "MASTER_PASSWORD_CHANGED",
                        summary = "Úspěšně změněno Master Password a přebalen Vault Key"
                    )
                    showStatusMessage("Master heslo bylo úspěšně změněno.")
                } else {
                    showStatusMessage("Chyba: Současné Master heslo nesouhlasí.")
                }
                onDone(ok)
            } finally {
                _isBusyOperation.value = false
            }
        }
    }

    fun configureDuressPassword(duressPassword: String, action: DuressAction) {
        viewModelScope.launch {
            container.securityGate.configureDuressPassword(duressPassword, action)
            container.repository.logAuditEvent(
                category = AuditCategory.SECURITY,
                actionType = "DURESS_CONFIGURED",
                summary = "Nastaveno nouzové (Duress) heslo v režimu ${action.name}"
            )
            showStatusMessage("Nouzové (Duress) heslo bylo aktivováno.")
        }
    }

    fun updateVaultSettings(transform: (VaultSettings) -> VaultSettings) {
        viewModelScope.launch {
            val updated = container.settingsRepository.updateSettings(transform)
            QuickCaptureNotificationManager.showIfEnabled(
                context = appContext,
                enabled = updated.persistentNotificationEnabled,
                isUnlocked = container.securityGate.isUnlocked()
            )
            SyncWorker.schedulePeriodicSync(
                context = appContext,
                intervalMinutes = updated.syncIntervalMinutes,
                wifiOnly = updated.syncWifiOnly
            )
            if (container.securityGate.isUnlocked() && !container.securityGate.isDecoyModeActive()) {
                val vaultId = container.securityGate.requireVaultId()
                val integrityHash = container.securityGate.computeIntegrityHash(vaultId.encodeToByteArray())
                container.syncEngine.syncVaultHeader(updated, integrityHash)
            }
        }
    }

    fun exportOvaultContainer(password: String, onExported: (ByteArray) -> Unit = {}) {
        viewModelScope.launch {
            _isBusyOperation.value = true
            _busyOperationStep.value = "Balení a šifrování kontejneru .ovault (OVAULT01 + AES-256-GCM + HMAC)…"
            try {
                val bytes = container.repository.exportVaultToOvaultBytes(password)
                _lastExportedOvaultBytes.value = bytes
                showStatusMessage("Záloha .ovault úspěšně vytvořena (${bytes.size / 1024} KB).")
                onExported(bytes)
            } catch (e: Exception) {
                showStatusMessage("Export selhal: ${e.message}")
            } finally {
                _isBusyOperation.value = false
            }
        }
    }

    fun importOvaultContainer(
        bytes: ByteArray,
        password: String,
        mode: OvaultImportMode,
        initializeIfLocked: Boolean = false
    ) {
        viewModelScope.launch {
            _isBusyOperation.value = true
            _busyOperationStep.value = "Ověřování HMAC-SHA256 a dešifrování kontejneru .ovault…"
            try {
                if (initializeIfLocked && !container.securityGate.isVaultInitialized()) {
                    val header = com.openvault.app.security.OvaultBackupManager.inspectHeader(bytes)
                    container.securityGate.initializeNewVault(
                        masterPassword = password,
                        customVaultId = header.vaultId
                    )
                    container.repository.refreshDatabaseSessionForUnlockState()
                }
                val snapshot = container.repository.importVaultFromOvaultBytes(bytes, password, mode)
                showStatusMessage(
                    "Obnoveno z .ovault: ${snapshot.notes.size} poznámek, ${snapshot.passwords.size} hesel, ${snapshot.files.size} souborů."
                )
                navigateTo(VaultScreen.Dashboard, clearStack = true)
            } catch (e: Exception) {
                showStatusMessage("Import .ovault selhal: ${e.message}")
            } finally {
                _isBusyOperation.value = false
            }
        }
    }

    fun pingSupabaseDiagnostics() {
        viewModelScope.launch {
            _supabaseDiagnosticText.value = "Ověřování spojení se Supabase…"
            val (_, msg) = container.remoteDataSource.pingDiagnostics()
            _supabaseDiagnosticText.value = msg
        }
    }

    fun wipeEntireVaultCryptographically() {
        viewModelScope.launch {
            container.securityGate.wipeVaultCryptographically()
            QuickCaptureNotificationManager.dismiss(appContext)
            showStatusMessage("Trezor a veškeré kryptografické klíče byly trvale smazány.")
        }
    }

    class Factory(
        private val container: AppContainer,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OpenVaultViewModel(container, appContext) as T
        }
    }
}
