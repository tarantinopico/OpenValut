package com.openvault.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteVaultDto(
    @SerialName("id") val id: String,
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("encrypted_settings")
    @Serializable(with = ByteaHexSerializer::class)
    val encryptedSettings: ByteArray? = null,
    @SerialName("settings_nonce")
    @Serializable(with = ByteaHexSerializer::class)
    val settingsNonce: ByteArray? = null,
    @SerialName("integrity_hash")
    @Serializable(with = ByteaHexSerializer::class)
    val integrityHash: ByteArray? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class RemoteVaultObjectDto(
    @SerialName("id") val id: String,
    @SerialName("vault_id") val vaultId: String,
    @SerialName("object_type") val objectType: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("encrypted_payload")
    @Serializable(with = ByteaHexSerializer::class)
    val encryptedPayload: ByteArray,
    @SerialName("nonce")
    @Serializable(with = ByteaHexSerializer::class)
    val nonce: ByteArray,
    @SerialName("rev") val rev: Long = 1L,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class RemoteVaultFileDto(
    @SerialName("id") val id: String,
    @SerialName("vault_id") val vaultId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("encrypted_metadata")
    @Serializable(with = ByteaHexSerializer::class)
    val encryptedMetadata: ByteArray,
    @SerialName("metadata_nonce")
    @Serializable(with = ByteaHexSerializer::class)
    val metadataNonce: ByteArray,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("rev") val rev: Long = 1L,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class RemoteVaultAuditDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("vault_id") val vaultId: String,
    @SerialName("encrypted_entry")
    @Serializable(with = ByteaHexSerializer::class)
    val encryptedEntry: ByteArray,
    @SerialName("entry_nonce")
    @Serializable(with = ByteaHexSerializer::class)
    val entryNonce: ByteArray,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RemoteVaultSyncLogDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("vault_id") val vaultId: String,
    @SerialName("object_id") val objectId: String,
    @SerialName("table_name") val tableName: String,
    @SerialName("action") val action: String, // "upsert" or "delete"
    @SerialName("rev") val rev: Long,
    @SerialName("synced_at") val syncedAt: String? = null
)
