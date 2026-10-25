package com.openvault.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["vaultId", "updatedAt"]),
        Index(value = ["collectionId"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val collectionId: String?,
    val title: String,
    val body: String,
    val tagsJson: String,
    val attachmentsJson: String,
    val eventLinksJson: String,
    val historyJson: String,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val color: String?,
    val templateName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val rev: Long
)

@Entity(
    tableName = "passwords",
    indices = [
        Index(value = ["vaultId", "category"]),
        Index(value = ["collectionId"])
    ]
)
data class PasswordEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val collectionId: String?,
    val title: String,
    val username: String,
    val encryptedPassword: String,
    val url: String,
    val totpSecret: String?,
    val notes: String,
    val category: String,
    val strengthScore: Int,
    val isFavorite: Boolean,
    val expiresAt: Long?,
    val lastChangedAt: Long,
    val historyJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val rev: Long
)

@Entity(
    tableName = "calendar_events",
    indices = [Index(value = ["vaultId", "startAt"])]
)
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val title: String,
    val description: String,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean,
    val remindersJson: String,
    val recurrenceRule: String,
    val linkedNotesJson: String,
    val linkedRecordsJson: String,
    val systemCalendarEventId: Long?,
    val color: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val rev: Long
)

@Entity(
    tableName = "vault_files",
    indices = [Index(value = ["vaultId", "folderId"])]
)
data class VaultFileEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val folderId: String?,
    val storagePath: String,
    val encryptedMetadata: ByteArray,
    val metadataNonce: ByteArray,
    val decryptedFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localEncryptedPath: String?,
    val sha256Hex: String,
    val thumbnailBase64: String? = null,
    val previewText: String? = null,
    val linkedObjectsJson: String = "[]",
    val isUploadedToCloud: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val rev: Long
)

@Entity(
    tableName = "user_databases",
    indices = [Index(value = ["vaultId"])]
)
data class UserDatabaseEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val name: String,
    val description: String,
    val icon: String,
    val schemaJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val rev: Long
)

@Entity(
    tableName = "database_records",
    indices = [Index(value = ["vaultId", "databaseId"])]
)
data class DatabaseRecordEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val databaseId: String,
    val valuesJson: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val rev: Long
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["vaultId", "parentId"])]
)
data class CollectionEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val parentId: String?,
    val name: String,
    val icon: String,
    val color: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val rev: Long
)

@Entity(
    tableName = "audit_entries",
    indices = [Index(value = ["vaultId", "createdAt"])]
)
data class AuditEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: Long? = null,
    val vaultId: String,
    val category: String,
    val actionType: String,
    val targetObjectId: String?,
    val summary: String,
    val deviceInfo: String,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val createdAt: Long,
    val isSynced: Boolean
)

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["tableName", "objectId"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tableName: String,
    val objectId: String,
    val action: String, // "upsert" or "delete"
    val payloadJson: String,
    val rev: Long,
    val retries: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
