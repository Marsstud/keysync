package com.devoid.keysync.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.devoid.keysync.model.AppConfig
import com.devoid.keysync.model.DisplayContext
import com.devoid.keysync.model.DraggableItem
import com.devoid.keysync.model.toAbsolute
import com.devoid.keysync.model.toRelative
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

val Context.datastore by preferencesDataStore("app_configurations")

class DataStoreManager @Inject constructor(private val context: Context) {

    companion object {
        fun getButtonsConfigKey(packageName: String): Preferences.Key<String> =
            stringPreferencesKey("buttons_config_$packageName")

        val ADDED_PACKAGES = stringPreferencesKey("added_packages")
        val POINTER_SENSITIVITY = floatPreferencesKey("pointer_sensitivity")
        val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        val KEYS_CONFIG = stringPreferencesKey("keys_config")
        val DATA_SCHEMA_VERSION = intPreferencesKey("data_schema_version")

        const val CURRENT_SCHEMA_VERSION = 1
    }
    
    fun getButtonsConfigKeys(): Flow<List<Preferences.Key<String>>> {
       return context.datastore.data.map { it.asMap().keys.map { it as Preferences.Key<String> }.filter { prefKey->prefKey.name.startsWith("buttons_config") } }
    }

    suspend fun remove(key: Preferences.Key<String>) {
        context.datastore.edit { pref ->
            pref.remove(key)
        }
    }

    suspend fun save(key: Preferences.Key<String>, value: List<DraggableItem>) {
        val json = Json.encodeToString(value)
        context.datastore.edit { pref ->
            pref[key] = json
        }
    }

    fun getButtons(key: Preferences.Key<String>): Flow<List<DraggableItem>> {
        return context.datastore.data.map { pref ->
            val json = pref[key] ?: return@map emptyList()
            Json.decodeFromString(json)
        }
    }

    suspend fun saveList(key: Preferences.Key<String>, value: List<String>) {
        val json = Json.encodeToString(value)
        context.datastore.edit { pref ->
            pref[key] = json
        }
    }

    fun getList(key: Preferences.Key<String>): Flow<List<String>> {
        return context.datastore.data.map { pref ->
            val json = pref[key] ?: return@map emptyList()
            Json.decodeFromString(json)
        }
    }

    suspend fun save(key: Preferences.Key<String>, value: AppConfig) {
        val json = Json.encodeToString(value)
        context.datastore.edit { pref ->
            pref[key] = json
        }
    }

    fun getKeyConfig(key: Preferences.Key<String>): Flow<AppConfig> {
        return context.datastore.data.map { pref ->
            val json = pref[key] ?: return@map AppConfig.Default
            Json.decodeFromString(json)
        }
    }

    suspend fun save(key: Preferences.Key<String>, value: String) {
        context.datastore.edit { pref ->
            pref[key] = value
        }
    }


    fun getString(key: Preferences.Key<String>): Flow<String?> {
        return context.datastore.data.map { pref ->
            pref[key]
        }
    }

    suspend fun save(key: Preferences.Key<Int>, value: Int) {
        context.datastore.edit { pref ->
            pref[key] = value
        }
    }

    fun getInt(key: Preferences.Key<Int>): Flow<Int?> {
        return context.datastore.data.map { pref ->
            pref[key]
        }
    }

    suspend fun save(key: Preferences.Key<Float>, value: Float) {
        context.datastore.edit { pref ->
            pref[key] = value
        }
    }

    fun getFloat(key: Preferences.Key<Float>): Flow<Float?> {
        return context.datastore.data.map { pref ->
            pref[key]
        }
    }

    suspend fun migrateSchemaIfNeeded(displayContext: DisplayContext) {
        val currentVersion = context.datastore.data.first()[DATA_SCHEMA_VERSION] ?: 0
        if (currentVersion >= CURRENT_SCHEMA_VERSION) return

        val buttonKeys = getButtonsConfigKeys().first()
        for (key in buttonKeys) {
            val json = context.datastore.data.first()[key] ?: continue
            val items = Json.decodeFromString<List<DraggableItem>>(json)
            if (items.isEmpty()) continue
            // If the first item has position x > 1, it's in absolute pixels — convert
            if (items.first().position.x > 1f) {
                val relativeItems = items.toRelative(displayContext)
                context.datastore.edit { pref ->
                    pref[key] = Json.encodeToString(relativeItems)
                }
            }
        }

        context.datastore.edit { pref ->
            pref[DATA_SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
        }
    }

    suspend fun saveButtons(key: Preferences.Key<String>, items: List<DraggableItem>, displayContext: DisplayContext) {
        val relativeItems = items.toRelative(displayContext)
        val json = Json.encodeToString(relativeItems)
        context.datastore.edit { pref ->
            pref[key] = json
        }
    }

    fun getButtonsWithContext(key: Preferences.Key<String>, displayContext: DisplayContext): Flow<List<DraggableItem>> {
        return context.datastore.data.map { pref ->
            val json = pref[key] ?: return@map emptyList()
            val items = Json.decodeFromString<List<DraggableItem>>(json)
            items.toAbsolute(displayContext)
        }
    }


}