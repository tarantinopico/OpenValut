package com.openvault.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenVaultDao {

    // --- NOTES ---
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllNotesIncludingDeleted(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: String)

    // --- PASSWORDS ---
    @Query("SELECT * FROM passwords WHERE deletedAt IS NULL ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeActivePasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords ORDER BY updatedAt DESC")
    suspend fun getAllPasswordsIncludingDeleted(): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getPasswordById(id: String): PasswordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPassword(password: PasswordEntity)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordPermanently(id: String)

    // --- CALENDAR EVENTS ---
    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NULL ORDER BY startAt ASC")
    fun observeActiveEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY startAt ASC")
    suspend fun getAllEventsIncludingDeleted(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEventPermanently(id: String)

    // --- VAULT FILES (VFS) ---
    @Query("SELECT * FROM vault_files WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActiveFiles(): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files ORDER BY updatedAt DESC")
    suspend fun getAllFilesIncludingDeleted(): List<VaultFileEntity>

    @Query("SELECT * FROM vault_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): VaultFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: VaultFileEntity)

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun deleteFilePermanently(id: String)

    // --- USER DATABASES ---
    @Query("SELECT * FROM user_databases WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActiveDatabases(): Flow<List<UserDatabaseEntity>>

    @Query("SELECT * FROM user_databases ORDER BY updatedAt DESC")
    suspend fun getAllDatabasesIncludingDeleted(): List<UserDatabaseEntity>

    @Query("SELECT * FROM user_databases WHERE id = :id LIMIT 1")
    suspend fun getDatabaseById(id: String): UserDatabaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDatabase(database: UserDatabaseEntity)

    @Query("DELETE FROM user_databases WHERE id = :id")
    suspend fun deleteDatabasePermanently(id: String)

    // --- DATABASE RECORDS ---
    @Query("SELECT * FROM database_records WHERE deletedAt IS NULL ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAllActiveRecords(): Flow<List<DatabaseRecordEntity>>

    @Query("SELECT * FROM database_records WHERE databaseId = :databaseId AND deletedAt IS NULL ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeActiveRecordsForDatabase(databaseId: String): Flow<List<DatabaseRecordEntity>>

    @Query("SELECT * FROM database_records WHERE databaseId = :databaseId AND deletedAt IS NULL")
    suspend fun getRecordsForDatabase(databaseId: String): List<DatabaseRecordEntity>

    @Query("SELECT * FROM database_records ORDER BY updatedAt DESC")
    suspend fun getAllRecordsIncludingDeleted(): List<DatabaseRecordEntity>

    @Query("SELECT * FROM database_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): DatabaseRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: DatabaseRecordEntity)

    @Query("DELETE FROM database_records WHERE id = :id")
    suspend fun deleteRecordPermanently(id: String)

    // --- COLLECTIONS ---
    @Query("SELECT * FROM collections WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    fun observeActiveCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC")
    suspend fun getAllCollectionsIncludingDeleted(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionPermanently(id: String)

    // --- AUDIT LOG (APPEND-ONLY) ---
    @Query("SELECT * FROM audit_entries ORDER BY createdAt DESC")
    fun observeAuditEntries(): Flow<List<AuditEntryEntity>>

    @Query("SELECT * FROM audit_entries ORDER BY createdAt DESC")
    suspend fun getAllAuditEntries(): List<AuditEntryEntity>

    @Query("SELECT * FROM audit_entries WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedAuditEntries(): List<AuditEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEntry(entry: AuditEntryEntity): Long

    @Query("UPDATE audit_entries SET isSynced = 1, remoteId = :remoteId WHERE id = :localId")
    suspend fun markAuditEntrySynced(localId: Long, remoteId: Long?)

    // --- SYNC QUEUE ---
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    fun observeSyncQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getPendingSyncQueue(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE objectId = :objectId LIMIT 1")
    suspend fun getPendingSyncItemForObject(objectId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncItem(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun removeSyncItemById(id: Long)

    @Query("DELETE FROM sync_queue WHERE objectId = :objectId AND rev <= :rev")
    suspend fun removeSyncItemsForObjectUpToRev(objectId: String, rev: Long)

    @Query("UPDATE sync_queue SET retries = retries + 1, lastError = :error WHERE id = :id")
    suspend fun recordSyncFailure(id: Long, error: String?)

    // --- WIPE ---
    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM passwords")
    suspend fun clearPasswords()

    @Query("DELETE FROM calendar_events")
    suspend fun clearEvents()

    @Query("DELETE FROM vault_files")
    suspend fun clearFiles()

    @Query("DELETE FROM user_databases")
    suspend fun clearDatabases()

    @Query("DELETE FROM database_records")
    suspend fun clearRecords()

    @Query("DELETE FROM collections")
    suspend fun clearCollections()

    @Query("DELETE FROM audit_entries")
    suspend fun clearAuditEntries()

    @Query("DELETE FROM sync_queue")
    suspend fun clearSyncQueue()
}
