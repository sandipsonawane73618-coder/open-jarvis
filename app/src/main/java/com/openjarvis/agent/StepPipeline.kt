package com.openjarvis.agent

data class StepContext(
    val app: String? = null,
    val contact: String? = null,
    val messageText: String? = null,
    val groupName: String? = null,
    val lastCommand: String? = null,
    val lastResult: String? = null,
    val pendingSteps: List<String> = emptyList()
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
    val skipped: List<String>,
    val finalContext: StepContext = StepContext()
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
    suspend fun run(
        steps: List<String>,
        initialContext: StepContext = StepContext()
    ): RunResult {
        var ctx = initialContext.copy(pendingSteps = steps)
        val traces = ArrayList<StepTrace>()
        for ((i, step) in steps.withIndex()) {
            var handled = false
            val beforeStep = ctx.copy(pendingSteps = steps.drop(i))
            for (handler in handlers) {
                val outcome = handler.handle(step, beforeStep) ?: continue
                val updated = (outcome.ctx ?: beforeStep).copy(
                    lastCommand = step,
                    lastResult = outcome.reply,
                    pendingSteps = if (outcome.ok) steps.drop(i + 1) else steps.drop(i)
                )
                traces.add(StepTrace(i, step, handler.name, outcome.ok, outcome.reply))
                ctx = updated
                if (!outcome.ok) return RunResult(traces, false, steps.drop(i + 1), ctx)
                handled = true
                break
            }
            if (!handled) {
                val reply = "Samajh nahi aaya: $step"
                ctx = beforeStep.copy(lastResult = reply, pendingSteps = steps.drop(i))
                traces.add(StepTrace(i, step, "unhandled", false, reply))
                return RunResult(traces, false, steps.drop(i + 1), ctx)
            }
        }
        return RunResult(traces, true, emptyList(), ctx.copy(pendingSteps = emptyList()))
    }
}
