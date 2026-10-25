package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openvault.app.OpenVaultApplication
import com.openvault.app.notification.QuickCaptureNotificationManager
import com.openvault.app.security.VaultLockState
import com.openvault.app.sync.SyncStatus
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.*
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.screens.*
import com.openvault.app.ui.theme.OpenVaultTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var backgroundTimestampMs: Long = 0L
    private var latestSettings = com.openvault.app.domain.model.VaultSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val appContainer = (application as OpenVaultApplication).container

        if (intent?.action == QuickCaptureNotificationManager.ACTION_LOCK_NOW) {
            appContainer.securityGate.lock()
        }

        setContent {
            val viewModel: OpenVaultViewModel = viewModel(
                factory = OpenVaultViewModel.Factory(appContainer, applicationContext)
            )
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings) {
                latestSettings = settings
            }

            LaunchedEffect(settings.flagSecureEnabled) {
                if (settings.flagSecureEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(intent) {
                viewModel.handleIncomingActionIntent(intent?.action)
            }

            OpenVaultTheme(settings = settings) {
                OpenVaultAppShell(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestampMs = System.currentTimeMillis()
        val appContainer = (application as OpenVaultApplication).container
        if (latestSettings.lockOnBackground) {
            appContainer.securityGate.lock()
        }
    }

    override fun onResume() {
        super.onResume()
        if (backgroundTimestampMs > 0L) {
            val appContainer = (application as OpenVaultApplication).container
            val elapsedMs = System.currentTimeMillis() - backgroundTimestampMs
            if (latestSettings.lockOnBackground || elapsedMs >= latestSettings.autoLockTimeoutMinutes * 60_000L) {
                appContainer.securityGate.lock()
            }
            backgroundTimestampMs = 0L
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenVaultAppShell(viewModel: OpenVaultViewModel) {
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val activeModalSheet by viewModel.activeModalSheet.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val databases by viewModel.databases.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var fabExpanded by remember { mutableStateOf(false) }
    var pendingSyncQueueCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(statusBannerMessage) {
        statusBannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.showStatusMessage(null)
        }
    }

    LaunchedEffect(activeModalSheet, syncStatus) {
        if (activeModalSheet != null) {
            pendingSyncQueueCount = viewModel.getPendingSyncQueueItems().size
        }
    }

    val isUnlocked = lockState is VaultLockState.Unlocked

    BackHandler(enabled = isUnlocked && (drawerState.isOpen || activeModalSheet != null || currentScreen != VaultScreen.Dashboard)) {
        when {
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            activeModalSheet != null -> viewModel.openModalSheet(null)
            else -> viewModel.navigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && isUnlocked) {
                    val ctrl = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                    when {
                        ctrl && keyEvent.key == Key.N -> {
                            viewModel.navigateTo(VaultScreen.NoteEditor())
                            true
                        }
                        ctrl && keyEvent.key == Key.E -> {
                            viewModel.navigateTo(VaultScreen.EventEditor())
                            true
                        }
                        ctrl && (keyEvent.key == Key.K || keyEvent.key == Key.F) -> {
                            viewModel.openModalSheet(VaultModalSheet.QuickCapture(0))
                            true
                        }
                        ctrl && keyEvent.key == Key.S -> {
                            viewModel.triggerManualSync()
                            true
                        }
                        ctrl && keyEvent.key == Key.L -> {
                            viewModel.lockVaultImmediately("Klávesová zkratka Ctrl+L")
                            true
                        }
                        keyEvent.key == Key.Escape -> {
                            if (activeModalSheet != null) {
                                viewModel.openModalSheet(null)
                                true
                            } else if (currentScreen != VaultScreen.Dashboard) {
                                viewModel.navigateBack()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        if (!isUnlocked) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentScreen) {
                        VaultScreen.OnboardingWelcome -> OnboardingWelcomeScreen(viewModel)
                        VaultScreen.OnboardingMasterPassword -> OnboardingMasterPasswordScreen(viewModel)
                        VaultScreen.OnboardingBiometrics -> OnboardingBiometricsScreen(viewModel)
                        VaultScreen.UnlockBiometric -> UnlockBiometricScreen(viewModel)
                        else -> UnlockPasswordScreen(
                            viewModel = viewModel,
                            lockState = lockState as? VaultLockState.Locked
                        )
                    }
                }
            }
        } else {
            val unlockedState = lockState as? VaultLockState.Unlocked
            val isPrimaryDrawerScreen = currentScreen in setOf(
                VaultScreen.Dashboard,
                VaultScreen.NotesList,
                VaultScreen.CalendarMonth,
                VaultScreen.CalendarAgenda,
                VaultScreen.PasswordsList,
                VaultScreen.DatabasesList,
                VaultScreen.CollectionsManager,
                VaultScreen.AuditLog,
                VaultScreen.SecurityCenter,
                VaultScreen.SettingsHub
            ) || currentScreen is VaultScreen.FilesBrowser

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = isPrimaryDrawerScreen,
                drawerContent = {
                    OpenVaultDrawerContent(
                        currentScreen = currentScreen,
                        vaultId = unlockedState?.vaultId ?: "",
                        isDecoyMode = unlockedState?.isDecoyMode == true,
                        onNavigate = { target ->
                            coroutineScope.launch { drawerState.close() }
                            viewModel.navigateTo(target)
                        },
                        onRequestLock = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.openModalSheet(VaultModalSheet.LockConfirmation)
                        }
                    )
                }
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (currentScreen !is VaultScreen.OnboardingBiometrics) {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = currentScreen.titleCs,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (unlockedState?.isDecoyMode == true) {
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("Krycí", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    if (isPrimaryDrawerScreen) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                                }
                                            },
                                            modifier = Modifier.testTag("drawer_menu_button")
                                        ) {
                                            Icon(Icons.Default.Menu, contentDescription = "Otevřít navigační menu")
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { viewModel.navigateBack() },
                                            modifier = Modifier.testTag("back_navigation_button")
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Zpět"
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    SyncStatusBadge(
                                        syncStatus = syncStatus,
                                        onClick = {
                                            viewModel.openModalSheet(VaultModalSheet.SyncAndIntegrityProgress)
                                        }
                                    )
                                    IconButton(
                                        onClick = { viewModel.openModalSheet(VaultModalSheet.QuickCapture(0)) },
                                        modifier = Modifier.testTag("top_quick_capture_button")
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Rychlý zápis")
                                    }
                                    IconButton(
                                        onClick = { viewModel.lockVaultImmediately("Tlačítko okamžitého uzamčení") },
                                        modifier = Modifier.testTag("top_instant_lock_button")
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = "Okamžitě uzamknout trezor")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    },
                    floatingActionButton = {
                        if (currentScreen in setOf(
                                VaultScreen.Dashboard,
                                VaultScreen.NotesList,
                                VaultScreen.CalendarMonth,
                                VaultScreen.CalendarAgenda,
                                VaultScreen.PasswordsList,
                                VaultScreen.DatabasesList
                            ) || currentScreen is VaultScreen.FilesBrowser
                        ) {
                            ExpandableVaultFab(
                                expanded = fabExpanded,
                                onExpandedChange = { fabExpanded = it },
                                onNewNote = { viewModel.navigateTo(VaultScreen.NoteEditor()) },
                                onNewEvent = { viewModel.navigateTo(VaultScreen.EventEditor()) },
                                onNewPassword = { viewModel.navigateTo(VaultScreen.PasswordDetailEditor()) },
                                onNewFile = { viewModel.navigateTo(VaultScreen.FilesBrowser()) },
                                onNewRecord = {
                                    val firstDb = databases.firstOrNull()
                                    if (firstDb != null) {
                                        viewModel.navigateTo(VaultScreen.RecordEditor(firstDb.id))
                                    } else {
                                        viewModel.navigateTo(VaultScreen.DatabaseSchemaBuilder())
                                    }
                                },
                                onLongPressQuickNote = {
                                    viewModel.openModalSheet(VaultModalSheet.QuickCapture(0))
                                },
                                onSwipeUpQuickCapture = {
                                    viewModel.openModalSheet(VaultModalSheet.QuickCapture(0))
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (val screen = currentScreen) {
                            VaultScreen.OnboardingWelcome -> OnboardingWelcomeScreen(viewModel)
                            VaultScreen.OnboardingMasterPassword -> OnboardingMasterPasswordScreen(viewModel)
                            VaultScreen.OnboardingBiometrics -> OnboardingBiometricsScreen(viewModel)
                            VaultScreen.UnlockBiometric -> UnlockBiometricScreen(viewModel)
                            VaultScreen.UnlockPassword -> UnlockPasswordScreen(
                                viewModel = viewModel,
                                lockState = null
                            )
                            VaultScreen.Dashboard -> DashboardScreen(viewModel)
                            VaultScreen.NotesList -> NotesListScreen(viewModel)
                            is VaultScreen.NoteEditor -> NoteEditorScreen(
                                viewModel = viewModel,
                                noteId = screen.noteId,
                                initialCollectionId = screen.initialCollectionId
                            )
                            is VaultScreen.NoteDetail -> NoteDetailScreen(
                                viewModel = viewModel,
                                noteId = screen.noteId
                            )
                            VaultScreen.CalendarMonth -> CalendarMonthScreen(viewModel)
                            VaultScreen.CalendarAgenda -> CalendarAgendaScreen(viewModel)
                            is VaultScreen.EventEditor -> EventEditorScreen(
                                viewModel = viewModel,
                                eventId = screen.eventId,
                                initialDateMs = screen.initialDateMs
                            )
                            is VaultScreen.EventDetail -> EventDetailScreen(
                                viewModel = viewModel,
                                eventId = screen.eventId
                            )
                            VaultScreen.PasswordsList -> PasswordsListScreen(viewModel)
                            is VaultScreen.PasswordDetailEditor -> PasswordDetailEditorScreen(
                                viewModel = viewModel,
                                passwordId = screen.passwordId
                            )
                            is VaultScreen.FilesBrowser -> FilesBrowserScreen(
                                viewModel = viewModel,
                                initialFolderId = screen.folderId
                            )
                            is VaultScreen.FileDetail -> FileDetailScreen(
                                viewModel = viewModel,
                                fileId = screen.fileId
                            )
                            VaultScreen.DatabasesList -> DatabasesListScreen(viewModel)
                            is VaultScreen.DatabaseSchemaBuilder -> DatabaseSchemaBuilderScreen(
                                viewModel = viewModel,
                                databaseId = screen.databaseId
                            )
                            is VaultScreen.DatabaseRecordsList -> DatabaseRecordsListScreen(
                                viewModel = viewModel,
                                databaseId = screen.databaseId
                            )
                            is VaultScreen.RecordEditor -> RecordEditorScreen(
                                viewModel = viewModel,
                                databaseId = screen.databaseId,
                                recordId = screen.recordId
                            )
                            is VaultScreen.RecordDetail -> RecordDetailScreen(
                                viewModel = viewModel,
                                databaseId = screen.databaseId,
                                recordId = screen.recordId
                            )
                            VaultScreen.CollectionsManager -> CollectionsManagerScreen(viewModel)
                            VaultScreen.AuditLog -> AuditLogScreen(viewModel)
                            VaultScreen.SecurityCenter -> SecurityCenterScreen(viewModel)
                            VaultScreen.SettingsHub -> SettingsHubScreen(viewModel)
                            VaultScreen.SettingsSecurity -> SettingsSecurityScreen(viewModel)
                            VaultScreen.SettingsSync -> SettingsSyncScreen(viewModel)
                            VaultScreen.SettingsAppearance -> SettingsAppearanceScreen(viewModel)
                            VaultScreen.SettingsBackup -> SettingsBackupScreen(viewModel)
                            VaultScreen.SettingsCalendar -> SettingsCalendarScreen(viewModel)
                            VaultScreen.SettingsQuickActions -> SettingsQuickActionsScreen(viewModel)
                            VaultScreen.SettingsAudit -> SettingsAuditScreen(viewModel)
                            VaultScreen.SettingsAbout -> SettingsAboutScreen(viewModel)
                        }
                    }
                }
            }

            // Modal Bottom Sheets (Screens #35 – #38)
            when (val sheet = activeModalSheet) {
                is VaultModalSheet.QuickCapture -> QuickCaptureBottomSheet(
                    initialTab = sheet.initialTab,
                    onDismiss = { viewModel.openModalSheet(null) },
                    onQuickSaveNote = { title, body ->
                        viewModel.saveNote(title = title.ifBlank { "Rychlá poznámka" }, body = body)
                    },
                    onQuickSavePassword = { title, username, password ->
                        viewModel.savePassword(title = title, username = username, password = password)
                    },
                    onQuickSaveEvent = { title, startAt ->
                        viewModel.saveEvent(
                            title = title,
                            startAt = startAt,
                            endAt = startAt + 3_600_000L
                        )
                    }
                )
                VaultModalSheet.SyncAndIntegrityProgress -> SyncProgressModalSheet(
                    syncStatus = syncStatus,
                    pendingQueueCount = pendingSyncQueueCount,
                    resolvedConflicts = (syncStatus as? SyncStatus.ConflictOrWarning)?.let { listOf(it.message) }
                        ?: emptyList(),
                    onTriggerSyncNow = { viewModel.triggerManualSync() },
                    onDismiss = { viewModel.openModalSheet(null) }
                )
                VaultModalSheet.LockConfirmation -> LockConfirmationBottomSheet(
                    pendingSyncCount = pendingSyncQueueCount,
                    onConfirmLock = {
                        viewModel.openModalSheet(null)
                        viewModel.lockVaultImmediately("Potvrzené uzamčení z menu")
                    },
                    onDismiss = { viewModel.openModalSheet(null) }
                )
                is VaultModalSheet.ContextMenu -> ContextMenuBottomSheet(
                    itemTitle = sheet.itemTitle,
                    itemType = sheet.itemType,
                    onEdit = {
                        when (sheet.itemType) {
                            "note" -> viewModel.navigateTo(VaultScreen.NoteEditor(noteId = sheet.itemId))
                            "password" -> viewModel.navigateTo(VaultScreen.PasswordDetailEditor(passwordId = sheet.itemId))
                            "event" -> viewModel.navigateTo(VaultScreen.EventEditor(eventId = sheet.itemId))
                            "file" -> viewModel.navigateTo(VaultScreen.FileDetail(fileId = sheet.itemId))
                        }
                    },
                    onDuplicate = if (sheet.itemType == "note") {
                        { viewModel.duplicateNote(sheet.itemId) }
                    } else null,
                    onDelete = {
                        when (sheet.itemType) {
                            "note" -> viewModel.deleteNote(sheet.itemId)
                            "password" -> viewModel.deletePassword(sheet.itemId)
                            "event" -> viewModel.deleteEvent(sheet.itemId)
                            "file" -> viewModel.deleteFile(sheet.itemId)
                            "record" -> viewModel.deleteDatabaseRecord(sheet.itemId)
                        }
                    },
                    onDismiss = { viewModel.openModalSheet(null) }
                )
                null -> Unit
            }
        }
    }
}
