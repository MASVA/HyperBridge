package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.models.HyperIslandData
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

class StandardTranslator(context: Context) : BaseTranslator(context) {

    fun translate(sbn: StatusBarNotification, picKey: String): HyperIslandData {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: sbn.packageName
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""

        // Detect Media (Spotify, YouTube, Apple Music)
        val isMedia = template.contains("MediaStyle")

        val builder = HyperIslandNotification.Builder(context, "bridge_${sbn.packageName}", title)

        // 1. Extract Resources
        val bridgeActions = extractBridgeActions(sbn)
        val actionKeys = bridgeActions.map { it.action.key }

        // 2. Register Main Picture
        // This must happen before building the JSON
        builder.addPicture(resolveIcon(sbn, picKey))

        // 3. Configure Expanded Notification (Shade)
        builder.setBaseInfo(
            title = title,
            content = if (isMedia) "Now Playing" else text,
            pictureKey = picKey, // This makes the icon visible in the shade
            actionKeys = actionKeys
        )

        // 4. Configure Big Island (Popup)
        if (isMedia) {
            // MEDIA MODE: Show ONLY the Icon (Album Art)
            // We pass empty text to hide the text labels, making the icon dominant
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    1,
                    PicInfo(1, picKey),
                    TextInfo(title = "", content = "") // Hide text
                )
            )
        } else {
            // STANDARD MODE: Icon Left | Text Right
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    1,
                    PicInfo(1, picKey),
                    TextInfo(title = title, content = text)
                )
            )
        }

        // 5. Small Island (Pill)
        builder.setSmallIslandIcon(picKey)

        // 6. Add Actions & Action Icons
        bridgeActions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { iconPic -> builder.addPicture(iconPic) }
        }

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}