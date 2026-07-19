package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.PersistableBundle
import androidx.annotation.WorkerThread
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.ScreenButtons
import com.svartifoss.snfell.common.QuickPanelButtons
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.config.buttons.ConfigConstants
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.util.BundleFileSerialization
import timber.log.Timber
import java.io.File
import kotlin.math.min

internal data class PreviewActionIcon(
        val bitmap: Bitmap,
        val tintable: Boolean,
        val actionKey: String = "",
        val title: String = ""
)

internal data class PreviewButtonIcons(
        val miniButtons: List<PreviewActionIcon>,
        val quadrants: Map<Int, PreviewActionIcon>,
        val quickPanel: Map<Int, PreviewActionIcon>
)

/**
 * Reads the currently active playing/stopped button config off disk and renders the real action
 * icons for both mini buttons and screen quadrants, so the Watch tab's preview shows the same
 * affordances the watch does instead of hardcoded transport glyphs.
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

    private const val ICON_RENDER_PX = 64

    @WorkerThread
    internal fun loadConfiguredIcons(context: Context, playing: Boolean): PreviewButtonIcons {
        val configFile = if (playing) "action_config_playing" else "action_config_stopped"
        val bundle = try {
            BundleFileSerialization.readFromFile(File(context.filesDir, configFile))
        } catch (e: Exception) {
            Timber.w(e, "Could not read button config for preview")
            null
        } ?: return PreviewButtonIcons(emptyList(), emptyMap(), emptyMap())

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
                val isPreviewSlot = info.buttonCode in ScreenButtons.ALL_SLOTS ||
                        info.buttonCode in ScreenQuadrant.LEFT..ScreenQuadrant.BOTTOM ||
                        info.buttonCode in QuickPanelButtons.ALL_SLOTS ||
                        info.buttonCode == QuickPanelButtons.SLOT_LONG
                if (info.physicalButton || !isPreviewSlot) {
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
            return PreviewButtonIcons(emptyList(), emptyMap(), emptyMap())
        }

        fun decode(actionBundle: PersistableBundle): PreviewActionIcon? {
            return try {
                val action = PhoneAction.deserialize<PhoneAction>(context, actionBundle) ?: return null
                val iconTintable = action.iconTintable
                PreviewActionIcon(
                        bitmap = renderIcon(loadActionIcon(context, action), iconTintable),
                        tintable = iconTintable,
                        actionKey = action.javaClass.canonicalName ?: action.javaClass.name,
                        title = action.title
                )
            } catch (e: Exception) {
                Timber.w(e, "Could not decode action icon for preview")
                null
            }
        }

        val icons = ArrayList<PreviewActionIcon>(ScreenButtons.ALL_SLOTS.size)
        for (slot in ScreenButtons.ALL_SLOTS) {
            val actionBundle = singleTapActions[slot] ?: longPressActions[slot] ?: continue
            decode(actionBundle)?.let(icons::add)
        }

        val quadrants = HashMap<Int, PreviewActionIcon>(4)
        for (quadrant in ScreenQuadrant.LEFT..ScreenQuadrant.BOTTOM) {
            val actionBundle = singleTapActions[quadrant] ?: continue
            decode(actionBundle)?.let { quadrants[quadrant] = it }
        }
        val quickPanel = HashMap<Int, PreviewActionIcon>(4)
        for (slot in QuickPanelButtons.ALL_SLOTS + QuickPanelButtons.SLOT_LONG) {
            val actionBundle = singleTapActions[slot] ?: continue
            decode(actionBundle)?.let { quickPanel[slot] = it }
        }
        return PreviewButtonIcons(icons, quadrants, quickPanel)
    }

    /** Mirrors CustomIconStorage's private on-disk mapping without pulling the whole DI graph
     *  into this worker-only preview loader. */
    private fun loadActionIcon(context: Context, action: PhoneAction): android.graphics.drawable.Drawable {
        val customUri = action.customIconUri ?: return action.defaultIcon
        val iconFile = CustomIconStorage.resolveStoredIconFile(context.filesDir, customUri)
        val bitmap = decodeSampledIcon(iconFile) ?: return action.defaultIcon
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun decodeSampledIcon(file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                var sampleSize = 1
                val target = ICON_RENDER_PX * 2
                while (bounds.outWidth / sampleSize > target || bounds.outHeight / sampleSize > target) {
                    sampleSize *= 2
                }
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            }
        } catch (e: OutOfMemoryError) {
            Timber.w(e, "Custom mini-button icon is too large for preview")
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not decode custom mini-button icon for preview")
            null
        }
    }

    /** Renders into a square canvas with center-fit bounds. Template glyphs become white so each
     * destination surface can tint them; gallery/app artwork preserves colour and aspect ratio. */
    private fun renderIcon(
            source: android.graphics.drawable.Drawable,
            tintable: Boolean
    ): Bitmap {
        val bmp = Bitmap.createBitmap(ICON_RENDER_PX, ICON_RENDER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val drawable = source.constantState?.newDrawable()?.mutate() ?: source.mutate()
        if (tintable) drawable.setTint(Color.WHITE)
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_RENDER_PX
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_RENDER_PX
        val scale = min(
                ICON_RENDER_PX.toFloat() / intrinsicWidth,
                ICON_RENDER_PX.toFloat() / intrinsicHeight
        )
        val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
        val left = (ICON_RENDER_PX - width) / 2
        val top = (ICON_RENDER_PX - height) / 2
        drawable.setBounds(left, top, left + width, top + height)
        drawable.draw(canvas)
        return bmp
    }
}
