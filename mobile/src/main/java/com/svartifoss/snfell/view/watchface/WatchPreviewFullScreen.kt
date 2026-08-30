package com.svartifoss.snfell.view.watchface

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.svartifoss.snfell.R

/**
 * Opens the watch miniature at full-screen size.
 *
 * The miniature is a ~140dp circle docked above a settings list, which is enough to tell that a
 * setting changed and not enough to judge how it looks -- the exact question both the Watch tab
 * and a community theme's detail page exist to answer. This is deliberately the *same*
 * [WatchPreviewView] rather than a scaled bitmap of the small one: it re-renders at the larger
 * size, so text, rings and artwork are drawn at that resolution instead of being magnified.
 *
 * It is a plain [Dialog] rather than a DialogFragment because both callers are ordinary
 * Activity/Fragment screens that already own their own lifecycle, and the only state worth
 * restoring across a rotation is which surface was open -- which the caller re-supplies anyway.
 */
object WatchPreviewFullScreen {

    /**
     * Fraction of the shorter screen edge the face occupies.
     *
     * Not 1.0: a round face touching both edges reads as clipped, and the surfaces that draw to
     * the very edge (the progress ring, the edge-seek arc) need visible background around them to
     * be legible as edge treatments at all.
     */
    private const val FACE_FRACTION = 0.86f

    /**
     * Shows the dialog and hands the caller the enlarged view to configure.
     *
     * [configure] runs before the first frame and receives a view that has been given no content
     * of its own: the caller is responsible for applying either a theme profile or the live
     * preference/now-playing state, exactly as it did for its own small preview. Returns the
     * created [WatchPreviewView] so a caller driving a live ticker can keep feeding it, and null
     * if the Activity is not in a state where a dialog can be shown.
     */
    fun show(
            activity: Activity,
            onDismiss: () -> Unit = {},
            configure: (WatchPreviewView) -> Unit
    ): WatchPreviewView? {
        if (activity.isFinishing || activity.isDestroyed) return null

        val content = activity.layoutInflater
                .inflate(R.layout.dialog_watch_preview_fullscreen, null) as FrameLayout
        val preview = content.findViewById<WatchPreviewView>(R.id.watch_preview_fullscreen)

        val metrics = activity.resources.displayMetrics
        val size = (minOf(metrics.widthPixels, metrics.heightPixels) * FACE_FRACTION).toInt()
        preview.layoutParams = (preview.layoutParams as FrameLayout.LayoutParams).apply {
            width = size
            height = size
        }

        configure(preview)

        val dialog = Dialog(activity, R.style.LyraFullScreenPreviewDialog).apply {
            setContentView(content, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))
            setCanceledOnTouchOutside(true)
        }
        content.setOnClickListener { dialog.dismiss() }
        content.findViewById<TextView>(R.id.watch_preview_fullscreen_hint)
        dialog.setOnDismissListener { onDismiss() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT)
        }
        dialog.show()
        return preview
    }
}
