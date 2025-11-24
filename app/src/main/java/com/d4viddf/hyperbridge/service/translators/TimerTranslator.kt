package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.models.HyperIslandData
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.d4viddf.hyperisland_kit.models.TimerInfo

class TimerTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: "Timer"
        val chronometerBase = sbn.notification.`when`

        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", title)

        val now = System.currentTimeMillis()
        val isCountdown = chronometerBase > now
        val timerType = if (isCountdown) -1 else 1

        // --- NEW ACTION EXTRACTION LOGIC ---
        val bridgeActions = extractBridgeActions(sbn)
        val actionKeys = bridgeActions.map { it.action.key }

        builder.setChatInfo(
            title = title,
            timer = TimerInfo(timerType, chronometerBase, now, now),
            pictureKey = picKey,
            actionKeys = actionKeys
        )

        if (isCountdown) {
            builder.setBigIslandCountdown(chronometerBase, picKey)
        } else {
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, PicInfo(1, picKey), TextInfo(title, "Active"))
            )
        }

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