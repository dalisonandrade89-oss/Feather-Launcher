package com.umilauncher.kiss.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "umilauncher_prefs")

/**
 * DataStore (Preferences) em vez de Room: para guardar só um conjunto de
 * chaves de app favoritas não vale a pena pagar o custo de memória e de
 * inicialização do SQLite/Room em um dispositivo com pouca RAM.
 */
class FavoritesStore(private val context: Context) {

    private val favoritesKey = stringSetPreferencesKey("favorite_keys")

    val favoriteKeys: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[favoritesKey] ?: emptySet()
    }

    suspend fun toggle(appKey: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (appKey in current) current - appKey else current + appKey
        }
    }
}
