package com.svartifoss.snfell.common

import kotlin.math.abs

enum class ScreenSwipeDirection { UP, DOWN, LEFT }

/** Shared direction policy for the View gesture layer and the full-screen Compose bridge. */
object ScreenSwipeResolver {
    fun resolve(velocityX: Float, velocityY: Float, minimumVelocity: Float): ScreenSwipeDirection? {
        return if (abs(velocityX) > abs(velocityY)) {
            ScreenSwipeDirection.LEFT.takeIf {
                velocityX < -minimumVelocity
            }
        } else {
            when {
                velocityY < -minimumVelocity -> ScreenSwipeDirection.UP
                velocityY > minimumVelocity -> ScreenSwipeDirection.DOWN
                else -> null
            }
        }
    }
}
