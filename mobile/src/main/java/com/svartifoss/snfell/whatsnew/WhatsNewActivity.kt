package com.svartifoss.snfell.whatsnew

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.WatchInfo
import com.svartifoss.snfell.view.LyraAccent
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * The screen [WhatsNewPrompt] opens once after an update: the Play build's counterpart of the
 * sideloaded build's Updates screen, built from the same pieces (the Svartifoss waterfall cover, the
 * status card, the phone and watch versions) with the notes as rows, each with a glyph. It is a
 * greeting rather than a place to do anything, so it has one button.
 *
 * Almost every word on it is a string the Updates screen already has in every locale
 * (`update_whats_new`, `update_state_current`, `update_device_*`); the notes themselves are the
 * `whats_new_item_*` strings, listed with their glyphs by [WhatsNewPrompt.items].
 */
class WhatsNewActivity : AppCompatActivity() {

    private lateinit var watchVersionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whats_new)
        applySystemBarInsets(findViewById(R.id.whats_new_root))

        val accent = LyraAccent.resolve(this)
        // The same soft wash of the runtime accent behind an accent-tinted glyph the Updates screen
        // uses for its status badge.
        val wash = ColorStateList.valueOf((accent and 0x00FFFFFF) or (0x24 shl 24))
        val ink = ColorStateList.valueOf(accent)

        val version = BuildConfig.VERSION_NAME
        findViewById<TextView>(R.id.text_version_line).text = "Svartifoss $version"
        findViewById<View>(R.id.status_badge).backgroundTintList = wash
        findViewById<ImageView>(R.id.status_icon).imageTintList = ink
        findViewById<TextView>(R.id.text_state_sub).text =
                getString(R.string.update_state_current_sub, version)
        findViewById<TextView>(R.id.text_phone_version).text = version

        watchVersionText = findViewById(R.id.text_watch_version)
        watchVersionText.text = getString(R.string.update_watch_version_unknown)
        loadWatchVersion()

        inflateNotes(findViewById(R.id.notes_container), wash, ink)

        findViewById<MaterialButton>(R.id.button_done).apply {
            backgroundTintList = ink
            // The button style's label is always white, which is unreadable on a light accent (a
            // grey or pastel one); the shared decision picks black or white for the fill it sits on.
            setTextColor(if (AdaptiveTextContrast.prefersDarkText(accent)) Color.BLACK else Color.WHITE)
            setOnClickListener { finish() }
        }
    }

    private fun inflateNotes(container: LinearLayout, wash: ColorStateList, ink: ColorStateList) {
        val inflater = LayoutInflater.from(this)
        val density = resources.displayMetrics.density
        val divider = ContextCompat.getColor(this, R.color.lyra_divider)

        WhatsNewPrompt.items(this).forEachIndexed { index, item ->
            if (index > 0) {
                // Starts under the text, not under the glyph: 16dp padding + 38dp badge + 14dp gap.
                container.addView(View(this).apply {
                    setBackgroundColor(divider)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = (68 * density).toInt()
                        marginEnd = (16 * density).toInt()
                    }
                })
            }
            val row = inflater.inflate(R.layout.item_whats_new_row, container, false)
            row.findViewById<View>(R.id.row_badge).backgroundTintList = wash
            row.findViewById<ImageView>(R.id.row_icon).apply {
                setImageResource(item.icon)
                imageTintList = ink
            }
            row.findViewById<TextView>(R.id.row_text).text = item.text
            container.addView(row)
        }
    }

    /** The watch reports its version inside its WatchInfo DataItem (absent on older builds). */
    private fun loadWatchVersion() {
        lifecycleScope.launch {
            val version = try {
                val dataItems = Wearable.getDataClient(this@WhatsNewActivity).dataItems.await()
                try {
                    dataItems
                            .firstOrNull { it.uri.path == CommPaths.DATA_WATCH_INFO }
                            ?.let { WatchInfo.parseFrom(it.data) }
                            ?.takeIf { it.hasAppVersionName() }
                            ?.appVersionName
                } finally {
                    dataItems.release()
                }
            } catch (e: Exception) {
                // No throwable on purpose: a phone with no watch paired is the ordinary case, and
                // Crashlytics should not be fed by it.
                Timber.i("Could not read the watch version: %s", e.message)
                null
            }
            watchVersionText.text = version ?: getString(R.string.update_watch_version_unknown)
        }
    }

    private fun applySystemBarInsets(root: View) {
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
