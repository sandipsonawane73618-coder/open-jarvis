package com.openjarvis.agent

/**
 * Executes the bounded scroll-until-target loop against fresh accessibility
 * observations. A successful scroll is never treated as success by itself:
 * the target must appear in the next observation.
 */
internal object ScrollUntilTargetRunner {
    data class Result(
        val found: Boolean,
        val scrolls: Int,
        val reason: String
    )

    suspend fun run(
        target: String,
        maxScrolls: Int = ScrollUntilTargetPolicy.DEFAULT_MAX_SCROLLS,
        observe: suspend () -> ScreenObservation,
        scroll: suspend () -> Boolean
    ): Result {
        var scrollCount = 0
        var screen = observe()
        while (true) {
            when (ScrollUntilTargetPolicy.decide(target, screen, scrollCount, maxScrolls)) {
                ScrollUntilTargetPolicy.Decision.FOUND ->
                    return Result(true, scrollCount, "Found '$target' after $scrollCount scroll(s).")
                ScrollUntilTargetPolicy.Decision.INVALID_TARGET ->
                    return Result(false, scrollCount, "Cannot scroll: target text is empty.")
                ScrollUntilTargetPolicy.Decision.EXHAUSTED ->
                    return Result(false, scrollCount, "Target '$target' was not visible after $scrollCount scroll(s).")
                ScrollUntilTargetPolicy.Decision.SCROLL -> {
                    if (!scroll()) {
                        return Result(false, scrollCount + 1, "Could not scroll: no scrollable area accepted the action.")
                    }
                    scrollCount++
                    screen = observe()
                }
            }
        }
    }
}
