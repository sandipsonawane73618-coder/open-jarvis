package com.openjarvis.agent

import com.openjarvis.llm.providers.OllamaSkillPlanParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LlmSkillRouteSafetyPolicyTest {
    private class AlwaysTorchExampleFakeLlm {
        fun complete(command: String) = OllamaSkillPlanParser.parse(
            """{"skill":"TORCH","args":{"enabled":true}}"""
        )
    }

    @Test fun modelTorchExampleCannotExecuteForVolumeOrUnrelatedCommands() {
        val fakeLlm = AlwaysTorchExampleFakeLlm()
        listOf("Awaaz badhao", "kuch bhi").forEach { command ->
            val modelPlan = fakeLlm.complete(command)
            assertNull(
                "TORCH must be rejected for command: $command",
                LlmSkillRouteSafetyPolicy.acceptedSkill(modelPlan.skill, command)
            )
            var torchExecuted = false
            LlmSkillRouteSafetyPolicy.acceptedSkill(modelPlan.skill, command)?.let {
                torchExecuted = true
            }
            assertFalse("Torch must remain off for command: $command", torchExecuted)
        }
    }
}
