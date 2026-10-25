package com.openvault.app.sync

import com.openvault.app.data.local.EntityMappers
import com.openvault.app.data.local.EntityMappers.toDomain
import com.openvault.app.data.local.EntityMappers.toEntity
import com.openvault.app.data.local.OpenVaultDao
import com.openvault.app.data.local.SyncQueueEntity
import com.openvault.app.data.remote.RemoteVaultApi
import com.openvault.app.data.remote.RemoteVaultAuditDto
import com.openvault.app.data.remote.RemoteVaultDto
import com.openvault.app.data.remote.RemoteVaultFileDto
import com.openvault.app.data.remote.RemoteVaultObjectDto
import com.openvault.app.data.remote.SupabaseRemoteDataSource
import com.openvault.app.domain.model.AuditCategory
import com.openvault.app.domain.model.AuditEntry
import com.openvault.app.domain.model.CalendarEvent
import com.openvault.app.domain.model.CollectionItem
import com.openvault.app.domain.model.ConflictResolutionStrategy
import com.openvault.app.domain.model.DatabaseRecord
import com.openvault.app.domain.model.EncryptedFileMetadataPayload
import com.openvault.app.domain.model.Note
import com.openvault.app.domain.model.PasswordItem
import com.openvault.app.domain.model.UserDatabase
import com.openvault.app.domain.model.VaultFile
import com.openvault.app.domain.model.VaultSettings
import com.openvault.app.security.SecurityGate
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString

sealed class SyncStatus(val badgeSymbol: String, val labelCs: String) {
    data class Synced(val lastSyncedAtMs: Long = System.currentTimeMillis()) :
        SyncStatus("☁✓", "Synchronizováno")

    data class Syncing(
        val progressPercent: Int = 0,
        val stepDescription: String = "Synchronizace se Supabase…",
        val pushedCount: Int = 0,
        val pulledCount: Int = 0
    ) : SyncStatus("☁↻", "Synchronizuje se…")

    data class ConflictOrWarning(
        val message: String,
        val pendingCount: Int = 0,
        val resolvedConflictsCount: Int = 0
    ) : SyncStatus("☁⚠", "Varování / Kolize")

    data class Offline(val queuedCount: Int = 0) :
        SyncStatus("☁✕", "Offline (${queuedCount} ve frontě)")
}

