package com.openvault.app.data.repository

import android.content.Context
import android.os.Build
import android.util.Base64
import com.openvault.app.data.local.EntityMappers
import com.openvault.app.data.local.EntityMappers.toDomain
import com.openvault.app.data.local.EntityMappers.toEntity
import com.openvault.app.data.local.OpenVaultDao
import com.openvault.app.data.local.OpenVaultDatabase
import com.openvault.app.domain.model.AuditCategory
import com.openvault.app.domain.model.AuditEntry
import com.openvault.app.domain.model.CalendarEvent
import com.openvault.app.domain.model.CollectionItem
import com.openvault.app.domain.model.DatabaseFieldSchema
import com.openvault.app.domain.model.DatabaseFieldType
import com.openvault.app.domain.model.DatabaseRecord
import com.openvault.app.domain.model.Note
import com.openvault.app.domain.model.NoteRevision
import com.openvault.app.domain.model.NoteTemplate
import com.openvault.app.domain.model.PasswordHistoryEntry
import com.openvault.app.domain.model.PasswordItem
import com.openvault.app.domain.model.SecurityReport
import com.openvault.app.domain.model.UserDatabase
import com.openvault.app.domain.model.VaultFile
import com.openvault.app.security.CryptoEngine
import com.openvault.app.security.OvaultBackupManager
import com.openvault.app.security.OvaultEmbeddedFileBlob
import com.openvault.app.security.OvaultImportMode
import com.openvault.app.security.OvaultSnapshot
import com.openvault.app.security.PasswordAnalyzer
import com.openvault.app.security.SecurityGate
import com.openvault.app.security.hexToByteArray
import com.openvault.app.security.toHexString
import com.openvault.app.sync.SyncEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

