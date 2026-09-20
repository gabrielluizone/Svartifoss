package com.svartifoss.snfell.view.watchface

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.svartifoss.snfell.R

/**
 * The colours a row tints itself from, resolved once per render by the caller.
 *
 * Passed in rather than looked up here because the accent follows the album art: a row that read
 * it for itself could land a frame behind the card around it. [value] and [accent] are different
 * on purpose - text needs 4.5:1 against the surface, a switch track only 3:1.
 */
internal class EditorRowColors(
        val onSurface: Int,
        val secondary: Int,
        val value: Int,
        val accent: Int,
        val divider: Int)

/**
 * One row of a contextual editor: a title, optionally a description underneath, and either a
 * switch or the current value with a chevron at the end.
 *
 * The Player page draws every setting through this. It replaced a field of chips for the on/off
 * settings and outlined "Title · Value" buttons for the pickers - see [PlayerSlot] for what was
 * wrong with those. The row is a view over an existing Preference and holds no state of its own:
 * the caller reads the stored value, binds it here, and rebuilds the whole card after every change,
 * so the row on screen can never disagree with what was committed.
 *
 * The row itself is the touch target and the accessibility node. The switch and the chevron are
 * pictures of the state, kept out of the focus order, because a switch that was also clickable
 * would make every toggle two stops for a screen reader and two places a tap can land.
 */
internal class EditorSettingRow private constructor(
        val view: View,
        private val colors: EditorRowColors
) {
    private val title: TextView = view.findViewById(R.id.editor_row_title)
    private val value: TextView = view.findViewById(R.id.editor_row_value)
    private val chevron: ImageView = view.findViewById(R.id.editor_row_chevron)
    private val toggle: SwitchMaterial = view.findViewById(R.id.editor_row_switch)
    private val description: TextView = view.findViewById(R.id.editor_row_description)

    /**
     * Binds a multi-way setting: the title, what it is currently set to, and a chevron that says
     * a tap opens a list rather than flipping something.
     *
     * [spokenTitle] is what a screen reader says when it differs from what is drawn: a card headed
     * "Appearance" can show "Shape", but on its own that says nothing to someone who cannot see
     * the heading, so the full preference title travels in the content description instead.
     */
    fun bindChoice(
            key: String,
            titleText: CharSequence,
            valueText: CharSequence,
            descriptionText: CharSequence?,
            spokenTitle: CharSequence = titleText,
            onClick: () -> Unit
    ): EditorSettingRow {
        bindCommon(key, titleText, descriptionText)

        value.text = valueText
        value.setTextColor(colors.value)
        value.visibility = View.VISIBLE
        chevron.visibility = View.VISIBLE
        ImageViewCompat.setImageTintList(chevron, ColorStateList.valueOf(colors.secondary))
        toggle.visibility = View.GONE

        // The value has to be part of what a screen reader says: "Text alignment" alone would not
        // tell anyone what it is currently set to.
        view.contentDescription = listOfNotNull(spokenTitle, valueText, descriptionText)
                .joinToString(". ")
        view.setOnClickListener { onClick() }
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
        return this
    }

    /**
     * Binds a row that opens somewhere else rather than holding a value: a title, the sentence
     * saying where it goes, and a chevron. There is no value to show and no switch, so what a tap
     * does is the only thing the row has to communicate.
     */
    fun bindLink(
            key: String,
            titleText: CharSequence,
            descriptionText: CharSequence?,
            onClick: () -> Unit
    ): EditorSettingRow {
        bindCommon(key, titleText, descriptionText)

        value.visibility = View.GONE
        chevron.visibility = View.VISIBLE
        ImageViewCompat.setImageTintList(chevron, ColorStateList.valueOf(colors.secondary))
        toggle.visibility = View.GONE

        view.contentDescription = listOfNotNull(titleText, descriptionText).joinToString(". ")
        view.setOnClickListener { onClick() }
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
        return this
    }

    /** Binds an on/off setting: the title, an optional description, and the switch. */
    fun bindToggle(
            key: String,
            titleText: CharSequence,
            descriptionText: CharSequence?,
            checked: Boolean,
            onToggle: (Boolean) -> Unit
    ): EditorSettingRow {
        bindCommon(key, titleText, descriptionText)

        value.visibility = View.GONE
        chevron.visibility = View.GONE
        toggle.visibility = View.VISIBLE
        toggle.isChecked = checked
        tintSwitch()

        // No content description: the title and description are read from the children, and the
        // delegate below adds the switch role and its state. Setting one would replace both.
        view.contentDescription = null
        view.setOnClickListener {
            // Read from the widget rather than the captured value, so two quick taps before the
            // card is rebuilt still alternate instead of both writing the same answer.
            val next = !toggle.isChecked
            toggle.isChecked = next
            onToggle(next)
        }
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Switch::class.java.name
                info.isCheckable = true
                info.isChecked = toggle.isChecked
            }
        })
        return this
    }

    private fun bindCommon(key: String, titleText: CharSequence, descriptionText: CharSequence?) {
        // The key is the tag the search pulse looks for, on the row rather than on a child so the
        // whole row is what flashes.
        view.tag = key

        title.text = titleText
        title.setTextColor(colors.onSurface)

        description.text = descriptionText
        description.visibility =
                if (descriptionText.isNullOrBlank()) View.GONE else View.VISIBLE
        description.setTextColor(colors.secondary)
    }

    private fun tintSwitch() {
        val states = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        toggle.thumbTintList = ColorStateList(
                states,
                intArrayOf(
                        colors.divider,
                        colors.accent,
                        ContextCompat.getColor(view.context, R.color.lyra_stone)))
        toggle.trackTintList = ColorStateList(
                states,
                intArrayOf(
                        ColorUtils.setAlphaComponent(colors.divider, 0x60),
                        ColorUtils.setAlphaComponent(colors.accent, 0x80),
                        colors.divider))
        toggle.jumpDrawablesToCurrentState()
    }

    companion object {
        fun inflate(parent: ViewGroup, colors: EditorRowColors): EditorSettingRow =
                EditorSettingRow(
                        LayoutInflater.from(parent.context)
                                .inflate(R.layout.editor_setting_row, parent, false),
                        colors)
    }
}