class SyncEngine(
    private val daoProvider: () -> OpenVaultDao,
    private val remoteApi: RemoteVaultApi,
    private val securityGate: SecurityGate,
    private val isNetworkAvailableProvider: () -> Boolean = { true }
) {
    private val json = EntityMappers.json

    private val _syncState = MutableStateFlow<SyncStatus>(SyncStatus.Synced())
    val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    @Volatile
    var lastSyncTimestampMs: Long = 0L
        private set

    @Volatile
    var resolvedConflictsHistory: List<String> = emptyList()
        private set

    fun setNetworkOnlineState(isOnline: Boolean, queuedCount: Int = 0) {
        if (!isOnline) {
            _syncState.value = SyncStatus.Offline(queuedCount)
        }
    }

    /**
     * Enqueues an object mutation in `SyncQueueEntity` and immediately attempts online push if connected.
     */
    suspend fun enqueueAndSyncObject(
        objectType: String,
        objectId: String,
        parentId: String?,
        plainJsonPayload: String,
        rev: Long,
        deletedAtMs: Long?
    ): Boolean {
        if (!securityGate.isUnlocked() || securityGate.isDecoyModeActive()) return false
        val dao = daoProvider()
        val queueItem = SyncQueueEntity(
            tableName = "vault_objects",
            objectId = objectId,
            action = if (deletedAtMs != null) "delete" else "upsert",
            payloadJson = json.encodeToString(
                QueuedVaultObjectEnvelope(
                    id = objectId,
                    objectType = objectType,
                    parentId = parentId,
                    plainPayloadJson = plainJsonPayload,
                    rev = rev,
                    deletedAtMs = deletedAtMs
                )
            ),
            rev = rev
        )
        val queueId = dao.enqueueSyncItem(queueItem)

        if (!isNetworkAvailableProvider()) {
            val pending = dao.getPendingSyncQueue().size
            _syncState.value = SyncStatus.Offline(pending)
            return false
        }

        return pushQueuedItem(queueItem.copy(id = queueId))
    }

    suspend fun enqueueAndSyncFile(file: VaultFile, encryptedBlobBytes: ByteArray?): Boolean {
        if (!securityGate.isUnlocked() || securityGate.isDecoyModeActive()) return false
        val dao = daoProvider()
        val queueItem = SyncQueueEntity(
            tableName = "vault_files",
            objectId = file.id,
            action = if (file.deletedAt != null) "delete" else "upsert",
            payloadJson = json.encodeToString(file),
            rev = file.rev
        )
        val queueId = dao.enqueueSyncItem(queueItem)

        if (!isNetworkAvailableProvider()) {
            val pending = dao.getPendingSyncQueue().size
            _syncState.value = SyncStatus.Offline(pending)
            return false
        }

        val vaultId = securityGate.requireVaultId()
        if (encryptedBlobBytes != null && file.deletedAt == null) {
            remoteApi.uploadEncryptedFileBlob(vaultId, file.id, encryptedBlobBytes)
        }
        return pushQueuedItem(queueItem.copy(id = queueId))
    }

    private suspend fun pushQueuedItem(item: SyncQueueEntity): Boolean {
        if (!securityGate.isUnlocked() || securityGate.isDecoyModeActive()) return false
        val dao = daoProvider()
        val vaultId = securityGate.requireVaultId()

        return try {
            val success = when (item.tableName) {
                "vault_objects" -> {
                    val envelope = json.decodeFromString<QueuedVaultObjectEnvelope>(item.payloadJson)
                    val encrypted = securityGate.encryptString(envelope.plainPayloadJson)
                    val dto = RemoteVaultObjectDto(
                        id = envelope.id,
                        vaultId = vaultId,
                        objectType = envelope.objectType,
                        parentId = envelope.parentId,
                        encryptedPayload = encrypted.ciphertext,
                        nonce = encrypted.nonce,
                        rev = envelope.rev,
                        deletedAt = envelope.deletedAtMs?.let {
                            SupabaseRemoteDataSource.formatIsoTimestamp(it)
                        },
                        updatedAt = SupabaseRemoteDataSource.formatIsoTimestamp(System.currentTimeMillis())
                    )
                    remoteApi.upsertVaultObject(dto)
                }

                "vault_files" -> {
                    val file = json.decodeFromString<VaultFile>(item.payloadJson)
                    val entity = file.toEntity(securityGate)
                    val dto = RemoteVaultFileDto(
                        id = file.id,
                        vaultId = vaultId,
                        storagePath = file.storagePath,
                        encryptedMetadata = entity.encryptedMetadata,
                        metadataNonce = entity.metadataNonce,
                        sizeBytes = file.sizeBytes,
                        rev = file.rev,
                        deletedAt = file.deletedAt?.let {
                            SupabaseRemoteDataSource.formatIsoTimestamp(it)
                        },
                        updatedAt = SupabaseRemoteDataSource.formatIsoTimestamp(file.updatedAt)
                    )
                    val blobOk = if (file.localEncryptedPath != null && file.deletedAt == null) {
                        val localFile = File(file.localEncryptedPath)
                        if (localFile.exists()) {
                            remoteApi.uploadEncryptedFileBlob(vaultId, file.id, localFile.readBytes())
                        } else {
                            true
                        }
                    } else {
                        true
                    }
                    blobOk && remoteApi.upsertVaultFileMetadata(dto)
                }

                else -> true
            }

            if (success) {
                dao.removeSyncItemById(item.id)
                val remaining = dao.getPendingSyncQueue().size
                if (remaining == 0) {
                    _syncState.value = SyncStatus.Synced(System.currentTimeMillis())
                }
                true
            } else {
                dao.recordSyncFailure(item.id, "Supabase upsert returned false")
                val pending = dao.getPendingSyncQueue().size
                _syncState.value = SyncStatus.Offline(pending)
                false
            }
        } catch (e: Exception) {
            dao.recordSyncFailure(item.id, e.message)
            val pending = dao.getPendingSyncQueue().size
            _syncState.value = SyncStatus.Offline(pending)
            false
        }
    }

    /**
     * Flushes all pending items in `SyncQueueEntity` to Supabase.
     */
    suspend fun flushSyncQueue(): Int {
        if (!securityGate.isUnlocked() || securityGate.isDecoyModeActive()) return 0
        val dao = daoProvider()
        val pendingItems = dao.getPendingSyncQueue()
        if (!isNetworkAvailableProvider()) {
            _syncState.value = SyncStatus.Offline(pendingItems.size)
            return 0
        }
        if (pendingItems.isEmpty()) return 0

        var pushed = 0
        for ((index, item) in pendingItems.withIndex()) {
            val pct = ((index + 1) * 50) / pendingItems.size.coerceAtLeast(1)
            _syncState.value = SyncStatus.Syncing(
                progressPercent = pct,
                stepDescription = "Odesílání lokální fronty (${index + 1}/${pendingItems.size})…",
                pushedCount = pushed,
                pulledCount = 0
            )
            if (pushQueuedItem(item)) {
                pushed++
            }
        }
        return pushed
    }

    /**
     * Registers or updates the top-level row in the `vaults` table on Supabase.
     */
    suspend fun syncVaultHeader(
        settings: VaultSettings,
        integrityHashBytes: ByteArray
    ): Boolean {
        if (!securityGate.isUnlocked() || securityGate.isDecoyModeActive()) return false
        val vaultId = securityGate.requireVaultId()
        val settingsBytes = json.encodeToString(settings).encodeToByteArray()
        val encryptedSettings = securityGate.encryptPayload(settingsBytes)
        val dto = RemoteVaultDto(
            id = vaultId,
            schemaVersion = 1,
            encrypted_settings = encryptedSettings.ciphertext,
            settings_nonce = encryptedSettings.nonce,
            integrity_hash = integrityHashBytes,
            updatedAt = SupabaseRemoteDataSource.formatIsoTimestamp(System.currentTimeMillis())
        )
        return remoteApi.upsertVault(dto)
    }

    /**
     * Performs a complete bidirectional sync:
     * 1. Pulls remote changes updated since `lastSyncTimestampMs` (or full pull if `forceFull = true`),
     *    resolving any collisions with local `SyncQueue` items via `ConflictResolver`.
     * 2. Flushes all remaining items in `SyncQueueEntity` and unsynced `AuditEntryEntity` rows.
     */
    suspend fun performSync(
        forceFull: Boolean = false,
        conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.LAST_WRITE_WINS_WITH_HISTORY
    ): SyncStatus {
        if (!securityGate.isUnlocked() || securityGate.isDecoyModeActive()) {
            return SyncStatus.Synced()
        }
        val dao = daoProvider()
        if (!isNetworkAvailableProvider()) {
            val queued = dao.getPendingSyncQueue().size
            val status = SyncStatus.Offline(queued)
            _syncState.value = status
            return status
        }

        val vaultId = securityGate.requireVaultId()
        _syncState.value = SyncStatus.Syncing(
            progressPercent = 15,
            stepDescription = "Stahování změn ze Supabase…",
            pushedCount = 0,
            pulledCount = 0
        )

        val sinceIso = if (forceFull || lastSyncTimestampMs == 0L) {
            null
        } else {
            SupabaseRemoteDataSource.formatIsoTimestamp(lastSyncTimestampMs)
        }

        val remoteObjects = remoteApi.fetchVaultObjectsUpdatedSince(vaultId, sinceIso)
        val remoteFiles = remoteApi.fetchVaultFilesUpdatedSince(vaultId, sinceIso)
        var pulledCount = 0
        var conflictsCount = 0
        val newConflictMessages = mutableListOf<String>()

        for ((idx, remoteObj) in remoteObjects.withIndex()) {
            val pct = 15 + ((idx + 1) * 40) / remoteObjects.size.coerceAtLeast(1)
            _syncState.value = SyncStatus.Syncing(
                progressPercent = pct,
                stepDescription = "Dešifrování a slučování objektu ${idx + 1}/${remoteObjects.size}…",
                pushedCount = 0,
                pulledCount = pulledCount
            )
            val resolved = applyRemoteVaultObject(dao, remoteObj, conflictStrategy)
            if (resolved.first) pulledCount++
            if (resolved.second != null) {
                conflictsCount++
                newConflictMessages.add(resolved.second!!)
            }
        }

        for (remoteFile in remoteFiles) {
            if (applyRemoteVaultFile(dao, remoteFile)) {
                pulledCount++
            }
        }

        _syncState.value = SyncStatus.Syncing(
            progressPercent = 70,
            stepDescription = "Odesílání lokálních změn a audit logu…",
            pushedCount = 0,
            pulledCount = pulledCount
        )

        val pushedCount = flushSyncQueue()
        syncUnsyncedAuditEntries(dao, vaultId)

        lastSyncTimestampMs = System.currentTimeMillis()
        if (newConflictMessages.isNotEmpty()) {
            resolvedConflictsHistory = (newConflictMessages + resolvedConflictsHistory).take(20)
        }

        val remainingQueue = dao.getPendingSyncQueue().size
        val finalStatus = when {
            conflictsCount > 0 -> SyncStatus.ConflictOrWarning(
                message = newConflictMessages.first(),
                pendingCount = remainingQueue,
                resolvedConflictsCount = conflictsCount
            )
            remainingQueue > 0 -> SyncStatus.Offline(remainingQueue)
            else -> SyncStatus.Synced(lastSyncTimestampMs)
        }
        _syncState.value = finalStatus
        return finalStatus
    }

    private suspend fun applyRemoteVaultObject(
        dao: OpenVaultDao,
        remoteDto: RemoteVaultObjectDto,
        strategy: ConflictResolutionStrategy
    ): Pair<Boolean, String?> {
        val decryptedJson = try {
            securityGate.decryptString(remoteDto.encryptedPayload, remoteDto.nonce)
        } catch (_: Exception) {
            return false to null
        }

        val pendingLocal = dao.getPendingSyncItemForObject(remoteDto.id) != null
        val remoteUpdatedAt = SupabaseRemoteDataSource.parseIsoTimestamp(remoteDto.updatedAt)
        val remoteDeletedAt = remoteDto.deletedAt?.let { SupabaseRemoteDataSource.parseIsoTimestamp(it) }

        return when (remoteDto.objectType) {
            "note" -> {
                val remoteNote = runCatching { json.decodeFromString<Note>(decryptedJson) }
                    .getOrNull()?.copy(
                        rev = remoteDto.rev,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    ) ?: return false to null

                val localEntity = dao.getNoteById(remoteDto.id)
                if (localEntity == null) {
                    dao.upsertNote(remoteNote.toEntity())
                    true to null
                } else {
                    val localNote = localEntity.toDomain()
                    val outcome = ConflictResolver.resolveNoteConflict(
                        localNote = localNote,
                        remoteNote = remoteNote,
                        hasLocalUnpushedChanges = pendingLocal,
                        strategy = strategy
                    )
                    dao.upsertNote(outcome.winningItem.toEntity())
                    if (outcome.winner == ConflictWinner.REMOTE && pendingLocal) {
                        dao.removeSyncItemsForObjectUpToRev(remoteDto.id, remoteDto.rev)
                    }
                    if (outcome.hadCollision && outcome.collisionSummary != null) {
                        recordConflictAudit(dao, remoteDto.id, outcome.collisionSummary)
                    }
                    true to outcome.collisionSummary
                }
            }

            "password" -> {
                val remotePwd = runCatching { json.decodeFromString<PasswordItem>(decryptedJson) }
                    .getOrNull()?.copy(
                        rev = remoteDto.rev,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    ) ?: return false to null

                val localEntity = dao.getPasswordById(remoteDto.id)
                if (localEntity == null) {
                    dao.upsertPassword(remotePwd.toEntity(securityGate))
                    true to null
                } else {
                    val winner = ConflictResolver.determineWinner(
                        localRev = localEntity.rev,
                        localUpdatedAt = localEntity.updatedAt,
                        remoteRev = remotePwd.rev,
                        remoteUpdatedAt = remotePwd.updatedAt,
                        hasLocalUnpushedChanges = pendingLocal,
                        strategy = strategy
                    )
                    if (winner == ConflictWinner.REMOTE) {
                        dao.upsertPassword(remotePwd.toEntity(securityGate))
                        dao.removeSyncItemsForObjectUpToRev(remoteDto.id, remoteDto.rev)
                    }
                    val collisionMsg = if (pendingLocal) {
                        "Vyřešena kolize hesla '${remotePwd.title}' (vítěz: ${winner.name})"
                    } else null
                    true to collisionMsg
                }
            }

            "event" -> {
                val remoteEvent = runCatching { json.decodeFromString<CalendarEvent>(decryptedJson) }
                    .getOrNull()?.copy(
                        rev = remoteDto.rev,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    ) ?: return false to null

                val localEntity = dao.getEventById(remoteDto.id)
                if (localEntity == null || ConflictResolver.determineWinner(
                        localEntity.rev,
                        localEntity.updatedAt,
                        remoteEvent.rev,
                        remoteEvent.updatedAt,
                        pendingLocal,
                        strategy
                    ) == ConflictWinner.REMOTE
                ) {
                    dao.upsertEvent(remoteEvent.toEntity())
                    dao.removeSyncItemsForObjectUpToRev(remoteDto.id, remoteDto.rev)
                }
                true to null
            }

            "database" -> {
                val remoteDb = runCatching { json.decodeFromString<UserDatabase>(decryptedJson) }
                    .getOrNull()?.copy(
                        rev = remoteDto.rev,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    ) ?: return false to null

                val localEntity = dao.getDatabaseById(remoteDto.id)
                if (localEntity == null || ConflictResolver.determineWinner(
                        localEntity.rev,
                        localEntity.updatedAt,
                        remoteDb.rev,
                        remoteDb.updatedAt,
                        pendingLocal,
                        strategy
                    ) == ConflictWinner.REMOTE
                ) {
                    dao.upsertDatabase(remoteDb.toEntity())
                    dao.removeSyncItemsForObjectUpToRev(remoteDto.id, remoteDto.rev)
                }
                true to null
            }

            "record" -> {
                val remoteRec = runCatching { json.decodeFromString<DatabaseRecord>(decryptedJson) }
                    .getOrNull()?.copy(
                        rev = remoteDto.rev,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    ) ?: return false to null

                val localEntity = dao.getRecordById(remoteDto.id)
                if (localEntity == null || ConflictResolver.determineWinner(
                        localEntity.rev,
                        localEntity.updatedAt,
                        remoteRec.rev,
                        remoteRec.updatedAt,
                        pendingLocal,
                        strategy
                    ) == ConflictWinner.REMOTE
                ) {
                    dao.upsertRecord(remoteRec.toEntity())
                    dao.removeSyncItemsForObjectUpToRev(remoteDto.id, remoteDto.rev)
                }
                true to null
            }

            "collection" -> {
                val remoteCol = runCatching { json.decodeFromString<CollectionItem>(decryptedJson) }
                    .getOrNull()?.copy(
                        rev = remoteDto.rev,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    ) ?: return false to null

                val localEntity = dao.getCollectionById(remoteDto.id)
                if (localEntity == null || ConflictResolver.determineWinner(
                        localEntity.rev,
                        localEntity.updatedAt,
                        remoteCol.rev,
                        remoteCol.updatedAt,
                        pendingLocal,
                        strategy
                    ) == ConflictWinner.REMOTE
                ) {
                    dao.upsertCollection(remoteCol.toEntity())
                    dao.removeSyncItemsForObjectUpToRev(remoteDto.id, remoteDto.rev)
                }
                true to null
            }

            else -> false to null
        }
    }

    private suspend fun applyRemoteVaultFile(
        dao: OpenVaultDao,
        remoteFile: RemoteVaultFileDto
    ): Boolean {
        val metaJson = try {
            securityGate.decryptString(remoteFile.encryptedMetadata, remoteFile.metadataNonce)
        } catch (_: Exception) {
            return false
        }
        val payload = runCatching {
            json.decodeFromString<EncryptedFileMetadataPayload>(metaJson)
        }.getOrNull() ?: return false

        val localEntity = dao.getFileById(remoteFile.id)
        if (localEntity != null && localEntity.rev > remoteFile.rev) {
            return false
        }
        val updatedFile = VaultFile(
            id = remoteFile.id,
            vaultId = remoteFile.vaultId,
            folderId = payload.folderId,
            storagePath = remoteFile.storagePath,
            fileName = payload.fileName,
            mimeType = payload.mimeType,
            sizeBytes = remoteFile.sizeBytes,
            sha256Hex = payload.sha256Hex,
            localEncryptedPath = localEntity?.localEncryptedPath,
            thumbnailBase64 = payload.thumbnailBase64,
            previewText = payload.previewText,
            linkedObjectIds = payload.linkedObjectIds,
            isUploadedToCloud = true,
            createdAt = localEntity?.createdAt ?: SupabaseRemoteDataSource.parseIsoTimestamp(remoteFile.createdAt),
            updatedAt = SupabaseRemoteDataSource.parseIsoTimestamp(remoteFile.updatedAt),
            deletedAt = remoteFile.deletedAt?.let { SupabaseRemoteDataSource.parseIsoTimestamp(it) },
            rev = remoteFile.rev
        )
        dao.upsertFile(updatedFile.toEntity(securityGate))
        return true
    }

    private suspend fun syncUnsyncedAuditEntries(dao: OpenVaultDao, vaultId: String) {
        val unsynced = dao.getUnsyncedAuditEntries()
        for (entry in unsynced) {
            val remoteId = remoteApi.insertAuditEntry(
                RemoteVaultAuditDto(
                    vaultId = vaultId,
                    encryptedEntry = entry.encryptedPayload,
                    entryNonce = entry.nonce
                )
            )
            if (remoteId != null) {
                dao.markAuditEntrySynced(entry.id, remoteId)
            }
        }
    }

    private suspend fun recordConflictAudit(dao: OpenVaultDao, targetId: String, summary: String) {
        val vaultId = securityGate.getVaultIdOrNull() ?: return
        val entry = AuditEntry(
            vaultId = vaultId,
            category = AuditCategory.SYNC,
            actionType = "CONFLICT_RESOLVED",
            targetObjectId = targetId,
            summary = summary,
            deviceInfo = "SyncEngine LWW"
        )
        dao.insertAuditEntry(entry.toEntity(securityGate))
    }
}

@kotlinx.serialization.Serializable
data class QueuedVaultObjectEnvelope(
    val id: String,
    val objectType: String,
    val parentId: String?,
    val plainPayloadJson: String,
    val rev: Long,
    val deletedAtMs: Long?
)

private fun RemoteVaultDto(
    id: String,
    schemaVersion: Int,
    encrypted_settings: ByteArray,
    settings_nonce: ByteArray,
    integrity_hash: ByteArray,
    updatedAt: String
) = com.openvault.app.data.remote.RemoteVaultDto(
    id = id,
    schemaVersion = schemaVersion,
    encryptedSettings = encrypted_settings,
    settingsNonce = settings_nonce,
    integrityHash = integrity_hash,
    updatedAt = updatedAt
)
