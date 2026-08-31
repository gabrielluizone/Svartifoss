package com.svartifoss.snfell.common.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GestureDetectorCompat
import com.svartifoss.snfell.common.R
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.ScreenSwipeDirection
import com.svartifoss.snfell.common.ScreenSwipeResolver

class FourWayTouchLayout : FrameLayout,
        GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener {
    private val gestureDetector = GestureDetectorCompat(context, this)

    // One drawable in one full-screen overlay: the pulse follows the touch point and is no
    // longer clipped per-quadrant, so the old four-ImageView/triangle-clip arrangement became
    // pointless complexity.
    private val tapPulse: TapPulseDrawable

    var listener: UserActionListener? = null
    val enabledDoubleTaps = booleanArrayOf(false, false, false, false)
    val enabledLongTaps = booleanArrayOf(false, false, false, false)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        gestureDetector.setOnDoubleTapListener(this)

        val rippleColor = ResourcesCompat.getColor(context.resources, R.color.music_screen_ripple, null)
        tapPulse = TapPulseDrawable(rippleColor)

        post {
            createFullScreenImageView().setImageDrawable(tapPulse)
        }
    }

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            tapPulse.release()
            listener?.onTouchUp()
        }

        return gestureDetector.onTouchEvent(event)
    }


    override fun onDown(e: MotionEvent): Boolean {
        tapPulse.press(e.x, e.y)
        listener?.onTouchDown(e.x, e.y)
        return true
    }

    /** Lets the host screen keep the tap-feedback pulse in sync with a dynamic accent (e.g. the
     *  color extracted from the current album art), instead of a fixed color. */
    fun setTapFeedbackColor(color: Int) {
        tapPulse.accentColor = color
    }

    override fun onShowPress(e: MotionEvent) {
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val quadrant = getQuadrant(e.x.toInt(), e.y.toInt())
        if (enabledDoubleTaps[quadrant]) {
            // This method is only handling single taps while double taps are enabled
            return false
        }

        // Send cancel event to gesture detector to stop it from detecting any further taps
        // as double tap and instead report repeated single taps
        val cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        gestureDetector.onTouchEvent(cancelEvent)
        cancelEvent.recycle()

        listener?.onSingleTap(quadrant)
        return true
    }


    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false

    private fun createFullScreenImageView(): android.widget.ImageView {
        val imageView = android.widget.ImageView(context)

        val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)

        imageView.layoutParams = params
        addView(imageView)
        return imageView
    }

    private fun getQuadrant(x: Int, y: Int): Int {
        val xPercent = x / width.toFloat()
        val yPercent = y / height.toFloat()

        if (x > width / 2) {
            //We are either in top OR Right OR Bottom

            return if (y > height / 2) {
                //We are either in Right OR Bottom
                if (xPercent > yPercent) ScreenQuadrant.RIGHT else ScreenQuadrant.BOTTOM
            } else {
                //We are either in Right OR Top
                if (xPercent > 1 - yPercent) ScreenQuadrant.RIGHT else ScreenQuadrant.TOP
            }
        } else {
            //We are either in left OR Right OR Bottom

            return if (y > height / 2) {
                //We are either in Left OR Bottom
                if (1 - xPercent > yPercent) ScreenQuadrant.LEFT else ScreenQuadrant.BOTTOM
            } else {
                //We are either in Left OR Top
                if (xPercent > yPercent) ScreenQuadrant.TOP else ScreenQuadrant.LEFT
            }
        }
    }

    // Whether a swipe actually *does* anything is entirely down to whether the user assigned a
    // phone action to that direction (same as an unconfigured quadrant tap doing nothing) - this
    // layout itself no longer gates detection behind a separate on/off toggle.
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        when (ScreenSwipeResolver.resolve(velocityX, velocityY, SWIPE_MIN_VELOCITY)) {
            ScreenSwipeDirection.UP -> listener?.onUpwardsSwipe()
            ScreenSwipeDirection.DOWN -> listener?.onDownwardsSwipe()
            ScreenSwipeDirection.LEFT -> listener?.onSwipeLeft()
            null -> return false
        }
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val quadrant = getQuadrant(e.x.toInt(), e.y.toInt())
        if (!enabledDoubleTaps[quadrant]) {
            return false
        }

        listener?.onSingleTap(quadrant)

        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val quadrant = getQuadrant(e.x.toInt(), e.y.toInt())
        if (!enabledDoubleTaps[quadrant]) {
            return false
        }

        listener?.onDoubleTap(quadrant)

        return true
    }

    override fun onLongPress(e: MotionEvent) {
        val quadrant = getQuadrant(e.x.toInt(), e.y.toInt())
        if (!enabledLongTaps[quadrant]) {
            return
        }

        listener?.onLongTap(quadrant)
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    interface UserActionListener {
        fun onUpwardsSwipe()
        fun onDownwardsSwipe()
        fun onSwipeLeft()
        fun onSingleTap(quadrant: Int)
        fun onDoubleTap(quadrant: Int)
        fun onLongTap(quadrant: Int)

        /** Raw touch-down position, fired alongside this layout's own [tapPulse] (which always
         *  draws at this layout's own z-order). A host that layers other opaque content above
         *  this layout - e.g. a full-screen Compose face - can use this to mirror the same
         *  feedback at a higher z-order where it will actually be visible. Default no-op so
         *  existing listeners (e.g. the phone's touch-zone picker) don't need to implement it. */
        fun onTouchDown(x: Float, y: Float) {}

        /** Matches a prior [onTouchDown] - fired on ACTION_UP/ACTION_CANCEL. */
        fun onTouchUp() {}
    }

    companion object {
        // Shared with the watch's center tap zone, which sits on top of this layout and has to
        // re-implement the same fling thresholds for swipes that start on it.
        const val SWIPE_MIN_VELOCITY = 400f
    }
}
