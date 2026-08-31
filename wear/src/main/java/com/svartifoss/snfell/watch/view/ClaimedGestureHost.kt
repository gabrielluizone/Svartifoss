package com.svartifoss.snfell.watch.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Non-consuming observation boundary around player UI that sits above FourWayTouchLayout.
 *
 * The Compose player is itself backed by an internal AndroidComposeView. That child receives
 * streams claimed by pointerInput/clickable modifiers, so an OnTouchListener installed on the
 * outer ComposeView is never called for them. Ordinary Android buttons above the gesture layer
 * have the same effect. Observing dispatch here, after a child has answered the event, mirrors a
 * claimed stream into the shared swipe detector without intercepting it. Taps, long presses and
 * direct manipulation therefore continue to belong to the original child.
 *
 * A DOWN rejected by every child is deliberately not observed: the parent can then dispatch it to
 * the FourWayTouchLayout sibling underneath, which remains the sole detector for that stream.
 */
class ClaimedGestureHost @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val claimedStream = ClaimedTouchStream()

    var touchObserver: ((MotionEvent) -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        val phase = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> ClaimedTouchStream.Phase.DOWN
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ClaimedTouchStream.Phase.END
            else -> ClaimedTouchStream.Phase.CONTINUE
        }
        if (claimedStream.shouldObserve(phase, handled)) {
            touchObserver?.invoke(event)
        }
        return handled
    }
}
