package com.d4viddf.hyperbridge.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.data.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Simple data class for the UI
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Bitmap,
    val isBridged: Boolean = false
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManager = application.packageManager
    private val preferences = AppPreferences(application)

    // Raw list of installed apps
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    // Search query
    val searchQuery = MutableStateFlow("")

    // FINAL STATE: Combines installed apps + User Preferences + Search Query
    val uiState: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        preferences.allowedPackagesFlow,
        searchQuery
    ) { apps, allowedSet, query ->
        apps.map { app ->
            app.copy(isBridged = allowedSet.contains(app.packageName))
        }.filter {
            it.name.contains(query, ignoreCase = true)
        }.sortedBy { !it.isBridged } // Show active apps at the top
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = getLaunchableApps()
        }
    }

    fun toggleApp(packageName: String, isEnabled: Boolean) {
        viewModelScope.launch {
            preferences.toggleApp(packageName, isEnabled)
        }
    }

    // Heavy scanning logic moved to IO thread
    private suspend fun getLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Query for all apps that have a launcher icon
        // This automatically filters out background services/system junk
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)

        resolveInfos.mapNotNull { resolveInfo ->
            try {
                val activityInfo = resolveInfo.activityInfo
                val name = resolveInfo.loadLabel(packageManager).toString()
                val pkg = activityInfo.packageName
                val icon = resolveInfo.loadIcon(packageManager).toBitmap()

                AppInfo(name, pkg, icon)
            } catch (e: Exception) {
                null
            }
        }.distinctBy { it.packageName } // Remove duplicates
    }

    // Helper: Drawable -> Bitmap
    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable) return this.bitmap
        val bitmap = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}