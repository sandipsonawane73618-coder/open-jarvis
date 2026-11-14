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
    
    private fun assertTorchRejected(
        commands: List<String>,
        gate: (String, String) -> String?
    ) {
        val fakeLlm = AlwaysTorchExampleFakeLlm()
        commands.forEach { command ->
            val modelPlan = fakeLlm.complete(command)
            assertNull("TORCH must be rejected for command: $command", gate(modelPlan.skill, command))
            var torchExecuted = false
            gate(modelPlan.skill, command)?.let { torchExecuted = true }
            assertFalse("Torch must remain off for command: $command", torchExecuted)
        }
    }

    @Test fun mainSkillPromptRejectsTorchExampleForVolumeAndVagueCommands() =
        assertTorchRejected(listOf("Awaaz badhao", "kuch bhi"), LlmSkillRouteSafetyPolicy::acceptedMainPromptSkill)

    @Test fun pipelineSkillPromptRejectsTorchExampleForVolumeAndVagueCommands() =
        assertTorchRejected(listOf("Awaaz badhao", "kuch bhi"), LlmSkillRouteSafetyPolicy::acceptedPipelinePromptSkill)
}

