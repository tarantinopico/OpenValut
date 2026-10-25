package com.openvault.app.data.local

import com.openvault.app.domain.model.AuditCategory
import com.openvault.app.domain.model.AuditEntry
import com.openvault.app.domain.model.CalendarEvent
import com.openvault.app.domain.model.CollectionItem
import com.openvault.app.domain.model.DatabaseFieldSchema
import com.openvault.app.domain.model.DatabaseRecord
import com.openvault.app.domain.model.EncryptedFileMetadataPayload
import com.openvault.app.domain.model.Note
import com.openvault.app.domain.model.NoteRevision
import com.openvault.app.domain.model.PasswordHistoryEntry
import com.openvault.app.domain.model.PasswordItem
import com.openvault.app.domain.model.RecurrenceRule
import com.openvault.app.domain.model.UserDatabase
import com.openvault.app.domain.model.VaultFile
import com.openvault.app.security.CryptoEngine
import com.openvault.app.security.SecurityGate
import com.openvault.app.security.hexToByteArray
import com.openvault.app.security.toHexString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object EntityMappers {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // --- NOTE ---
    fun Note.toEntity(): NoteEntity = NoteEntity(
        id = id,
        vaultId = vaultId,
        collectionId = collectionId,
        title = title,
        body = body,
        tagsJson = json.encodeToString(tags),
        attachmentsJson = json.encodeToString(attachmentFileIds),
        eventLinksJson = json.encodeToString(linkedEventIds),
        historyJson = json.encodeToString(history),
        isPinned = isPinned,
        isFavorite = isFavorite,
        color = colorHex,
        templateName = templateName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    fun NoteEntity.toDomain(): Note = Note(
        id = id,
        vaultId = vaultId,
        collectionId = collectionId,
        title = title,
        body = body,
        tags = runCatching { json.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList()),
        attachmentFileIds = runCatching { json.decodeFromString<List<String>>(attachmentsJson) }.getOrDefault(emptyList()),
        linkedEventIds = runCatching { json.decodeFromString<List<String>>(eventLinksJson) }.getOrDefault(emptyList()),
        history = runCatching { json.decodeFromString<List<NoteRevision>>(historyJson) }.getOrDefault(emptyList()),
        isPinned = isPinned,
        isFavorite = isFavorite,
        colorHex = color,
        templateName = templateName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    // --- PASSWORD ---
    fun PasswordItem.toEntity(securityGate: SecurityGate): PasswordEntity {
        val encryptedBlob = securityGate.encryptString(password, aad = id)
        val packedHex = CryptoEngine.packNonceAndCiphertext(encryptedBlob).toHexString()
        return PasswordEntity(
            id = id,
            vaultId = vaultId,
            collectionId = collectionId,
            title = title,
            username = username,
            encryptedPassword = packedHex,
            url = url,
            totpSecret = totpSecret,
            notes = notes,
            category = category,
            strengthScore = strengthScore,
            isFavorite = isFavorite,
            expiresAt = expiresAt,
            lastChangedAt = lastChangedAt,
            historyJson = json.encodeToString(passwordHistory),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            rev = rev
        )
    }

    fun PasswordEntity.toDomain(securityGate: SecurityGate): PasswordItem {
        val plainPassword = try {
            val unpacked = CryptoEngine.unpackNonceAndCiphertext(encryptedPassword.hexToByteArray())
            securityGate.decryptString(unpacked.ciphertext, unpacked.nonce, aad = id)
        } catch (_: Exception) {
            encryptedPassword
        }
        return PasswordItem(
            id = id,
            vaultId = vaultId,
            collectionId = collectionId,
            title = title,
            username = username,
            password = plainPassword,
            url = url,
            totpSecret = totpSecret,
            notes = notes,
            category = category,
            strengthScore = strengthScore,
            isFavorite = isFavorite,
            expiresAt = expiresAt,
            lastChangedAt = lastChangedAt,
            passwordHistory = runCatching {
                json.decodeFromString<List<PasswordHistoryEntry>>(historyJson)
            }.getOrDefault(emptyList()),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            rev = rev
        )
    }

    // --- CALENDAR EVENT ---
    fun CalendarEvent.toEntity(): CalendarEventEntity = CalendarEventEntity(
        id = id,
        vaultId = vaultId,
        title = title,
        description = description,
        startAt = startAt,
        endAt = endAt,
        allDay = allDay,
        remindersJson = json.encodeToString(remindersMinutesBefore),
        recurrenceRule = recurrenceRule.name,
        linkedNotesJson = json.encodeToString(linkedNoteIds),
        linkedRecordsJson = json.encodeToString(linkedRecordIds),
        systemCalendarEventId = systemCalendarEventId,
        color = colorHex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
        id = id,
        vaultId = vaultId,
        title = title,
        description = description,
        startAt = startAt,
        endAt = endAt,
        allDay = allDay,
        remindersMinutesBefore = runCatching {
            json.decodeFromString<List<Int>>(remindersJson)
        }.getOrDefault(listOf(15)),
        recurrenceRule = runCatching { RecurrenceRule.valueOf(recurrenceRule) }
            .getOrDefault(RecurrenceRule.NONE),
        linkedNoteIds = runCatching {
            json.decodeFromString<List<String>>(linkedNotesJson)
        }.getOrDefault(emptyList()),
        linkedRecordIds = runCatching {
            json.decodeFromString<List<String>>(linkedRecordsJson)
        }.getOrDefault(emptyList()),
        systemCalendarEventId = systemCalendarEventId,
        colorHex = color,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    // --- VAULT FILE ---
    fun VaultFile.toEntity(securityGate: SecurityGate): VaultFileEntity {
        val metadataPayload = EncryptedFileMetadataPayload(
            fileName = fileName,
            mimeType = mimeType,
            sha256Hex = sha256Hex,
            folderId = folderId,
            thumbnailBase64 = thumbnailBase64,
            linkedObjectIds = linkedObjectIds,
            previewText = previewText
        )
        val metadataBytes = json.encodeToString(metadataPayload).encodeToByteArray()
        val encryptedMeta = securityGate.encryptPayload(metadataBytes)
        return VaultFileEntity(
            id = id,
            vaultId = vaultId,
            folderId = folderId,
            storagePath = storagePath,
            encryptedMetadata = encryptedMeta.ciphertext,
            metadataNonce = encryptedMeta.nonce,
            decryptedFileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            localEncryptedPath = localEncryptedPath,
            sha256Hex = sha256Hex,
            thumbnailBase64 = thumbnailBase64,
            previewText = previewText,
            linkedObjectsJson = json.encodeToString(linkedObjectIds),
            isUploadedToCloud = isUploadedToCloud,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            rev = rev
        )
    }

    fun VaultFileEntity.toDomain(): VaultFile = VaultFile(
        id = id,
        vaultId = vaultId,
        folderId = folderId,
        storagePath = storagePath,
        fileName = decryptedFileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256Hex = sha256Hex,
        localEncryptedPath = localEncryptedPath,
        thumbnailBase64 = thumbnailBase64,
        previewText = previewText,
        linkedObjectIds = runCatching {
            json.decodeFromString<List<String>>(linkedObjectsJson)
        }.getOrDefault(emptyList()),
        isUploadedToCloud = isUploadedToCloud,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    // --- USER DATABASE ---
    fun UserDatabase.toEntity(): UserDatabaseEntity = UserDatabaseEntity(
        id = id,
        vaultId = vaultId,
        name = name,
        description = description,
        icon = icon,
        schemaJson = json.encodeToString(schema),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    fun UserDatabaseEntity.toDomain(): UserDatabase = UserDatabase(
        id = id,
        vaultId = vaultId,
        name = name,
        description = description,
        icon = icon,
        schema = runCatching {
            json.decodeFromString<List<DatabaseFieldSchema>>(schemaJson)
        }.getOrDefault(emptyList()),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    // --- DATABASE RECORD ---
    fun DatabaseRecord.toEntity(): DatabaseRecordEntity = DatabaseRecordEntity(
        id = id,
        vaultId = vaultId,
        databaseId = databaseId,
        valuesJson = json.encodeToString(values),
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    fun DatabaseRecordEntity.toDomain(): DatabaseRecord = DatabaseRecord(
        id = id,
        vaultId = vaultId,
        databaseId = databaseId,
        values = runCatching {
            json.decodeFromString<Map<String, String>>(valuesJson)
        }.getOrDefault(emptyMap()),
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    // --- COLLECTION ---
    fun CollectionItem.toEntity(): CollectionEntity = CollectionEntity(
        id = id,
        vaultId = vaultId,
        parentId = parentId,
        name = name,
        icon = icon,
        color = colorHex,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    fun CollectionEntity.toDomain(): CollectionItem = CollectionItem(
        id = id,
        vaultId = vaultId,
        parentId = parentId,
        name = name,
        icon = icon,
        colorHex = color,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        rev = rev
    )

    // --- AUDIT ENTRY ---
    fun AuditEntry.toEntity(securityGate: SecurityGate): AuditEntryEntity {
        val encrypted = if (securityGate.isUnlocked()) {
            securityGate.encryptString( json.encodeToString(this) )
        } else {
            val fallbackKey = CryptoEngine.deriveSubKey(
                vaultId.encodeToByteArray(),
                "OpenVault-PreAuth-Audit-v1"
            )
            CryptoEngine.encryptAesGcm(json.encodeToString(this).encodeToByteArray(), fallbackKey)
                .also { CryptoEngine.zeroize(fallbackKey) }
        }
        return AuditEntryEntity(
            id = id,
            remoteId = remoteId,
            vaultId = vaultId,
            category = category.name,
            actionType = actionType,
            targetObjectId = targetObjectId,
            summary = summary,
            deviceInfo = deviceInfo,
            encryptedPayload = encrypted.ciphertext,
            nonce = encrypted.nonce,
            createdAt = createdAt,
            isSynced = isSynced
        )
    }

    fun AuditEntryEntity.toDomain(): AuditEntry = AuditEntry(
        id = id,
        remoteId = remoteId,
        vaultId = vaultId,
        category = runCatching { AuditCategory.valueOf(category) }.getOrDefault(AuditCategory.SECURITY),
        actionType = actionType,
        targetObjectId = targetObjectId,
        summary = summary,
        deviceInfo = deviceInfo,
        createdAt = createdAt,
        isSynced = isSynced
    )
}
