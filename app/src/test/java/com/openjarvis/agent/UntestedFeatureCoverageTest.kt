package com.openjarvis.agent

import com.openjarvis.skills.SkillRegistry
import com.openjarvis.ui.CommandListeningPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UntestedFeatureCoverageTest {

    @Test
    fun commandListeningPolicyClassifiesMuteAndUnmuteAcrossEnglishHindiMarathiAndHinglish() {
        val mutePhrases = listOf(
            "shant raho", "शांत रहो", "chup ho jao", "चुप हो जाओ",
            "mic band kara", "ऐकणं बंद कर", "Stop listening", "Mute ho ja"
        )
        mutePhrases.forEach { phrase ->
            assertEquals("active: $phrase", CommandListeningPolicy.Action.MUTE,
                CommandListeningPolicy.classify(phrase, muted = false))
            assertEquals("muted: $phrase", CommandListeningPolicy.Action.IGNORE,
                CommandListeningPolicy.classify(phrase, muted = true))
        }

        val unmutePhrases = listOf(
            "ab suno", "अब बोलो", "आता ऐका", "Awaaz chalu karo",
            "Unmute karo", "पुन्हा ऐका", "माइक चालू करा"
        )
        unmutePhrases.forEach { phrase ->
            assertEquals("active: $phrase", CommandListeningPolicy.Action.UNMUTE,
                CommandListeningPolicy.classify(phrase, muted = false))
            assertEquals("muted: $phrase", CommandListeningPolicy.Action.UNMUTE,
                CommandListeningPolicy.classify(phrase, muted = true))
        }
    }

    @Test
    fun commandListeningPolicyDoesNotMuteOrdinaryConversation() {
        assertEquals(CommandListeningPolicy.Action.EXECUTE_COMMAND,
            CommandListeningPolicy.classify("shant music chalao", muted = false))
        assertEquals(CommandListeningPolicy.Action.EXECUTE_COMMAND,
            CommandListeningPolicy.classify("ab suno kya hua", muted = false))
        assertEquals(CommandListeningPolicy.Action.IGNORE,
            CommandListeningPolicy.classify("YouTube kholo", muted = true))
    }

    @Test
    fun youtubeQueryExtractionStripsOnlyLeadingWakeWordAndSupportsPlayVerb() {
        val spoken = JarvisCommandNormalizer.stripWakeWord(
            "Jarvis, YouTube par Arijit Singh latest song play karo"
        )
        assertEquals("arijit singh latest song", SearchCommandParser.parseYouTubeQuery(spoken))

        val playOnly = JarvisCommandNormalizer.stripWakeWord("Hey Jarvis, YouTube kholo aur lofi play karo")
        assertEquals("lofi", SearchCommandParser.parseYouTubeQuery(playOnly))

        assertEquals("jarvis songs", SearchCommandParser.parseYouTubeQuery("YouTube par Jarvis songs search karo"))
        assertNull(SearchCommandParser.parseYouTubeQuery("Jarvis, YouTube kholo"))
        assertNull(SearchCommandParser.parseYouTubeQuery("YouTube par play karo"))
    }

    @Test
    fun volumeTorchTimerAndAlarmIntentArgumentsAreDeterministic() {
        assertEquals(StepIntent.Volume(true), StepIntentParser.parse("volume badhao"))
        assertEquals(StepIntent.Volume(false), StepIntentParser.parse("आवाज़ कम करो"))
        assertEquals(StepIntent.Torch(true), StepIntentParser.parse("flashlight on"))
        assertEquals(StepIntent.Torch(false), StepIntentParser.parse("टॉर्च बंद करो"))
        assertEquals(StepIntent.Timer(90), StepIntentParser.parse("90 seconds ka timer"))
        assertEquals(StepIntent.Timer(3660), StepIntentParser.parse("1 hour 1 minute timer"))
        assertEquals(StepIntent.Alarm(19, 45), StepIntentParser.parse("shaam 7:45 alarm lagao"))
        assertNull(StepIntentParser.parse("timer sometime"))
        assertNull(StepIntentParser.parse("alarm sometime"))
    }

    @Test
    fun skillRegistryBuildsExecutorRequestsForTimerAlarmTorchAndVolume() {
        assertEquals(SkillRegistry.Request(SkillRegistry.Kind.TIMER, mapOf("seconds" to "120")),
            SkillRegistry.parse("timer for 2 minutes"))
        assertEquals(SkillRegistry.Request(SkillRegistry.Kind.ALARM, mapOf("hour" to "6", "minute" to "30")),
            SkillRegistry.parse("subah 6:30 alarm"))
        assertEquals(SkillRegistry.Request(SkillRegistry.Kind.TORCH, mapOf("enabled" to "false")),
            SkillRegistry.parse("torch off"))
        assertEquals(SkillRegistry.Request(SkillRegistry.Kind.VOLUME, mapOf("direction" to "down")),
            SkillRegistry.parse("volume down"))
    }

    @Test
    fun appLaunchIsVerifiedOnlyWhenLaunchAndForegroundAreBothConfirmed() {
        assertFalse(AgentPlanPolicy.isAppLaunchVerified(launchAccepted = false, foregroundConfirmed = false))
        assertFalse(AgentPlanPolicy.isAppLaunchVerified(launchAccepted = true, foregroundConfirmed = false))
        assertFalse(AgentPlanPolicy.isAppLaunchVerified(launchAccepted = false, foregroundConfirmed = true))
        assertTrue(AgentPlanPolicy.isAppLaunchVerified(launchAccepted = true, foregroundConfirmed = true))
    }

    @Test
    fun whatsappColonMessageBodyCanBePrefilledWithoutSendKeyword() {
        val intent = StepIntentParser.parse("WhatsApp Rahul ko message: main 10 minute mein aa raha hoon")
        assertEquals(
            StepIntent.Message("whatsapp", "rahul", "main 10 minute mein aa raha hoon"),
            intent
        )
    }

    @Test
    fun whatsappExplicitSendTargetRequiresConfirmationAndApprovalIsSeparate() {
        val send = Action(action = Action.CLICK, text = "Send")
        assertTrue(ActionRiskPolicy.requiresConfirmation(send))
        assertEquals(true, ActionRiskPolicy.approvalDecision("yes confirm"))
        assertEquals(false, ActionRiskPolicy.approvalDecision("no cancel"))
        assertNull(ActionRiskPolicy.approvalDecision("maybe"))
    }
}