@OptIn(ExperimentalCoroutinesApi::class)
class OpenVaultRepository(
    private val context: Context,
    private val securityGate: SecurityGate,
    private val settingsRepository: SettingsRepository,
    private val syncEngineProvider: () -> SyncEngine,
    private val inMemoryDbForTest: Boolean = false
) {
    private val json = EntityMappers.json

    @Volatile
    private var currentDb: OpenVaultDatabase? = null

    @Volatile
    private var currentDbModeDecoy: Boolean? = null

    @Volatile
    private var currentDbUnlocked: Boolean? = null

    private val dbEpochTrigger = MutableStateFlow(0L)

    init {
        securityGate.setOnCryptographicWipeListener {
            wipeAllLocalDatabases()
        }
    }

    @Synchronized
    fun getActiveDao(): OpenVaultDao {
        val isDecoy = securityGate.isDecoyModeActive()
        val isUnlocked = securityGate.isUnlocked()
        val existing = currentDb
        if (existing != null && currentDbModeDecoy == isDecoy && currentDbUnlocked == isUnlocked) {
            return existing.openVaultDao()
        }
        runCatching { existing?.close() }
        val sqlKey = if (isUnlocked) {
            securityGate.deriveSqlCipherKeyBytes()
        } else {
            null
        }
        val newDb = OpenVaultDatabase.buildDatabase(
            context = context,
            sqlCipherKeyBytes = sqlKey,
            isDecoyMode = isDecoy,
            inMemoryForTesting = inMemoryDbForTest
        )
        CryptoEngine.zeroize(sqlKey)
        currentDb = newDb
        currentDbModeDecoy = isDecoy
        currentDbUnlocked = isUnlocked
        return newDb.openVaultDao()
    }

    @Synchronized
    fun refreshDatabaseSessionForUnlockState() {
        runCatching { currentDb?.close() }
        currentDb = null
        currentDbModeDecoy = null
        currentDbUnlocked = null
        if (securityGate.isUnlocked()) {
            runCatching { getActiveDao() }
        }
        dbEpochTrigger.value = System.currentTimeMillis()
    }

    @Synchronized
    fun wipeAllLocalDatabases() {
        runCatching { currentDb?.close() }
        currentDb = null
        currentDbModeDecoy = null
        currentDbUnlocked = null
        context.deleteDatabase(OpenVaultDatabase.PRIMARY_DB_NAME)
        context.deleteDatabase(OpenVaultDatabase.DECOY_DB_NAME)
        dbEpochTrigger.value = System.currentTimeMillis()
    }

    // --- REACTIVE FLOWS ---
    val activeNotesFlow: Flow<List<Note>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeActiveNotes().collect { list ->
                        send(list.map { it.toDomain() })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    val activePasswordsFlow: Flow<List<PasswordItem>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeActivePasswords().collect { list ->
                        if (!securityGate.isUnlocked()) send(emptyList())
                        else send(list.map { it.toDomain(securityGate) })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    val activeEventsFlow: Flow<List<CalendarEvent>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeActiveEvents().collect { list ->
                        send(list.map { it.toDomain() })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    val activeFilesFlow: Flow<List<VaultFile>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeActiveFiles().collect { list ->
                        send(list.map { it.toDomain() })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    val activeDatabasesFlow: Flow<List<UserDatabase>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeActiveDatabases().collect { list ->
                        send(list.map { it.toDomain() })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    val activeRecordsFlow: Flow<List<DatabaseRecord>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeAllActiveRecords().collect { list ->
                        send(list.map { it.toDomain() })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    val activeCollectionsFlow: Flow<List<CollectionItem>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeActiveCollections().collect { list ->
                        send(list.map { it.toDomain() })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    val auditEntriesFlow: Flow<List<AuditEntry>> = dbEpochTrigger.flatMapLatest {
        if (!securityGate.isUnlocked()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            kotlinx.coroutines.flow.channelFlow {
                runCatching {
                    getActiveDao().observeAuditEntries().collect { list ->
                        send(list.map { it.toDomain() })
                    }
                }.onFailure { send(emptyList()) }
            }
        }
    }

    // --- PREDEFINED TEMPLATES (ON-DEMAND) ---
    fun getNoteTemplates(): List<NoteTemplate> = listOf(
        NoteTemplate(
            id = "blank",
            name = "Prázdná poznámka",
            icon = "note",
            defaultTitle = "",
            markdownContent = "",
            defaultTags = emptyList()
        ),
        NoteTemplate(
            id = "meeting",
            name = "Zápis ze schůzky",
            icon = "groups",
            defaultTitle = "Schůzka – ",
            markdownContent = "# Zápis ze schůzky\n\n## Účastníci\n- \n\n## Hlavní body\n- \n\n## Akční úkoly\n- [ ] \n- [ ] ",
            defaultTags = listOf("schůzka", "práce")
        ),
        NoteTemplate(
            id = "journal",
            name = "Osobní deník",
            icon = "book",
            defaultTitle = "Deníkový záznam",
            markdownContent = "# Deníkový záznam\n\n## Klíčové události dne\n- \n\n## Myšlenky a poznámky\n> ",
            defaultTags = listOf("deník", "osobní")
        ),
        NoteTemplate(
            id = "project",
            name = "Projektový plán",
            icon = "rocket",
            defaultTitle = "Projekt: ",
            markdownContent = "# Cíl projektu\n\n## Milníky\n- [ ] Fáze 1: Návrh\n- [ ] Fáze 2: Implementace\n- [ ] Fáze 3: Verifikace\n\n## Technické poznámky\n```\n\n```",
            defaultTags = listOf("projekt")
        ),
        NoteTemplate(
            id = "security_memo",
            name = "Bezpečnostní záznam",
            icon = "shield",
            defaultTitle = "Bezpečnostní protokol",
            markdownContent = "# Bezpečnostní protokol\n\n- **Systém / Služba:** \n- **Úroveň citlivosti:** Vysoká (E2E)\n\n## Záložní kódy / Klíče\n```\n\n```",
            defaultTags = listOf("bezpečnost", "recovery")
        )
    )

    fun getDatabaseTemplates(): List<UserDatabase> {
        val vaultId = securityGate.getVaultIdOrNull() ?: "template"
        return listOf(
            UserDatabase(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                name = "Krypto seedy a peněženky",
                description = "Bezpečné uložení BIP-39 seed frází, adres a derivačních cest",
                icon = "key",
                schema = listOf(
                    DatabaseFieldSchema("wallet_name", "Název peněženky", DatabaseFieldType.TEXT, required = true, sortOrder = 0),
                    DatabaseFieldSchema("network", "Síť (BTC/ETH/SOL)", DatabaseFieldType.TEXT, required = true, sortOrder = 1),
                    DatabaseFieldSchema("seed_phrase", "Seed fráze (12/24 slov)", DatabaseFieldType.PASSWORD, required = true, sortOrder = 2),
                    DatabaseFieldSchema("derivation_path", "Derivační cesta", DatabaseFieldType.TEXT, defaultValue = "m/84'/0'/0'/0/0", sortOrder = 3),
                    DatabaseFieldSchema("notes", "Poznámka", DatabaseFieldType.NOTE, sortOrder = 4)
                )
            ),
            UserDatabase(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                name = "Platební a bankovní karty",
                description = "Šifrovaná evidence platebních karet, PINů a limitů",
                icon = "credit_card",
                schema = listOf(
                    DatabaseFieldSchema("card_label", "Označení karty", DatabaseFieldType.TEXT, required = true, sortOrder = 0),
                    DatabaseFieldSchema("card_number", "Číslo karty (PAN)", DatabaseFieldType.PASSWORD, required = true, sortOrder = 1),
                    DatabaseFieldSchema("expiry", "Platnost (MM/RR)", DatabaseFieldType.TEXT, required = true, sortOrder = 2),
                    DatabaseFieldSchema("cvv", "CVV / CVC", DatabaseFieldType.PASSWORD, required = true, sortOrder = 3),
                    DatabaseFieldSchema("pin", "PIN kód", DatabaseFieldType.PASSWORD, sortOrder = 4)
                )
            ),
            UserDatabase(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                name = "Licenční klíče a certifikáty",
                description = "Softwarové licence, SSH/PGP otisky a API tokeny",
                icon = "verified",
                schema = listOf(
                    DatabaseFieldSchema("software", "Produkt / Služba", DatabaseFieldType.TEXT, required = true, sortOrder = 0),
                    DatabaseFieldSchema("license_key", "Licenční klíč / Token", DatabaseFieldType.PASSWORD, required = true, sortOrder = 1),
                    DatabaseFieldSchema("portal_url", "Správa licence (URL)", DatabaseFieldType.URL, sortOrder = 2),
                    DatabaseFieldSchema("expires_on", "Datum expirace", DatabaseFieldType.DATE, sortOrder = 3),
                    DatabaseFieldSchema("active", "Aktivní", DatabaseFieldType.BOOLEAN, defaultValue = "true", sortOrder = 4)
                )
            ),
            UserDatabase(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                name = "Zdravotní záznamy",
                description = "Osobní zdravotní dokumentace, léky a výsledky vyšetření",
                icon = "medical",
                schema = listOf(
                    DatabaseFieldSchema("record_title", "Název vyšetření / záznamu", DatabaseFieldType.TEXT, required = true, sortOrder = 0),
                    DatabaseFieldSchema("visit_date", "Datum", DatabaseFieldType.DATE, required = true, sortOrder = 1),
                    DatabaseFieldSchema("doctor", "Lékař / Pracoviště", DatabaseFieldType.TEXT, sortOrder = 2),
                    DatabaseFieldSchema("summary", "Zpráva a medikace", DatabaseFieldType.NOTE, sortOrder = 3),
                    DatabaseFieldSchema("attachment", "Přiložená zpráva (VFS)", DatabaseFieldType.FILE, sortOrder = 4)
                )
            )
        )
    }

    // --- NOTES CRUD ---
    suspend fun saveNote(
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
        templateName: String? = null
    ): Note {
        val vaultId = securityGate.requireVaultId()
        val dao = getActiveDao()
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getNoteById(it)?.toDomain() }

        val resolvedTitle = title.trim().ifEmpty {
            body.lineSequence().firstOrNull { it.isNotBlank() }
                ?.removePrefix("#")?.trim()?.take(48)
                ?: "Poznámka ${java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now))}"
        }

        val updatedHistory = if (existing != null && (existing.body != body || existing.title != resolvedTitle)) {
            val prevRev = NoteRevision(
                rev = existing.rev,
                title = existing.title,
                body = existing.body,
                timestamp = existing.updatedAt
            )
            (listOf(prevRev) + existing.history).take(25)
        } else {
            existing?.history ?: emptyList()
        }

        val nextRev = (existing?.rev ?: 0L) + 1L
        val note = Note(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            vaultId = vaultId,
            collectionId = collectionId,
            title = resolvedTitle,
            body = body,
            tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            attachmentFileIds = attachmentFileIds,
            linkedEventIds = linkedEventIds,
            history = updatedHistory,
            isPinned = isPinned,
            isFavorite = isFavorite,
            colorHex = colorHex,
            templateName = templateName,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
            rev = nextRev
        )

        dao.upsertNote(note.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "note",
            objectId = note.id,
            parentId = note.collectionId,
            plainJsonPayload = json.encodeToString(note),
            rev = note.rev,
            deletedAtMs = null
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = if (existing == null) "NOTE_CREATED" else "NOTE_UPDATED",
            targetObjectId = note.id,
            summary = "Uložena šifrovaná poznámka (rev ${note.rev})"
        )
        return note
    }

    suspend fun toggleNotePinned(noteId: String) {
        val dao = getActiveDao()
        val existing = dao.getNoteById(noteId)?.toDomain() ?: return
        val updated = existing.copy(
            isPinned = !existing.isPinned,
            updatedAt = System.currentTimeMillis(),
            rev = existing.rev + 1L
        )
        dao.upsertNote(updated.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "note",
            objectId = updated.id,
            parentId = updated.collectionId,
            plainJsonPayload = json.encodeToString(updated),
            rev = updated.rev,
            deletedAtMs = null
        )
    }

    suspend fun toggleNoteFavorite(noteId: String) {
        val dao = getActiveDao()
        val existing = dao.getNoteById(noteId)?.toDomain() ?: return
        val updated = existing.copy(
            isFavorite = !existing.isFavorite,
            updatedAt = System.currentTimeMillis(),
            rev = existing.rev + 1L
        )
        dao.upsertNote(updated.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "note",
            objectId = updated.id,
            parentId = updated.collectionId,
            plainJsonPayload = json.encodeToString(updated),
            rev = updated.rev,
            deletedAtMs = null
        )
    }

    suspend fun restoreNoteRevision(noteId: String, revision: NoteRevision): Note? {
        val existing = getActiveDao().getNoteById(noteId)?.toDomain() ?: return null
        return saveNote(
            id = existing.id,
            title = revision.title,
            body = revision.body,
            tags = existing.tags,
            collectionId = existing.collectionId,
            attachmentFileIds = existing.attachmentFileIds,
            linkedEventIds = existing.linkedEventIds,
            isPinned = existing.isPinned,
            isFavorite = existing.isFavorite,
            colorHex = existing.colorHex,
            templateName = existing.templateName
        )
    }

    suspend fun duplicateNote(noteId: String): Note? {
        val existing = getActiveDao().getNoteById(noteId)?.toDomain() ?: return null
        return saveNote(
            id = null,
            title = "${existing.title} (Kopie)",
            body = existing.body,
            tags = existing.tags,
            collectionId = existing.collectionId,
            attachmentFileIds = existing.attachmentFileIds,
            linkedEventIds = existing.linkedEventIds,
            isPinned = existing.isPinned,
            isFavorite = existing.isFavorite,
            colorHex = existing.colorHex,
            templateName = existing.templateName
        )
    }

    suspend fun softDeleteNote(noteId: String) {
        val dao = getActiveDao()
        val existing = dao.getNoteById(noteId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(
            deletedAt = now,
            updatedAt = now,
            rev = existing.rev + 1L
        )
        dao.upsertNote(deleted.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "note",
            objectId = deleted.id,
            parentId = deleted.collectionId,
            plainJsonPayload = json.encodeToString(deleted),
            rev = deleted.rev,
            deletedAtMs = now
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = "NOTE_DELETED",
            targetObjectId = deleted.id,
            summary = "Poznámka přesunuta do smazaných (soft-delete, rev ${deleted.rev})"
        )
    }

    // --- PASSWORDS CRUD ---
    suspend fun savePassword(
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
        expiresAt: Long? = null
    ): PasswordItem {
        val vaultId = securityGate.requireVaultId()
        val dao = getActiveDao()
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getPasswordById(it)?.toDomain(securityGate) }

        val strength = PasswordAnalyzer.analyzePassword(password)
        val pwdHistory = if (existing != null && existing.password != password) {
            (listOf(PasswordHistoryEntry(existing.password, existing.lastChangedAt)) + existing.passwordHistory).take(15)
        } else {
            existing?.passwordHistory ?: emptyList()
        }
        val lastChanged = if (existing == null || existing.password != password) now else existing.lastChangedAt
        val nextRev = (existing?.rev ?: 0L) + 1L

        val item = PasswordItem(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            vaultId = vaultId,
            collectionId = collectionId,
            title = title.trim().ifEmpty { "Záznam hesla" },
            username = username.trim(),
            password = password,
            url = url.trim(),
            totpSecret = totpSecret?.trim()?.ifEmpty { null },
            notes = notes,
            category = category,
            strengthScore = strength.score,
            isFavorite = isFavorite,
            expiresAt = expiresAt,
            lastChangedAt = lastChanged,
            passwordHistory = pwdHistory,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
            rev = nextRev
        )

        dao.upsertPassword(item.toEntity(securityGate))
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "password",
            objectId = item.id,
            parentId = item.collectionId,
            plainJsonPayload = json.encodeToString(item),
            rev = item.rev,
            deletedAtMs = null
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = if (existing == null) "PASSWORD_CREATED" else "PASSWORD_UPDATED",
            targetObjectId = item.id,
            summary = "Uložena položka hesla v kategorii ${item.category} (rev ${item.rev})"
        )
        return item
    }

    suspend fun togglePasswordFavorite(passwordId: String) {
        val dao = getActiveDao()
        val existing = dao.getPasswordById(passwordId)?.toDomain(securityGate) ?: return
        val updated = existing.copy(
            isFavorite = !existing.isFavorite,
            updatedAt = System.currentTimeMillis(),
            rev = existing.rev + 1L
        )
        dao.upsertPassword(updated.toEntity(securityGate))
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "password",
            objectId = updated.id,
            parentId = updated.collectionId,
            plainJsonPayload = json.encodeToString(updated),
            rev = updated.rev,
            deletedAtMs = null
        )
    }

    suspend fun softDeletePassword(passwordId: String) {
        val dao = getActiveDao()
        val existing = dao.getPasswordById(passwordId)?.toDomain(securityGate) ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(
            deletedAt = now,
            updatedAt = now,
            rev = existing.rev + 1L
        )
        dao.upsertPassword(deleted.toEntity(securityGate))
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "password",
            objectId = deleted.id,
            parentId = deleted.collectionId,
            plainJsonPayload = json.encodeToString(deleted),
            rev = deleted.rev,
            deletedAtMs = now
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = "PASSWORD_DELETED",
            targetObjectId = deleted.id,
            summary = "Smazána položka hesla (soft-delete, rev ${deleted.rev})"
        )
    }

    // --- CALENDAR EVENTS CRUD ---
    suspend fun saveCalendarEvent(
        id: String? = null,
        title: String,
        description: String = "",
        startAt: Long,
        endAt: Long,
        allDay: Boolean = false,
        remindersMinutesBefore: List<Int> = listOf(15),
        recurrenceRule: com.openvault.app.domain.model.RecurrenceRule = com.openvault.app.domain.model.RecurrenceRule.NONE,
        linkedNoteIds: List<String> = emptyList(),
        linkedRecordIds: List<String> = emptyList(),
        systemCalendarEventId: Long? = null,
        colorHex: String? = "#10B981"
    ): CalendarEvent {
        val vaultId = securityGate.requireVaultId()
        val dao = getActiveDao()
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getEventById(it)?.toDomain() }
        val nextRev = (existing?.rev ?: 0L) + 1L

        val event = CalendarEvent(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            vaultId = vaultId,
            title = title.trim().ifEmpty { "Událost v trezoru" },
            description = description,
            startAt = startAt,
            endAt = endAt.coerceAtLeast(startAt),
            allDay = allDay,
            remindersMinutesBefore = remindersMinutesBefore,
            recurrenceRule = recurrenceRule,
            linkedNoteIds = linkedNoteIds,
            linkedRecordIds = linkedRecordIds,
            systemCalendarEventId = systemCalendarEventId ?: existing?.systemCalendarEventId,
            colorHex = colorHex,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
            rev = nextRev
        )

        dao.upsertEvent(event.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "event",
            objectId = event.id,
            parentId = null,
            plainJsonPayload = json.encodeToString(event),
            rev = event.rev,
            deletedAtMs = null
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = if (existing == null) "EVENT_CREATED" else "EVENT_UPDATED",
            targetObjectId = event.id,
            summary = "Uložena kalendářní událost (rev ${event.rev})"
        )
        return event
    }

    suspend fun softDeleteCalendarEvent(eventId: String) {
        val dao = getActiveDao()
        val existing = dao.getEventById(eventId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(
            deletedAt = now,
            updatedAt = now,
            rev = existing.rev + 1L
        )
        dao.upsertEvent(deleted.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "event",
            objectId = deleted.id,
            parentId = null,
            plainJsonPayload = json.encodeToString(deleted),
            rev = deleted.rev,
            deletedAtMs = now
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = "EVENT_DELETED",
            targetObjectId = deleted.id,
            summary = "Kalendářní událost smazána (rev ${deleted.rev})"
        )
    }

    // --- VFS ENCRYPTED FILES CRUD ---
    suspend fun uploadAndEncryptFile(
        fileName: String,
        mimeType: String,
        plainBytes: ByteArray,
        folderId: String? = null,
        linkedObjectIds: List<String> = emptyList()
    ): VaultFile {
        val vaultId = securityGate.requireVaultId()
        val dao = getActiveDao()
        val fileId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val sha256Hex = CryptoEngine.computeSha256Hex(plainBytes)
        val encryptedBlob = securityGate.encryptPayload(plainBytes, aad = fileId.encodeToByteArray())
        val packedEncryptedBytes = CryptoEngine.packNonceAndCiphertext(encryptedBlob)

        val blobsDir = File(context.filesDir, "vault_blobs").apply { mkdirs() }
        val localEncFile = File(blobsDir, "$fileId.enc")
        localEncFile.writeBytes(packedEncryptedBytes)

        val previewText = if (mimeType.startsWith("text/") ||
            fileName.endsWith(".md", true) ||
            fileName.endsWith(".json", true) ||
            fileName.endsWith(".txt", true)
        ) {
            runCatching { plainBytes.decodeToString().take(2048) }.getOrNull()
        } else null

        val thumbnailBase64 = if (mimeType.startsWith("image/") && plainBytes.size <= 512 * 1024) {
            try {
                Base64.encodeToString(plainBytes, Base64.NO_WRAP)
            } catch (_: Throwable) {
                java.util.Base64.getEncoder().encodeToString(plainBytes)
            }
        } else null

        val vaultFile = VaultFile(
            id = fileId,
            vaultId = vaultId,
            folderId = folderId,
            storagePath = "$vaultId/$fileId.enc",
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = plainBytes.size.toLong(),
            sha256Hex = sha256Hex,
            localEncryptedPath = localEncFile.absolutePath,
            thumbnailBase64 = thumbnailBase64,
            previewText = previewText,
            linkedObjectIds = linkedObjectIds,
            isUploadedToCloud = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            rev = 1L
        )

        dao.upsertFile(vaultFile.toEntity(securityGate))
        val synced = syncEngineProvider().enqueueAndSyncFile(vaultFile, packedEncryptedBytes)
        if (synced) {
            val uploaded = vaultFile.copy(isUploadedToCloud = true)
            dao.upsertFile(uploaded.toEntity(securityGate))
        }
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = "FILE_ENCRYPTED_UPLOAD",
            targetObjectId = vaultFile.id,
            summary = "Zašifrován a uložen soubor do VFS (${vaultFile.sizeBytes} B, SHA-256: ${sha256Hex.take(12)}…)"
        )
        return vaultFile
    }

    suspend fun decryptFileBytesInMemory(fileId: String): ByteArray? {
        val dao = getActiveDao()
        val entity = dao.getFileById(fileId) ?: return null
        val path = entity.localEncryptedPath ?: return null
        val encFile = File(path)
        if (!encFile.exists()) return null

        val packed = encFile.readBytes()
        val unpacked = CryptoEngine.unpackNonceAndCiphertext(packed)
        return securityGate.decryptPayload(
            ciphertext = unpacked.ciphertext,
            nonce = unpacked.nonce,
            aad = fileId.encodeToByteArray()
        )
    }

    suspend fun renameOrMoveVaultFile(
        fileId: String,
        newFileName: String,
        newFolderId: String?,
        newLinkedObjectIds: List<String>? = null
    ): VaultFile? {
        val dao = getActiveDao()
        val existing = dao.getFileById(fileId)?.toDomain() ?: return null
        val updated = existing.copy(
            fileName = newFileName.trim().ifEmpty { existing.fileName },
            folderId = newFolderId,
            linkedObjectIds = newLinkedObjectIds ?: existing.linkedObjectIds,
            updatedAt = System.currentTimeMillis(),
            rev = existing.rev + 1L
        )
        dao.upsertFile(updated.toEntity(securityGate))
        syncEngineProvider().enqueueAndSyncFile(updated, null)
        return updated
    }

    suspend fun softDeleteVaultFile(fileId: String) {
        val dao = getActiveDao()
        val existing = dao.getFileById(fileId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(
            deletedAt = now,
            updatedAt = now,
            rev = existing.rev + 1L
        )
        dao.upsertFile(deleted.toEntity(securityGate))
        syncEngineProvider().enqueueAndSyncFile(deleted, null)
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = "FILE_DELETED",
            targetObjectId = fileId,
            summary = "Šifrovaný soubor ve VFS smazán (rev ${deleted.rev})"
        )
    }

    // --- USER DATABASES & SCHEMA MIGRATION ---
    suspend fun saveUserDatabase(
        id: String? = null,
        name: String,
        description: String,
        icon: String = "database",
        schema: List<DatabaseFieldSchema>
    ): UserDatabase {
        val vaultId = securityGate.requireVaultId()
        val dao = getActiveDao()
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getDatabaseById(it)?.toDomain() }
        val normalizedSchema = schema.mapIndexed { idx, field -> field.copy(sortOrder = idx) }
        val nextRev = (existing?.rev ?: 0L) + 1L

        val dbObj = UserDatabase(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            vaultId = vaultId,
            name = name.trim().ifEmpty { "Vlastní databáze" },
            description = description.trim(),
            icon = icon,
            schema = normalizedSchema,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
            rev = nextRev
        )
        dao.upsertDatabase(dbObj.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "database",
            objectId = dbObj.id,
            parentId = null,
            plainJsonPayload = json.encodeToString(dbObj),
            rev = dbObj.rev,
            deletedAtMs = null
        )

        // Automatically migrate existing records if schema changed on an existing database
        if (existing != null) {
            val existingRecords = dao.getRecordsForDatabase(dbObj.id).map { it.toDomain() }
            val validFieldIds = normalizedSchema.map { it.id }.toSet()
            for (rec in existingRecords) {
                val migratedMap = mutableMapOf<String, String>()
                for (field in normalizedSchema) {
                    val prevValue = rec.values[field.id]
                    migratedMap[field.id] = prevValue ?: field.defaultValue
                }
                if (migratedMap != rec.values || !rec.values.keys.all { it in validFieldIds }) {
                    val migratedRec = rec.copy(
                        values = migratedMap,
                        updatedAt = now,
                        rev = rec.rev + 1L
                    )
                    dao.upsertRecord(migratedRec.toEntity())
                    syncEngineProvider().enqueueAndSyncObject(
                        objectType = "record",
                        objectId = migratedRec.id,
                        parentId = dbObj.id,
                        plainJsonPayload = json.encodeToString(migratedRec),
                        rev = migratedRec.rev,
                        deletedAtMs = null
                    )
                }
            }
        }

        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = if (existing == null) "DATABASE_CREATED" else "DATABASE_SCHEMA_MIGRATED",
            targetObjectId = dbObj.id,
            summary = "Uloženo schéma databáze (${normalizedSchema.size} polí, rev ${dbObj.rev})"
        )
        return dbObj
    }

    suspend fun softDeleteUserDatabase(databaseId: String) {
        val dao = getActiveDao()
        val existing = dao.getDatabaseById(databaseId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(deletedAt = now, updatedAt = now, rev = existing.rev + 1L)
        dao.upsertDatabase(deleted.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "database",
            objectId = deleted.id,
            parentId = null,
            plainJsonPayload = json.encodeToString(deleted),
            rev = deleted.rev,
            deletedAtMs = now
        )
    }

    // --- DATABASE RECORDS CRUD ---
    suspend fun saveDatabaseRecord(
        id: String? = null,
        databaseId: String,
        values: Map<String, String>,
        isFavorite: Boolean = false
    ): DatabaseRecord {
        val vaultId = securityGate.requireVaultId()
        val dao = getActiveDao()
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getRecordById(it)?.toDomain() }
        val nextRev = (existing?.rev ?: 0L) + 1L

        val record = DatabaseRecord(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            vaultId = vaultId,
            databaseId = databaseId,
            values = values,
            isFavorite = isFavorite,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
            rev = nextRev
        )
        dao.upsertRecord(record.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "record",
            objectId = record.id,
            parentId = databaseId,
            plainJsonPayload = json.encodeToString(record),
            rev = record.rev,
            deletedAtMs = null
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = if (existing == null) "RECORD_CREATED" else "RECORD_UPDATED",
            targetObjectId = record.id,
            summary = "Uložen záznam v uživatelské databázi (rev ${record.rev})"
        )
        return record
    }

    suspend fun softDeleteDatabaseRecord(recordId: String) {
        val dao = getActiveDao()
        val existing = dao.getRecordById(recordId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(deletedAt = now, updatedAt = now, rev = existing.rev + 1L)
        dao.upsertRecord(deleted.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "record",
            objectId = deleted.id,
            parentId = deleted.databaseId,
            plainJsonPayload = json.encodeToString(deleted),
            rev = deleted.rev,
            deletedAtMs = now
        )
    }

    // --- COLLECTIONS / FOLDERS CRUD ---
    suspend fun saveCollection(
        id: String? = null,
        name: String,
        parentId: String? = null,
        icon: String = "folder",
        colorHex: String? = "#10B981",
        sortOrder: Int = 0
    ): CollectionItem {
        val vaultId = securityGate.requireVaultId()
        val dao = getActiveDao()
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getCollectionById(it)?.toDomain() }
        val nextRev = (existing?.rev ?: 0L) + 1L

        val col = CollectionItem(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            vaultId = vaultId,
            parentId = parentId,
            name = name.trim().ifEmpty { "Kolekce" },
            icon = icon,
            colorHex = colorHex,
            sortOrder = sortOrder,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
            rev = nextRev
        )
        dao.upsertCollection(col.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "collection",
            objectId = col.id,
            parentId = col.parentId,
            plainJsonPayload = json.encodeToString(col),
            rev = col.rev,
            deletedAtMs = null
        )
        logAuditEvent(
            category = AuditCategory.DATA,
            actionType = if (existing == null) "COLLECTION_CREATED" else "COLLECTION_UPDATED",
            targetObjectId = col.id,
            summary = "Uložena kolekce/složka (rev ${col.rev})"
        )
        return col
    }

    suspend fun softDeleteCollection(collectionId: String) {
        val dao = getActiveDao()
        val existing = dao.getCollectionById(collectionId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(deletedAt = now, updatedAt = now, rev = existing.rev + 1L)
        dao.upsertCollection(deleted.toEntity())
        syncEngineProvider().enqueueAndSyncObject(
            objectType = "collection",
            objectId = deleted.id,
            parentId = deleted.parentId,
            plainJsonPayload = json.encodeToString(deleted),
            rev = deleted.rev,
            deletedAtMs = now
        )
    }

    // --- APPEND-ONLY ENCRYPTED AUDIT LOG (ZERO SECRET LEAKAGE) ---
    suspend fun logAuditEvent(
        category: AuditCategory,
        actionType: String,
        targetObjectId: String? = null,
        summary: String
    ): AuditEntry {
        val vaultId = securityGate.getVaultIdOrNull() ?: "uninitialized"
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})"
        val entry = AuditEntry(
            vaultId = vaultId,
            category = category,
            actionType = actionType,
            targetObjectId = targetObjectId,
            summary = summary,
            deviceInfo = deviceInfo,
            createdAt = System.currentTimeMillis(),
            isSynced = false
        )
        val dao = getActiveDao()
        val insertedId = dao.insertAuditEntry(entry.toEntity(securityGate))
        return entry.copy(id = insertedId)
    }

    // --- INTEGRITY CHECK & SECURITY SCORE ---
    suspend fun runVaultIntegrityCheck(
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): SecurityReport {
        val dao = getActiveDao()
        onProgress(10, "Načítání šifrovaných objektů z lokální databáze…")

        val notes = dao.getAllNotesIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val passwordEntities = dao.getAllPasswordsIncludingDeleted().filter { it.deletedAt == null }
        val events = dao.getAllEventsIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val fileEntities = dao.getAllFilesIncludingDeleted().filter { it.deletedAt == null }
        val databases = dao.getAllDatabasesIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val records = dao.getAllRecordsIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val collections = dao.getAllCollectionsIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }

        val corruptedIds = mutableListOf<String>()
        var verifiedCount = 0
        val totalCount = notes.size + passwordEntities.size + events.size + fileEntities.size +
            databases.size + records.size + collections.size

        onProgress(35, "Ověřování GCM autentizačních tagů hesel…")
        val decryptedPasswords = mutableListOf<PasswordItem>()
        for (pwdEntity in passwordEntities) {
            try {
                val unpacked = CryptoEngine.unpackNonceAndCiphertext(pwdEntity.encryptedPassword.hexToByteArray())
                val plain = securityGate.decryptString(unpacked.ciphertext, unpacked.nonce, aad = pwdEntity.id)
                decryptedPasswords.add(pwdEntity.toDomain(securityGate).copy(password = plain))
                verifiedCount++
            } catch (_: Exception) {
                corruptedIds.add(pwdEntity.id)
            }
        }

        onProgress(65, "Ověřování SHA-256 otisků a AES-GCM tagů souborů ve VFS…")
        for (fileEntity in fileEntities) {
            try {
                securityGate.decryptPayload(fileEntity.encryptedMetadata, fileEntity.metadataNonce)
                val localPath = fileEntity.localEncryptedPath
                if (localPath != null && File(localPath).exists()) {
                    val plainBytes = decryptFileBytesInMemory(fileEntity.id)
                    if (plainBytes == null ||
                        !CryptoEngine.computeSha256Hex(plainBytes).equals(fileEntity.sha256Hex, ignoreCase = true)
                    ) {
                        corruptedIds.add(fileEntity.id)
                    } else {
                        verifiedCount++
                    }
                } else {
                    verifiedCount++
                }
            } catch (_: Exception) {
                corruptedIds.add(fileEntity.id)
            }
        }

        verifiedCount += notes.size + events.size + databases.size + records.size + collections.size

        onProgress(85, "Analýza entropie, duplicit a stáří hesel…")
        val now = System.currentTimeMillis()
        val sixMonthsMs = 180L * 24L * 3600L * 1000L
        val weakCount = decryptedPasswords.count { it.strengthScore < 50 }
        val duplicateGroups = decryptedPasswords.groupBy { it.password }.filter { it.value.size > 1 }
        val duplicateCount = duplicateGroups.values.sumOf { it.size }
        val oldOrExpiredCount = decryptedPasswords.count {
            (it.expiresAt != null && it.expiresAt < now) || (now - it.lastChangedAt > sixMonthsMs)
        }

        val snapshot = buildSnapshotObject(
            notes = notes,
            passwords = decryptedPasswords,
            events = events,
            files = fileEntities.map { it.toDomain() },
            databases = databases,
            records = records,
            collections = collections,
            auditEntries = emptyList()
        )
        val integrityHmacHex = securityGate.computeIntegrityHash(
            snapshot.integritySha256Hex.encodeToByteArray()
        ).toHexString()

        var score = 100
        if (corruptedIds.isNotEmpty()) score -= 35
        score -= (weakCount * 6).coerceAtMost(24)
        score -= (duplicateCount * 5).coerceAtMost(20)
        score -= (oldOrExpiredCount * 3).coerceAtMost(12)
        if (!securityGate.isBiometricEnabled()) score -= 5
        score = score.coerceIn(10, 100)

        onProgress(100, "Kontrola integrity dokončena.")
        logAuditEvent(
            category = AuditCategory.SECURITY,
            actionType = "INTEGRITY_CHECK_COMPLETED",
            summary = "Zkontrolováno $totalCount objektů, poškozených: ${corruptedIds.size}, skóre: $score %"
        )

        return SecurityReport(
            overallScore = score,
            totalObjectsChecked = totalCount,
            verifiedObjectsCount = verifiedCount,
            corruptedObjectIds = corruptedIds,
            weakPasswordsCount = weakCount,
            duplicatePasswordsCount = duplicateCount,
            expiredOrOldPasswordsCount = oldOrExpiredCount,
            computedIntegrityHashHex = integrityHmacHex,
            lastVerifiedTimestamp = now
        )
    }

    // --- .OVAULT EXPORT & IMPORT ---
    suspend fun exportVaultToOvaultBytes(
        exportPassword: String,
        kdfIterations: Int = com.openvault.app.security.KdfParameters.DEFAULT_PBKDF2_ITERATIONS
    ): ByteArray {
        val dao = getActiveDao()
        val notes = dao.getAllNotesIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val passwords = dao.getAllPasswordsIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain(securityGate) }
        val events = dao.getAllEventsIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val files = dao.getAllFilesIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val databases = dao.getAllDatabasesIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val records = dao.getAllRecordsIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val collections = dao.getAllCollectionsIncludingDeleted().filter { it.deletedAt == null }.map { it.toDomain() }
        val audits = dao.getAllAuditEntries().map { it.toDomain() }

        val embeddedBlobs = files.mapNotNull { vf ->
            val path = vf.localEncryptedPath ?: return@mapNotNull null
            val f = File(path)
            if (!f.exists()) return@mapNotNull null
            val plainBytes = decryptFileBytesInMemory(vf.id) ?: return@mapNotNull null
            OvaultEmbeddedFileBlob(
                fileId = vf.id,
                encryptedBytesHex = plainBytes.toHexString()
            )
        }

        val snapshot = buildSnapshotObject(
            notes = notes,
            passwords = passwords,
            events = events,
            files = files,
            fileBlobs = embeddedBlobs,
            databases = databases,
            records = records,
            collections = collections,
            auditEntries = audits
        )

        val bytes = OvaultBackupManager.exportToOvaultBytes(
            snapshot = snapshot,
            exportPassword = exportPassword,
            kdfIterations = kdfIterations
        )
        logAuditEvent(
            category = AuditCategory.SECURITY,
            actionType = "OVAULT_EXPORTED",
            summary = "Exportován šifrovaný kontejner .ovault (${bytes.size} bajtů)"
        )
        return bytes
    }

    suspend fun importVaultFromOvaultBytes(
        containerBytes: ByteArray,
        importPassword: String,
        mode: OvaultImportMode = OvaultImportMode.MERGE
    ): OvaultSnapshot {
        val snapshot = OvaultBackupManager.importFromOvaultBytes(containerBytes, importPassword)
        val dao = getActiveDao()
        val activeVaultId = securityGate.requireVaultId()

        if (mode == OvaultImportMode.REPLACE) {
            dao.clearNotes()
            dao.clearPasswords()
            dao.clearEvents()
            dao.clearFiles()
            dao.clearDatabases()
            dao.clearRecords()
            dao.clearCollections()
        }

        settingsRepository.replaceSettings(snapshot.settings)

        for (col in snapshot.collections) {
            dao.upsertCollection(col.copy(vaultId = activeVaultId).toEntity())
        }
        for (note in snapshot.notes) {
            dao.upsertNote(note.copy(vaultId = activeVaultId).toEntity())
        }
        for (pwd in snapshot.passwords) {
            dao.upsertPassword(pwd.copy(vaultId = activeVaultId).toEntity(securityGate))
        }
        for (ev in snapshot.events) {
            dao.upsertEvent(ev.copy(vaultId = activeVaultId).toEntity())
        }
        for (db in snapshot.databases) {
            dao.upsertDatabase(db.copy(vaultId = activeVaultId).toEntity())
        }
        for (rec in snapshot.records) {
            dao.upsertRecord(rec.copy(vaultId = activeVaultId).toEntity())
        }

        val blobsMap = snapshot.fileBlobs.associateBy { it.fileId }
        val blobsDir = File(context.filesDir, "vault_blobs").apply { mkdirs() }
        for (vf in snapshot.files) {
            val embedded = blobsMap[vf.id]
            val localPath = if (embedded != null) {
                val plainBytes = embedded.encryptedBytesHex.hexToByteArray()
                val reEncrypted = securityGate.encryptPayload(plainBytes, aad = vf.id.encodeToByteArray())
                val packed = CryptoEngine.packNonceAndCiphertext(reEncrypted)
                val targetFile = File(blobsDir, "${vf.id}.enc")
                targetFile.writeBytes(packed)
                targetFile.absolutePath
            } else {
                vf.localEncryptedPath
            }
            dao.upsertFile(
                vf.copy(
                    vaultId = activeVaultId,
                    localEncryptedPath = localPath
                ).toEntity(securityGate)
            )
        }

        logAuditEvent(
            category = AuditCategory.SECURITY,
            actionType = "OVAULT_IMPORTED",
            summary = "Importován kontejner .ovault v režimu ${mode.name} (${snapshot.notes.size} poznámek, ${snapshot.passwords.size} hesel)"
        )
        return snapshot
    }

    private suspend fun buildSnapshotObject(
        notes: List<Note>,
        passwords: List<PasswordItem>,
        events: List<CalendarEvent>,
        files: List<VaultFile>,
        fileBlobs: List<OvaultEmbeddedFileBlob> = emptyList(),
        databases: List<UserDatabase>,
        records: List<DatabaseRecord>,
        collections: List<CollectionItem>,
        auditEntries: List<AuditEntry>
    ): OvaultSnapshot {
        val vaultId = securityGate.getVaultIdOrNull() ?: "unknown"
        val settings = settingsRepository.getSettings()
        val preliminary = OvaultSnapshot(
            vaultId = vaultId,
            exportedAt = System.currentTimeMillis(),
            settings = settings,
            notes = notes,
            passwords = passwords,
            events = events,
            files = files,
            fileBlobs = fileBlobs,
            databases = databases,
            records = records,
            collections = collections,
            auditEntries = auditEntries,
            integritySha256Hex = ""
        )
        val canonicalHash = OvaultBackupManager.computeSnapshotCanonicalHashHex(preliminary)
        return preliminary.copy(integritySha256Hex = canonicalHash)
    }
}
