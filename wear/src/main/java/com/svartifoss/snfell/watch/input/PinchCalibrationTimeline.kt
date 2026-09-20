package com.svartifoss.snfell.watch.input

/**
 * The guided calibration sequence as a pure function of elapsed time.
 *
 * One answer read by two consumers: the sensor callback, which files each sample under the step
 * it belongs to, and the screen, which shows that same step. They used to be two separate
 * computations of the same windows - one in the sample callback and one in the UI tick - that
 * only agreed because both had been written with the same constants.
 *
 * Nothing here knows about Android, so it is pinned by a plain JVM test.
 */
internal object PinchCalibrationTimeline {
    enum class Kind { COUNTDOWN, REST, PINCH, WAIT, PREPARE_MOVE, MOVE, DONE }

    data class Step(
            val kind: Kind,
            /** 1-based attempt for [Kind.PINCH] and the [Kind.WAIT] after it; 0 otherwise. */
            val attempt: Int,
            /** How far through this step, 0..1. */
            val stepProgress: Float,
            /** How far through the whole sequence, 0..1. */
            val overallProgress: Float,
            /** Whole seconds left in this step, rounded up so a step never reads "0" while on. */
            val secondsLeft: Int
    )

    const val ATTEMPTS = 6
    const val COUNTDOWN_MS = 3_000L
    const val REST_MS = 4_000L
    const val PINCH_MS = 3_000L
    const val WAIT_MS = 2_000L
    const val PREPARE_MOVE_MS = 2_000L
    const val MOVE_MS = 6_000L

    private const val REST_START = COUNTDOWN_MS
    private const val ATTEMPTS_START = REST_START + REST_MS
    private const val ATTEMPT_MS = PINCH_MS + WAIT_MS
    private const val PREPARE_START = ATTEMPTS_START + ATTEMPTS * ATTEMPT_MS
    private const val MOVE_START = PREPARE_START + PREPARE_MOVE_MS
    const val TOTAL_MS = MOVE_START + MOVE_MS

    fun stepAt(elapsedMs: Long): Step {
        val overall = (elapsedMs.toFloat() / TOTAL_MS).coerceIn(0f, 1f)
        return when {
            elapsedMs < REST_START -> step(Kind.COUNTDOWN, 0, elapsedMs, COUNTDOWN_MS, overall)
            elapsedMs < ATTEMPTS_START ->
                step(Kind.REST, 0, elapsedMs - REST_START, REST_MS, overall)
            elapsedMs < PREPARE_START -> {
                val offset = elapsedMs - ATTEMPTS_START
                val attempt = (offset / ATTEMPT_MS).toInt() + 1
                val within = offset % ATTEMPT_MS
                if (within < PINCH_MS) {
                    step(Kind.PINCH, attempt, within, PINCH_MS, overall)
                } else {
                    step(Kind.WAIT, attempt, within - PINCH_MS, WAIT_MS, overall)
                }
            }
            elapsedMs < MOVE_START ->
                step(Kind.PREPARE_MOVE, 0, elapsedMs - PREPARE_START, PREPARE_MOVE_MS, overall)
            elapsedMs < TOTAL_MS -> step(Kind.MOVE, 0, elapsedMs - MOVE_START, MOVE_MS, overall)
            else -> Step(Kind.DONE, 0, 1f, 1f, 0)
        }
    }

    private fun step(kind: Kind, attempt: Int, inStepMs: Long, durationMs: Long, overall: Float) =
            Step(
                    kind = kind,
                    attempt = attempt,
                    stepProgress = (inStepMs.toFloat() / durationMs).coerceIn(0f, 1f),
                    overallProgress = overall,
                    secondsLeft = ((durationMs - inStepMs + 999) / 1000).toInt().coerceAtLeast(1))
}
