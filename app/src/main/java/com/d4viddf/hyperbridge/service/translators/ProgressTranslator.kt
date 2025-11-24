package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.models.HyperIslandData
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

class ProgressTranslator(context: Context) : BaseTranslator(context) {

    private val FINISH_KEYWORDS = listOf("downloaded", "completed", "finished", "installed", "done", "exitoso")

    fun translate(sbn: StatusBarNotification, title: String, picKey: String): HyperIslandData {
        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", title)
        val extras = sbn.notification.extras

        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val textContent = (extras.getString(Notification.EXTRA_TEXT) ?: "").toString()

        val percent = if (max > 0) (current * 100) / max else 0
        val isTextFinished = FINISH_KEYWORDS.any { textContent.contains(it, ignoreCase = true) }
        val isFinished = percent >= 100 || isTextFinished

        val tickKey = "${picKey}_tick"

        builder.addPicture(resolveIcon(sbn, picKey))

        if (isFinished) {
            val tickPic = HyperPicture(tickKey, context, android.R.drawable.checkbox_on_background)
            builder.addPicture(tickPic)
        }

        // --- NEW ACTION EXTRACTION LOGIC ---
        val bridgeActions = extractBridgeActions(sbn)
        val actionKeys = bridgeActions.map { it.action.key }

        // --- EXPANDED NOTIFICATION ---
        if (isFinished) {
            builder.setChatInfo(
                title = title,
                content = "Download Complete",
                pictureKey = picKey,
                actionKeys = actionKeys
            )
        } else {
            builder.setChatInfo(
                title = title,
                content = if (indeterminate) "Pending..." else "$percent% • $textContent",
                pictureKey = picKey,
                actionKeys = actionKeys
            )
            builder.setProgressBar(percent, "#007AFF")
        }

        // --- BIG ISLAND ---
        if (isFinished) {
            builder.setBigIslandInfo(
                right = ImageTextInfoRight(
                    1,
                    PicInfo(1, tickKey),
                    TextInfo("Finished", title)
                )
            )
            builder.setSmallIslandIcon(tickKey)
        } else {
            if (indeterminate) {
                builder.setBigIslandInfo(
                    left = ImageTextInfoLeft(1, PicInfo(1, picKey), TextInfo("Downloading", "Waiting..."))
                )
                builder.setSmallIslandIcon(picKey)
            } else {
                builder.setBigIslandInfo(
                    left = ImageTextInfoLeft(
                        1,
                        PicInfo(1, picKey),
                        TextInfo("Downloading", "$percent%")
                    )
                )

                builder.setBigIslandProgressCircle(picKey, "", percent, "#007AFF")
                builder.setSmallIslandCircularProgress(picKey, percent, "#007AFF")
            }
        }

        // --- ADD ACTIONS & IMAGES ---
        bridgeActions.forEach { bridgeAction ->
            builder.addAction(bridgeAction.action)
            bridgeAction.actionImage?.let { builder.addPicture(it) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}