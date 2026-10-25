package com.openvault.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openvault.app.domain.model.VaultSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "openvault_ui_preferences"
)

class SettingsRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsJsonKey = stringPreferencesKey("vault_settings_v2_json")

    val settingsFlow: Flow<VaultSettings> = context.vaultDataStore.data.map { prefs ->
        val raw = prefs[settingsJsonKey]
        if (raw.isNullOrBlank()) {
            VaultSettings()
        } else {
            runCatching { json.decodeFromString<VaultSettings>(raw) }.getOrDefault(VaultSettings())
        }
    }

    suspend fun getSettings(): VaultSettings = settingsFlow.first()

    suspend fun updateSettings(transform: (VaultSettings) -> VaultSettings): VaultSettings {
        var updated = VaultSettings()
        context.vaultDataStore.edit { prefs ->
            val currentRaw = prefs[settingsJsonKey]
            val current = if (currentRaw.isNullOrBlank()) {
                VaultSettings()
            } else {
                runCatching { json.decodeFromString<VaultSettings>(currentRaw) }.getOrDefault(VaultSettings())
            }
            updated = transform(current)
            prefs[settingsJsonKey] = json.encodeToString(updated)
        }
        return updated
    }

    suspend fun replaceSettings(newSettings: VaultSettings) {
        context.vaultDataStore.edit { prefs ->
            prefs[settingsJsonKey] = json.encodeToString(newSettings)
        }
    }
}
