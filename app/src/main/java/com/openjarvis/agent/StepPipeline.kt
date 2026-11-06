package com.openjarvis.agent

data class StepContext(
    val app: String? = null,
    val contact: String? = null,
    val messageText: String? = null
)

data class StepOutcome(val ok: Boolean, val reply: String, val ctx: StepContext? = null)

interface StepHandler {
    val name: String
    /** null = this handler does not own the step; try the next handler. */
    suspend fun handle(step: String, ctx: StepContext): StepOutcome?
}

data class StepTrace(
    val index: Int,
    val step: String,
    val route: String,
    val ok: Boolean,
    val reply: String
)

data class RunResult(
    val traces: List<StepTrace>,
    val allOk: Boolean,
    val skipped: List<String>
) {
    fun failureMessage(totalSteps: Int): String? {
        if (allOk) return null
        val failed = traces.lastOrNull { !it.ok } ?: return "Command pipeline failed."
        val skippedSteps = skipped.mapIndexed { index, text ->
            "${failed.index + index + 2}. $text"
        }
        return buildString {
            append("Step ${failed.index + 1}/$totalSteps failed: ${failed.reply}")
            if (skippedSteps.isNotEmpty()) {
                append(". Skipped steps: ${skippedSteps.joinToString("; ")}")
            }
        }
    }
}

class StepPipeline(private val handlers: List<StepHandler>) {
    suspend fun run(steps: List<String>): RunResult {
        var ctx = StepContext()
        val traces = ArrayList<StepTrace>()
        for ((i, step) in steps.withIndex()) {
            var handled = false
            for (handler in handlers) {
                val outcome = handler.handle(step, ctx) ?: continue
                traces.add(StepTrace(i, step, handler.name, outcome.ok, outcome.reply))
                if (!outcome.ok) return RunResult(traces, false, steps.drop(i + 1))
                ctx = outcome.ctx ?: ctx
                handled = true
                break
            }
            if (!handled) {
                traces.add(StepTrace(i, step, "unhandled", false, "Samajh nahi aaya: $step"))
                return RunResult(traces, false, steps.drop(i + 1))
            }
        }
        return RunResult(traces, true, emptyList())
    }
}
