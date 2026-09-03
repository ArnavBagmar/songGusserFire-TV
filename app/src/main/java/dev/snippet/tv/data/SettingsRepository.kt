package dev.snippet.tv.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class KeyboardLayoutOption(val displayName: String) {
    ALPHABETICAL("Alphabetical"),
    QWERTY("QWERTY"),
}

data class AppSettings(
    val keyboardLayout: KeyboardLayoutOption = KeyboardLayoutOption.ALPHABETICAL,
    val autocompleteEnabled: Boolean = true,
    val volumePercent: Int = DEFAULT_VOLUME_PERCENT,
) {
    companion object {
        const val DEFAULT_VOLUME_PERCENT = 100
        const val VOLUME_STEP = 10
    }
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val layoutKey = stringPreferencesKey("keyboard_layout")
    private val autocompleteKey = booleanPreferencesKey("autocomplete_enabled")
    private val volumeKey = intPreferencesKey("volume_percent")

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "Settings store unreadable; using defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            AppSettings(
                keyboardLayout = KeyboardLayoutOption.entries
                    .find { it.name == prefs[layoutKey] } ?: KeyboardLayoutOption.ALPHABETICAL,
                autocompleteEnabled = prefs[autocompleteKey] ?: true,
                volumePercent = (prefs[volumeKey] ?: AppSettings.DEFAULT_VOLUME_PERCENT)
                    .coerceIn(0, 100),
            )
        }

    suspend fun setKeyboardLayout(layout: KeyboardLayoutOption) {
        dataStore.edit { prefs -> prefs[layoutKey] = layout.name }
    }

    suspend fun setAutocompleteEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[autocompleteKey] = enabled }
    }

    suspend fun setVolumePercent(percent: Int) {
        dataStore.edit { prefs -> prefs[volumeKey] = percent.coerceIn(0, 100) }
    }

    companion object {
        private const val TAG = "SettingsRepository"
    }
}
