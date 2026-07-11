package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.PersistableBundle
import androidx.annotation.WorkerThread
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.ScreenButtons
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.config.buttons.ConfigConstants
import com.svartifoss.snfell.util.BundleFileSerialization
import timber.log.Timber
import java.io.File

/**
 * Reads the *playing* button config off disk and renders the real action icons for the mini
 * buttons the user has actually configured (in slot order), so the Watch tab's preview shows
 * the same bottom row the watch does - nothing when no slot is set, rather than phantom pills.
 *
 * Only [ButtonInfo] is unpacked to find the configured screen-button slots (cheap); the matched
 * actions are then deserialized just to pull their icon, preferring a slot's single-tap action
 * over its long-press one, matching the watch's `displayedAction = tapAction ?: longAction`.
 * Deliberately mirrors the on-disk format of
 * [DiskButtonConfigStorage][com.svartifoss.snfell.config.buttons.DiskButtonConfigStorage] - the
 * source of truth - rather than reusing it, since that path also loads (and transmits) every
 * other action and needs DI, neither of which the preview wants.
 */
object MiniButtonIconLoader {

    /** Matches DiskButtonConfigStorage's `action_config$suffix`; the preview mirrors the
     *  playing state, so it reads the playing config. */
    private const val PLAYING_CONFIG_FILE = "action_config_playing"

    private const val ICON_RENDER_PX = 64

    @WorkerThread
    fun loadConfiguredIcons(context: Context): List<Bitmap> {
        val bundle = try {
            BundleFileSerialization.readFromFile(File(context.filesDir, PLAYING_CONFIG_FILE))
        } catch (e: Exception) {
            Timber.w(e, "Could not read button config for preview")
            null
        } ?: return emptyList()

        val singleTapActions = HashMap<Int, PersistableBundle>()
        val longPressActions = HashMap<Int, PersistableBundle>()

        try {
            // Bundles unparcel lazily (nested ones stay deferred even after readFromFile's own
            // eager check), so a corrupt config can throw at any get*() here - keep the whole
            // walk guarded rather than just the file read.
            val numButtons = bundle.getInt(ConfigConstants.NUM_BUTTONS, 0)
            for (i in 0 until numButtons) {
                val info = bundle.getPersistableBundle("${ConfigConstants.BUTTON_INFO}.$i")
                        ?.let { ButtonInfo(it) } ?: continue
                if (info.physicalButton || info.buttonCode !in ScreenButtons.ALL_SLOTS) {
                    continue
                }
                val actionBundle = bundle.getPersistableBundle("${ConfigConstants.BUTTON_ACTION}.$i")
                        ?: continue
                when (info.gesture) {
                    GESTURE_SINGLE_TAP -> singleTapActions[info.buttonCode] = actionBundle
                    GESTURE_LONG_TAP -> longPressActions[info.buttonCode] = actionBundle
                }
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not parse button config for preview")
            return emptyList()
        }

        val icons = ArrayList<Bitmap>(ScreenButtons.ALL_SLOTS.size)
        for (slot in ScreenButtons.ALL_SLOTS) {
            val actionBundle = singleTapActions[slot] ?: longPressActions[slot] ?: continue
            val icon = try {
                PhoneAction.deserialize<PhoneAction>(context, actionBundle)?.defaultIcon
            } catch (e: Exception) {
                Timber.w(e, "Could not decode mini-button action icon for preview")
                null
            } ?: continue
            icons.add(renderWhiteIcon(icon))
        }
        return icons
    }

    /** Renders a drawable to a white-tinted bitmap - mini-button icons are always white on the
     *  watch (the pill background carries the color). */
    private fun renderWhiteIcon(drawable: android.graphics.drawable.Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(ICON_RENDER_PX, ICON_RENDER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.mutate()
        drawable.setTint(Color.WHITE)
        drawable.setBounds(0, 0, ICON_RENDER_PX, ICON_RENDER_PX)
        drawable.draw(canvas)
        return bmp
    }
}
