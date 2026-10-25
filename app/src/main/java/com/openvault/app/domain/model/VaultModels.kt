package com.openvault.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteRevision(
    val rev: Long,
    val title: String,
    val body: String,
    val timestamp: Long
)

@Serializable
data class Note(
    val id: String,
    val vaultId: String,
    val collectionId: String? = null,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val attachmentFileIds: List<String> = emptyList(),
    val linkedEventIds: List<String> = emptyList(),
    val history: List<NoteRevision> = emptyList(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val colorHex: String? = null,
    val templateName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val rev: Long = 1L
)

data class NoteTemplate(
    val id: String,
    val name: String,
    val icon: String,
    val defaultTitle: String,
    val markdownContent: String,
    val defaultTags: List<String>
)

@Serializable
data class PasswordHistoryEntry(
    val password: String,
    val changedAt: Long
)

@Serializable
data class PasswordItem(
    val id: String,
    val vaultId: String,
    val collectionId: String? = null,
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val totpSecret: String? = null,
    val notes: String = "",
    val category: String = "Ostatní",
    val strengthScore: Int = 50,
    val isFavorite: Boolean = false,
    val expiresAt: Long? = null,
    val lastChangedAt: Long = System.currentTimeMillis(),
    val passwordHistory: List<PasswordHistoryEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val rev: Long = 1L
)

@Serializable
enum class RecurrenceRule(val labelCs: String) {
    NONE("Neopakuje se"),
    DAILY("Denně"),
    WEEKLY("Týdně"),
    MONTHLY("Měsíčně"),
    YEARLY("Ročně")
}

@Serializable
data class CalendarEvent(
    val id: String,
    val vaultId: String,
    val title: String,
    val description: String = "",
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean = false,
    val remindersMinutesBefore: List<Int> = listOf(15),
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val linkedNoteIds: List<String> = emptyList(),
    val linkedRecordIds: List<String> = emptyList(),
    val systemCalendarEventId: Long? = null,
    val colorHex: String? = "#10B981",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val rev: Long = 1L
)

@Serializable
data class EncryptedFileMetadataPayload(
    val fileName: String,
    val mimeType: String,
    val sha256Hex: String,
    val folderId: String? = null,
    val thumbnailBase64: String? = null,
    val linkedObjectIds: List<String> = emptyList(),
    val previewText: String? = null
)

@Serializable
data class VaultFile(
    val id: String,
    val vaultId: String,
    val folderId: String? = null,
    val storagePath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val localEncryptedPath: String? = null,
    val thumbnailBase64: String? = null,
    val previewText: String? = null,
    val linkedObjectIds: List<String> = emptyList(),
    val isUploadedToCloud: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val rev: Long = 1L
)

@Serializable
enum class DatabaseFieldType(val labelCs: String) {
    TEXT("Text"),
    NUMBER("Číslo"),
    BOOLEAN("Přepínač (Ano/Ne)"),
    DATE("Datum"),
    PASSWORD("Heslo (maskované)"),
    URL("Odkaz (URL)"),
    NOTE("Markdown poznámka"),
    FILE("Šifrovaný soubor (VFS)"),
    JSON("Strukturovaný JSON")
}

@Serializable
data class DatabaseFieldSchema(
    val id: String,
    val name: String,
    val type: DatabaseFieldType,
    val required: Boolean = false,
    val defaultValue: String = "",
    val sortOrder: Int = 0
)

@Serializable
data class UserDatabase(
    val id: String,
    val vaultId: String,
    val name: String,
    val description: String = "",
    val icon: String = "database",
    val schema: List<DatabaseFieldSchema> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val rev: Long = 1L
)

@Serializable
data class DatabaseRecord(
    val id: String,
    val vaultId: String,
    val databaseId: String,
    val values: Map<String, String> = emptyMap(),
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val rev: Long = 1L
)

@Serializable
data class CollectionItem(
    val id: String,
    val vaultId: String,
    val parentId: String? = null,
    val name: String,
    val icon: String = "folder",
    val colorHex: String? = "#10B981",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val rev: Long = 1L
)

@Serializable
enum class AuditCategory(val labelCs: String) {
    AUTH("Autentizace"),
    DATA("Data"),
    SYNC("Synchronizace"),
    SECURITY("Bezpečnost")
}

@Serializable
data class AuditEntry(
    val id: Long = 0L,
    val remoteId: Long? = null,
    val vaultId: String,
    val category: AuditCategory,
    val actionType: String,
    val targetObjectId: String? = null,
    val summary: String,
    val deviceInfo: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Serializable
enum class ThemeMode(val labelCs: String) {
    AMOLED_BLACK("AMOLED Černý (#000000)"),
    DARK_GRAY("Tmavý šedý"),
    LIGHT("Světlý"),
    SYSTEM("Podle systému")
}

@Serializable
enum class AccentColorChoice(val labelCs: String, val primaryHex: Long, val secondaryHex: Long) {
    EMERALD("Emerald zelená", 0xFF10B981, 0xFF059669),
    CYAN("Kybernetická Cyan", 0xFF06B6D4, 0xFF0891B2),
    VIOLET("Kryptografická Violet", 0xFF8B5CF6, 0xFF7C3AED),
    AMBER("Bezpečnostní Amber", 0xFFF59E0B, 0xFFD97706),
    CRIMSON("Taktičká Crimson", 0xFFEF4444, 0xFFDC2626)
}

@Serializable
enum class FontScaleChoice(val labelCs: String, val scaleFactor: Float) {
    SMALL("Malé", 0.9f),
    DEFAULT("Výchozí", 1.0f),
    LARGE("Velké", 1.15f)
}

@Serializable
enum class UiDensityChoice(val labelCs: String, val paddingScale: Float) {
    COMPACT("Kompaktní", 0.8f),
    COMFORTABLE("Komfortní", 1.0f)
}

@Serializable
enum class ConflictResolutionStrategy(val labelCs: String) {
    LAST_WRITE_WINS_WITH_HISTORY("Poslední zápis vyhrává + záloha do historie revizí"),
    PREFER_LOCAL("Upřednostnit lokální změnu"),
    PREFER_REMOTE("Upřednostnit vzdálenou změnu ze serveru")
}

@Serializable
enum class FabActionType(val labelCs: String) {
    NEW_NOTE("Nová poznámka"),
    NEW_EVENT("Nová událost"),
    NEW_PASSWORD("Nové heslo"),
    NEW_FILE("Nahrát soubor"),
    NEW_RECORD("Nový záznam v databázi"),
    QUICK_CAPTURE("Quick Capture Sheet")
}

@Serializable
data class VaultSettings(
    val themeMode: ThemeMode = ThemeMode.AMOLED_BLACK,
    val accentColor: AccentColorChoice = AccentColorChoice.EMERALD,
    val fontScale: FontScaleChoice = FontScaleChoice.DEFAULT,
    val uiDensity: UiDensityChoice = UiDensityChoice.COMFORTABLE,
    val autoLockTimeoutMinutes: Int = 5,
    val lockOnBackground: Boolean = false,
    val flagSecureEnabled: Boolean = false,
    val clipboardAutoClear30s: Boolean = true,
    val syncIntervalMinutes: Int = 15, // 0 = manual
    val syncWifiOnly: Boolean = false,
    val realtimeSyncEnabled: Boolean = true,
    val conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.LAST_WRITE_WINS_WITH_HISTORY,
    val systemCalendarSyncEnabled: Boolean = false,
    val targetSystemCalendarId: Long = 1L,
    val anonymizeExportedEvents: Boolean = true,
    val defaultReminderMinutes: Int = 15,
    val fabLongPressAction: FabActionType = FabActionType.NEW_NOTE,
    val persistentNotificationEnabled: Boolean = false,
    val auditVerboseLevel: Int = 2 // 1 = Basic, 2 = Full E2E Audit
)

data class SecurityReport(
    val overallScore: Int, // 0..100
    val totalObjectsChecked: Int,
    val verifiedObjectsCount: Int,
    val corruptedObjectIds: List<String>,
    val weakPasswordsCount: Int,
    val duplicatePasswordsCount: Int,
    val expiredOrOldPasswordsCount: Int,
    val computedIntegrityHashHex: String,
    val lastVerifiedTimestamp: Long
)
