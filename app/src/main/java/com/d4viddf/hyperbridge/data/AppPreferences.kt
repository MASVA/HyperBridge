package com.d4viddf.hyperbridge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 1. Create the DataStore extension
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hyper_bridge_settings")

class AppPreferences(private val context: Context) {

    companion object {
        // We store a Set of Strings (Package Names)
        private val ALLOWED_PACKAGES_KEY = stringSetPreferencesKey("allowed_packages")
    }

    // --- READ (For the UI) ---
    // Returns a Flow that emits the list whenever it changes
    val allowedPackagesFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[ALLOWED_PACKAGES_KEY] ?: emptySet()
        }

    // --- WRITE (For the UI) ---
    suspend fun toggleApp(packageName: String, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[ALLOWED_PACKAGES_KEY] ?: emptySet()
            if (isEnabled) {
                preferences[ALLOWED_PACKAGES_KEY] = currentSet + packageName
            } else {
                preferences[ALLOWED_PACKAGES_KEY] = currentSet - packageName
            }
        }
    }

    // --- SYNC READ (For the Service) ---
    // The service needs to check this strictly at the moment a notification arrives.
    // Since DataStore is async, we can use a helper or collect the flow in the service scope.
    // Ideally, the Service observes the Flow and keeps a local cache.
}