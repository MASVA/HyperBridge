package com.d4viddf.hyperbridge.service.translators

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.models.BridgeAction
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperPicture

abstract class BaseTranslator(protected val context: Context) {

    /**
     * Extracts the main image (Large Icon > Small Icon > App Icon)
     */
    protected fun resolveIcon(sbn: StatusBarNotification, picKey: String): HyperPicture {
        var bitmap: Bitmap? = null
        val largeIcon = sbn.notification.getLargeIcon()

        // 1. Try Large Icon
        if (largeIcon != null) {
            bitmap = loadIconBitmap(largeIcon)
        }

        // 2. Try Small Icon
        if (bitmap == null && sbn.notification.smallIcon != null) {
            bitmap = loadIconBitmap(sbn.notification.smallIcon)
        }

        // 3. Fallback to App Icon
        if (bitmap == null) {
            bitmap = getAppIconBitmap(sbn.packageName)
        }

        return if (bitmap != null) {
            HyperPicture(picKey, bitmap)
        } else {
            HyperPicture(picKey, context, R.drawable.ic_launcher_foreground)
        }
    }

    /**
     * Extracts Actions AND their Icons
     */
    protected fun extractBridgeActions(sbn: StatusBarNotification): List<BridgeAction> {
        val bridgeActions = mutableListOf<BridgeAction>()
        val actions = sbn.notification.actions ?: return emptyList()

        actions.forEachIndexed { index, androidAction ->
            if (!androidAction.title.isNullOrEmpty()) {
                val uniqueKey = "act_${sbn.key.hashCode()}_$index"

                // 1. Extract the Icon Bitmap
                var actionIcon: Icon? = null
                var hyperPic: HyperPicture? = null

                val originalIcon = androidAction.getIcon()

                if (originalIcon != null) {
                    val bitmap = loadIconBitmap(originalIcon)
                    if (bitmap != null) {
                        // We need an Icon object for the HyperAction constructor
                        actionIcon = Icon.createWithBitmap(bitmap)
                        // We also store it as HyperPicture if we need to register it separately
                        hyperPic = HyperPicture("${uniqueKey}_img", bitmap)
                    }
                }

                // 2. Create the Action using the available Icon constructor
                val hyperAction = HyperAction(
                    key = uniqueKey,
                    title = androidAction.title.toString(),
                    icon = actionIcon, // Pass the custom icon here!
                    pendingIntent = androidAction.actionIntent,
                    actionIntentType = 1
                )

                bridgeActions.add(BridgeAction(hyperAction, hyperPic))
            }
        }
        return bridgeActions
    }

    // --- BITMAP UTILS ---

    private fun loadIconBitmap(icon: Icon): Bitmap? {
        return try {
            val drawable = icon.loadDrawable(context) ?: return null
            // FIX: Call extension function correctly
            drawable.toBitmap()
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppIconBitmap(packageName: String): Bitmap? {
        return try {
            context.packageManager.getApplicationIcon(packageName).toBitmap()
        } catch (e: Exception) { null }
    }

    // FIX: Use 'this' or properties directly inside extension function
    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && this.bitmap != null) return this.bitmap

        val width = if (intrinsicWidth > 0) intrinsicWidth else 1
        val height = if (intrinsicHeight > 0) intrinsicHeight else 1

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}