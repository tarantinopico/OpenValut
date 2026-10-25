package com.openvault.app.data.remote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object SupabaseConfig {
    const val SUPABASE_URL = "https://qoawtepyyldnszfrajrx.supabase.co"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFvYXd0ZXB5eWxkbnN6ZnJhanJ4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI5MjMzMTEsImV4cCI6MjEwODQ5OTMxMX0.t17ffYAfuA1-CeLu3vsLv42X8mNKWxUwYb0Ei_Dlofc"
    const val HEADER_VAULT_ID = "x-vault-id"
    const val STORAGE_BUCKET_FILES = "vault-files"
}

fun interface VaultIdProvider {
    fun getVaultId(): String?
}

data class RealtimeSyncNotification(
    val table: String,
    val eventType: String,
    val timestampMs: Long = System.currentTimeMillis()
)

interface RemoteVaultApi {
    suspend fun upsertVault(vault: RemoteVaultDto): Boolean
    suspend fun fetchVault(vaultId: String): RemoteVaultDto?
    suspend fun upsertVaultObject(obj: RemoteVaultObjectDto): Boolean
    suspend fun fetchVaultObjectsUpdatedSince(vaultId: String, sinceIsoTimestamp: String?): List<RemoteVaultObjectDto>
    suspend fun upsertVaultFileMetadata(fileDto: RemoteVaultFileDto): Boolean
    suspend fun fetchVaultFilesUpdatedSince(vaultId: String, sinceIsoTimestamp: String?): List<RemoteVaultFileDto>
    suspend fun uploadEncryptedFileBlob(vaultId: String, fileId: String, encryptedBytes: ByteArray): Boolean
    suspend fun downloadEncryptedFileBlob(vaultId: String, fileId: String): ByteArray?
    suspend fun insertAuditEntry(entry: RemoteVaultAuditDto): Long?
    suspend fun fetchAuditEntries(vaultId: String): List<RemoteVaultAuditDto>
    suspend fun insertSyncLog(log: RemoteVaultSyncLogDto): Boolean
    suspend fun pingDiagnostics(): Pair<Boolean, String>
}

