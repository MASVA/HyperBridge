package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.models.HyperIslandData
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

class NavTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: "Maps"
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT) ?: ""
        val subText = sbn.notification.extras.getString(Notification.EXTRA_SUB_TEXT)

        val navTitle = if (!subText.isNullOrEmpty()) subText else title
        val navContent = text

        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", navTitle.toString())

        // --- NEW ACTION EXTRACTION LOGIC ---
        val bridgeActions = extractBridgeActions(sbn)
        val actionKeys = bridgeActions.map { it.action.key }

        builder.setBaseInfo(
            title = navTitle.toString(),
            content = navContent,
            pictureKey = picKey,
            actionKeys = actionKeys
        )

        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                1,
                PicInfo(1, picKey),
                TextInfo(navTitle.toString(), navContent)
            )
        )

        builder.setSmallIslandIcon(picKey)
        builder.addPicture(resolveIcon(sbn, picKey))

        // --- ADD ACTIONS & IMAGES ---
        bridgeActions.forEach { bridgeAction ->
            builder.addAction(bridgeAction.action)
            bridgeAction.actionImage?.let { builder.addPicture(it) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}