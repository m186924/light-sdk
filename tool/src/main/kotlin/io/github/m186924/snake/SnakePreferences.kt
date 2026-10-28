package io.github.m186924.snake

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

data class SavedSnake(val best: Int, val speed: Int, val walls: Boolean)

/** The best score and last-used settings. Everything stays on the phone. */
class SnakePreferences(private val dataStore: DataStore<Preferences>) {

    suspend fun load(): SavedSnake {
        val prefs = dataStore.data.first()
        return SavedSnake(
            best = prefs[BEST] ?: 0,
            speed = (prefs[SPEED] ?: DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED),
            walls = prefs[WALLS] ?: false,
        )
    }

    suspend fun saveBest(score: Int) {
        dataStore.edit { it[BEST] = score }
    }

    suspend fun saveSettings(speed: Int, walls: Boolean) {
        dataStore.edit {
            it[SPEED] = speed
            it[WALLS] = walls
        }
    }

    private companion object {
        val BEST = intPreferencesKey("best")
        val SPEED = intPreferencesKey("speed")
        val WALLS = booleanPreferencesKey("walls")
    }
}
