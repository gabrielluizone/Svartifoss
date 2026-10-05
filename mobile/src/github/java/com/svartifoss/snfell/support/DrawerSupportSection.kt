package com.svartifoss.snfell.support

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.R

/**
 * GitHub (sideload) build: the drawer's support section links out to Buy Me a Coffee and Ko-fi.
 *
 * These links are the reason the section is split by flavor at all. Google Play's Payments policy
 * forbids an in-app button that leads to any other way of paying, so the Play build carries neither
 * the links nor the strings and icons that go with them - it has a tip sold through Google Play
 * Billing instead (`src/play`). `src/main` (MainActivity) only ever calls [bind].
 * FlavorSupportIsolationTest pins the split.
 */
object DrawerSupportSection {

    private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/gabrielsvafoss"
    private const val KOFI_URL = "https://ko-fi.com/gabrielsvafoss"

    fun bind(activity: AppCompatActivity, header: View) {
        // LyraGestureButton sets iconTint=@null so screens that hand-tint an action icon (the
        // gesture/action pickers) aren't fought by a style default - but this row never tints its
        // own icon, so ic_buy_me_a_coffee's plain white fill was showing through unmodified,
        // invisible on the light theme's surface. Same trap as the Watch tab's contextual editors.
        header.findViewById<MaterialButton>(R.id.drawer_support_button)?.apply {
            iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.lyra_on_surface))
            setOnClickListener {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BUY_ME_A_COFFEE_URL)))
            }
        }

        // ic_kofi is deliberately left untinted - it's a real, multi-colour brand mark (see its
        // own comment), not a template glyph.
        header.findViewById<View>(R.id.drawer_kofi_button)?.setOnClickListener {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)))
        }
    }
}