class SupabaseRemoteDataSource(
    private val vaultIdProvider: VaultIdProvider,
    baseClient: OkHttpClient? = null
) : RemoteVaultApi {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    var lastSentHeaders: Map<String, String> = emptyMap()
        private set

    private val vaultHeaderInterceptor = Interceptor { chain ->
        val original = chain.request()
        val vaultId = vaultIdProvider.getVaultId().orEmpty()
        val builder = original.newBuilder()
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .header(SupabaseConfig.HEADER_VAULT_ID, vaultId)

        val builtRequest = builder.build()
        lastSentHeaders = builtRequest.headers.toMultimap().mapValues { it.value.joinToString(",") }
        chain.proceed(builtRequest)
    }

    private val httpClient: OkHttpClient = (baseClient?.newBuilder() ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS))
        .addInterceptor(vaultHeaderInterceptor)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetStreamMediaType = "application/octet-stream".toMediaType()

    private var realtimeWebSocket: WebSocket? = null
    private val _realtimeEvents = MutableSharedFlow<RealtimeSyncNotification>(extraBufferCapacity = 32)
    val realtimeEvents: SharedFlow<RealtimeSyncNotification> = _realtimeEvents.asSharedFlow()

    override suspend fun upsertVault(vault: RemoteVaultDto): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(vault).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vaults")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    override suspend fun fetchVault(vaultId: String): RemoteVaultDto? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vaults?id=eq.$vaultId&select=*")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string().orEmpty()
                json.decodeFromString<List<RemoteVaultDto>>(raw).firstOrNull()
            }
        }.getOrNull()
    }

    override suspend fun upsertVaultObject(obj: RemoteVaultObjectDto): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(obj).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vault_objects")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()
            val ok = httpClient.newCall(request).execute().use { it.isSuccessful }
            if (ok) {
                insertSyncLog(
                    RemoteVaultSyncLogDto(
                        vaultId = obj.vaultId,
                        objectId = obj.id,
                        tableName = "vault_objects",
                        action = if (obj.deletedAt != null) "delete" else "upsert",
                        rev = obj.rev
                    )
                )
            }
            ok
        }.getOrDefault(false)
    }

    override suspend fun fetchVaultObjectsUpdatedSince(
        vaultId: String,
        sinceIsoTimestamp: String?
    ): List<RemoteVaultObjectDto> = withContext(Dispatchers.IO) {
        runCatching {
            val filter = if (!sinceIsoTimestamp.isNullOrBlank()) {
                "&updated_at=gt.$sinceIsoTimestamp"
            } else {
                ""
            }
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vault_objects?vault_id=eq.$vaultId$filter&order=updated_at.asc")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val raw = response.body?.string().orEmpty()
                json.decodeFromString<List<RemoteVaultObjectDto>>(raw)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun upsertVaultFileMetadata(fileDto: RemoteVaultFileDto): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(fileDto).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vault_files")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(body)
                    .build()
                val ok = httpClient.newCall(request).execute().use { it.isSuccessful }
                if (ok) {
                    insertSyncLog(
                        RemoteVaultSyncLogDto(
                            vaultId = fileDto.vaultId,
                            objectId = fileDto.id,
                            tableName = "vault_files",
                            action = if (fileDto.deletedAt != null) "delete" else "upsert",
                            rev = fileDto.rev
                        )
                    )
                }
                ok
            }.getOrDefault(false)
        }

    override suspend fun fetchVaultFilesUpdatedSince(
        vaultId: String,
        sinceIsoTimestamp: String?
    ): List<RemoteVaultFileDto> = withContext(Dispatchers.IO) {
        runCatching {
            val filter = if (!sinceIsoTimestamp.isNullOrBlank()) {
                "&updated_at=gt.$sinceIsoTimestamp"
            } else {
                ""
            }
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vault_files?vault_id=eq.$vaultId$filter&order=updated_at.asc")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val raw = response.body?.string().orEmpty()
                json.decodeFromString<List<RemoteVaultFileDto>>(raw)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Uploads an encrypted file binary blob to private Storage bucket `vault-files`
     * at path `{vault_id}/{file_id}.enc`.
     */
    override suspend fun uploadEncryptedFileBlob(
        vaultId: String,
        fileId: String,
        encryptedBytes: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val path = "$vaultId/$fileId.enc"
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.STORAGE_BUCKET_FILES}/$path")
                .header("x-upsert", "true")
                .post(encryptedBytes.toRequestBody(octetStreamMediaType))
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    override suspend fun downloadEncryptedFileBlob(
        vaultId: String,
        fileId: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val path = "$vaultId/$fileId.enc"
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.STORAGE_BUCKET_FILES}/$path")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()
            }
        }.getOrNull()
    }

    override suspend fun insertAuditEntry(entry: RemoteVaultAuditDto): Long? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(entry).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vault_audit")
                    .header("Prefer", "return=representation")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val raw = response.body?.string().orEmpty()
                    json.decodeFromString<List<RemoteVaultAuditDto>>(raw).firstOrNull()?.id ?: 1L
                }
            }.getOrNull()
        }

    override suspend fun fetchAuditEntries(vaultId: String): List<RemoteVaultAuditDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vault_audit?vault_id=eq.$vaultId&order=created_at.desc&limit=250")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val raw = response.body?.string().orEmpty()
                    json.decodeFromString<List<RemoteVaultAuditDto>>(raw)
                }
            }.getOrDefault(emptyList())
        }

    override suspend fun insertSyncLog(log: RemoteVaultSyncLogDto): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(log).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vault_sync_log")
                    .header("Prefer", "return=minimal")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    override suspend fun pingDiagnostics(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        runCatching {
            val vaultId = vaultIdProvider.getVaultId().orEmpty()
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/vaults?id=eq.$vaultId&select=id")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startMs
                val sentVaultHeader = lastSentHeaders[SupabaseConfig.HEADER_VAULT_ID].orEmpty()
                if (response.isSuccessful || response.code in 200..499) {
                    true to "Supabase PostgREST OK (HTTP ${response.code}, ${latency} ms, x-vault-id: ${sentVaultHeader.take(8)}…)"
                } else {
                    false to "HTTP ${response.code}: ${response.message}"
                }
            }
        }.getOrElse { e ->
            false to "Offline / Síť nedostupná (${e.javaClass.simpleName})"
        }
    }

    fun startRealtimeSubscription(vaultId: String) {
        stopRealtimeSubscription()
        runCatching {
            val wsUrl = SupabaseConfig.SUPABASE_URL
                .replaceFirst("https://", "wss://") +
                "/realtime/v1/websocket?apikey=${SupabaseConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"
            val request = Request.Builder()
                .url(wsUrl)
                .header(SupabaseConfig.HEADER_VAULT_ID, vaultId)
                .build()
            realtimeWebSocket = httpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val joinPayload = """
                            {
                              "topic": "realtime:public:vault_objects:vault_id=eq.$vaultId",
                              "event": "phx_join",
                              "payload": {},
                              "ref": "1"
                            }
                        """.trimIndent()
                        webSocket.send(joinPayload)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching {
                            val elem = json.parseToJsonElement(text).jsonObject
                            val event = elem["event"]?.jsonPrimitive?.content.orEmpty()
                            if (event in listOf("INSERT", "UPDATE", "DELETE", "postgres_changes")) {
                                _realtimeEvents.tryEmit(
                                    RealtimeSyncNotification(
                                        table = "vault_objects",
                                        eventType = event
                                    )
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    fun stopRealtimeSubscription() {
        runCatching {
            realtimeWebSocket?.close(1000, "Vault locked or realtime disabled")
        }
        realtimeWebSocket = null
    }

    companion object {
        fun formatIsoTimestamp(epochMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(epochMillis))
        }

        fun parseIsoTimestamp(iso: String?): Long {
            if (iso.isNullOrBlank()) return System.currentTimeMillis()
            return runCatching {
                val clean = iso.replace("+00:00", "Z")
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(clean.take(19))?.time ?: System.currentTimeMillis()
            }.getOrDefault(System.currentTimeMillis())
        }
    }
}
