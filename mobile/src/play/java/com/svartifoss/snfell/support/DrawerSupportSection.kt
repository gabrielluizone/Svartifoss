package com.svartifoss.snfell.support

import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.R
import kotlinx.coroutines.launch

/**
 * Play Store build: the drawer's support section offers a tip through Google Play Billing, in
 * place of the github build's Buy Me a Coffee and Ko-fi links. Google Play's Payments policy
 * forbids an in-app button that leads to any other way of paying, and a tip sold inside a Play
 * listing has to go through Play Billing - see [TipProducts].
 *
 * The inert twin lives in `src/github`; `src/main` (MainActivity) only ever calls [bind].
 * FlavorSupportIsolationTest pins that nothing of this reaches `src/main` or the github build.
 */
object DrawerSupportSection {

    /**
     * Wires the section inside the drawer [header]. The section starts hidden and appears only when
     * Google Play has answered that at least one tip product exists and can be bought here - so a
     * phone without the Play Store, an account Play will not sell to, and a listing whose products
     * are not set up yet all look like an app that never offered the option.
     */
    fun bind(activity: AppCompatActivity, header: View) {
        val section = header.findViewById<View>(R.id.drawer_support_section) ?: return

        // LyraGestureButton sets iconTint=@null so screens that hand-tint an action icon aren't
        // fought by a style default - this row never tints its own icon, so the plain white fill
        // showed through, invisible on the light theme's surface.
        header.findViewById<MaterialButton>(R.id.drawer_tip_button)?.apply {
            iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.lyra_on_surface))
            setOnClickListener { TipDialog.show(activity) }
        }

        section.isVisible = TipJar.offers.value.isNotEmpty()
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TipJar.refresh(activity)
                launch { TipJar.offers.collect { section.isVisible = it.isNotEmpty() } }
                launch { TipJar.events.collect { announce(activity, it) } }
            }
        }
    }

    /** Said by whichever activity is on screen, so it follows the in-app language. */
    private fun announce(activity: AppCompatActivity, event: TipEvent) {
        val message = when (event) {
            TipEvent.Thanks -> R.string.tip_thanks
            TipEvent.Pending -> R.string.tip_pending
            TipEvent.Failed -> R.string.tip_failed
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
