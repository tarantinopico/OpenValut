package com.openvault.app.ui.navigation

/**
 * Complete enumeration of all 38 screens, dialogs, and modal bottom sheets in OpenVault.
 */
sealed class VaultScreen(val titleCs: String, val routeKey: String) {
    // 1-3: Onboarding
    data object OnboardingWelcome : VaultScreen("Vítejte v OpenVault", "onboarding_welcome")
    data object OnboardingMasterPassword : VaultScreen("Vytvoření Master hesla", "onboarding_master_password")
    data object OnboardingBiometrics : VaultScreen("Aktivace biometrie", "onboarding_biometrics")

    // 4-5: Unlock
    data object UnlockBiometric : VaultScreen("Odemknutí biometrií", "unlock_biometric")
    data object UnlockPassword : VaultScreen("Odemknutí heslem", "unlock_password")

    // 6-7: Dashboard & Navigation Drawer (Drawer is persistent wrapper)
    data object Dashboard : VaultScreen("Přehled", "dashboard")

    // 8-10: Notes
    data object NotesList : VaultScreen("Poznámky", "notes_list")
    data class NoteEditor(val noteId: String? = null, val initialCollectionId: String? = null) :
        VaultScreen("Editor poznámky", "note_editor")
    data class NoteDetail(val noteId: String) : VaultScreen("Detail poznámky", "note_detail")

    // 11-14: Calendar & Events
    data object CalendarMonth : VaultScreen("Kalendář – Měsíc", "calendar_month")
    data object CalendarAgenda : VaultScreen("Kalendář – Agenda", "calendar_agenda")
    data class EventEditor(val eventId: String? = null, val initialDateMs: Long? = null) :
        VaultScreen("Editor události", "event_editor")
    data class EventDetail(val eventId: String) : VaultScreen("Detail události", "event_detail")

    // 15-16: Passwords & TOTP
    data object PasswordsList : VaultScreen("Hesla a TOTP", "passwords_list")
    data class PasswordDetailEditor(val passwordId: String? = null) :
        VaultScreen("Heslo a TOTP autentikátor", "password_detail_editor")

    // 17-18: VFS Files
    data class FilesBrowser(val folderId: String? = null) : VaultScreen("Šifrované soubory (VFS)", "files_browser")
    data class FileDetail(val fileId: String) : VaultScreen("Detail souboru", "file_detail")

    // 19-23: User Databases & Records
    data object DatabasesList : VaultScreen("Uživatelské databáze", "databases_list")
    data class DatabaseSchemaBuilder(val databaseId: String? = null) :
        VaultScreen("Schema Builder databáze", "database_schema_builder")
    data class DatabaseRecordsList(val databaseId: String) :
        VaultScreen("Záznamy databáze", "database_records_list")
    data class RecordEditor(val databaseId: String, val recordId: String? = null) :
        VaultScreen("Editor záznamu", "record_editor")
    data class RecordDetail(val databaseId: String, val recordId: String) :
        VaultScreen("Detail záznamu", "record_detail")

    // Collections management screen
    data object CollectionsManager : VaultScreen("Kolekce a složky", "collections_manager")

    // 24-25: Audit Log & Security Center
    data object AuditLog : VaultScreen("Šifrovaný Audit Log", "audit_log")
    data object SecurityCenter : VaultScreen("Bezpečnostní centrum", "security_center")

    // 26-34: Settings hub & 8 sub-screens
    data object SettingsHub : VaultScreen("Nastavení", "settings_hub")
    data object SettingsSecurity : VaultScreen("Nastavení – Bezpečnost", "settings_security")
    data object SettingsSync : VaultScreen("Nastavení – Synchronizace", "settings_sync")
    data object SettingsAppearance : VaultScreen("Nastavení – Vzhled", "settings_appearance")
    data object SettingsBackup : VaultScreen("Nastavení – Data a zálohy (.ovault)", "settings_backup")
    data object SettingsCalendar : VaultScreen("Nastavení – Kalendář", "settings_calendar")
    data object SettingsQuickActions : VaultScreen("Nastavení – Rychlé akce a widgety", "settings_quick_actions")
    data object SettingsAudit : VaultScreen("Nastavení – Audit a logy", "settings_audit")
    data object SettingsAbout : VaultScreen("Nastavení – O aplikaci", "settings_about")
}

/**
 * Modal bottom sheets (Screens 35–38).
 */
sealed class VaultModalSheet {
    data class QuickCapture(val initialTab: Int = 0) : VaultModalSheet() // #35
    data object SyncAndIntegrityProgress : VaultModalSheet() // #36
    data object LockConfirmation : VaultModalSheet() // #37
    data class ContextMenu(
        val itemId: String,
        val itemTitle: String,
        val itemType: String // "note", "password", "event", "file", "record"
    ) : VaultModalSheet() // #38
}
