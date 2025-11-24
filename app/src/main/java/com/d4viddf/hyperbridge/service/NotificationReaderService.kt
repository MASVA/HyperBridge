package com.d4viddf.hyperbridge.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.service.translators.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationReaderService : NotificationListenerService() {

    private val TAG = "HyperBridgeService"
    private val ISLAND_CHANNEL_ID = "hyper_bridge_island_channel"
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var allowedPackageSet: Set<String> = emptySet()
    private val activeTranslations = mutableMapOf<String, Int>()

    private lateinit var progressTranslator: ProgressTranslator
    private lateinit var navTranslator: NavTranslator
    private lateinit var timerTranslator: TimerTranslator
    private lateinit var standardTranslator: StandardTranslator

    override fun onCreate() {
        super.onCreate()
        createIslandChannel()
        progressTranslator = ProgressTranslator(this)
        navTranslator = NavTranslator(this)
        timerTranslator = TimerTranslator(this)
        standardTranslator = StandardTranslator(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val preferences = AppPreferences(this)
        serviceScope.launch { preferences.allowedPackagesFlow.collectLatest { allowedPackageSet = it } }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            if (it.packageName == packageName) return
            if (allowedPackageSet.contains(it.packageName)) translateAndPost(it)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            val key = it.key
            if (activeTranslations.containsKey(key)) {
                val hyperId = activeTranslations[key] ?: return
                try { NotificationManagerCompat.from(this).cancel(hyperId) } catch (_: Exception) {}
                activeTranslations.remove(key)
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun translateAndPost(sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: sbn.packageName
            val bridgeId = sbn.key.hashCode()
            val picKey = "pic_${bridgeId}"

            // 1. Detection
            val isNavigation = sbn.notification.category == Notification.CATEGORY_NAVIGATION ||
                    sbn.packageName.contains("maps") || sbn.packageName.contains("waze")

            val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val isIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
            val hasProgress = (progressMax > 0) || isIndeterminate

            val usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
            val chronometerBase = sbn.notification.`when`

            // 2. Translate (Get Data Only)
            val data: HyperIslandData = when {
                isNavigation -> navTranslator.translate(sbn, picKey)
                usesChronometer && chronometerBase > 0 -> timerTranslator.translate(sbn, picKey)
                hasProgress -> progressTranslator.translate(sbn, title, picKey)
                else -> standardTranslator.translate(sbn, picKey)
            }

            // 3. Post using Standard Notification Builder
            val notificationBuilder = NotificationCompat.Builder(this, ISLAND_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addExtras(data.resources) // <--- Magic Bundle

            sbn.notification.contentIntent?.let { notificationBuilder.setContentIntent(it) }

            val notification = notificationBuilder.build()
            notification.extras.putString("miui.focus.param", data.jsonParam) // <--- Magic JSON

            NotificationManagerCompat.from(this).notify(bridgeId, notification)
            activeTranslations[sbn.key] = bridgeId

        } catch (e: Exception) {
            Log.e(TAG, "Bridge Error", e)
        }
    }

    private fun createIslandChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ISLAND_CHANNEL_ID, "Active Islands", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null); channel.enableVibration(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}