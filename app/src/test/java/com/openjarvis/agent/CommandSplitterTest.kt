package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSplitterTest {
    // Use the exact same classifier that AgentCore uses for pipeline steps.
    private val can: (String) -> Boolean = { s ->
        CommandRouter.classify(s) != CommandRouter.Route.NONE
    }
    private fun s(t: String) = CommandSplitter.split(t, can)

    @Test
    fun productionClassifierIsSharedWithSplitter() {
        assertEquals(CommandRouter.Route.SKILL_REGISTRY, CommandRouter.classify("open youtube"))
        assertEquals(CommandRouter.Route.STEP_INTENT, CommandRouter.classify("torch on karo"))
        assertEquals(CommandRouter.Route.SEARCH, CommandRouter.classify("google par mausam search karo"))
        assertEquals(CommandRouter.Route.FAST, CommandRouter.classify("back"))
        assertEquals(CommandRouter.Route.NONE, CommandRouter.classify("kal milte hain"))
        assertEquals(CommandRouter.Route.MESSAGE_CONTINUATION, CommandRouter.classify("Amit ko bhi"))
    }

    @Test
    fun strongSequencers() {
        assertEquals(
            listOf("volume badhao", "Pune navigate karo"),
            s("volume badhao phir Pune navigate karo")
        )
        assertEquals(
            listOf("torch on karo", "6 am alarm lagao"),
            s("torch on karo, uske baad 6 am alarm lagao")
        )
    }

    @Test
    fun weakConnectorSplitsOnlyWhenBothSidesAreCommands() {
        assertEquals(
            listOf("alarm 6 baje lagao", "torch on karo"),
            s("alarm 6 baje lagao aur torch on karo")
        )
        assertEquals(
            listOf("Rahul aur Amit ko message bhejo ki hi"),
            s("Rahul aur Amit ko message bhejo ki hi")
        )
    }

    @Test
    fun messageTextIsNeverSplit() {
        assertEquals(
            listOf("Rahul ko message bhejo ki main aur tum phir milenge"),
            s("Rahul ko message bhejo ki main aur tum phir milenge")
        )
    }

    @Test
    fun commaAndWeakConnectorSplitCommandSequenceButNeverMessageBody() {
        assertEquals(
            listOf("torch on karo", "volume badhao", "WhatsApp kholo"),
            s("torch on karo, volume badhao aur WhatsApp kholo")
        )
        assertEquals(
            listOf("Rahul ko message bhejo ki kal aur parso milte hain"),
            s("Rahul ko message bhejo ki kal aur parso milte hain")
        )
    }

    @Test
    fun requestedSixPhraseRegressionCoverage() {
        assertEquals(
            listOf("torch on karo", "volume badhao", "WhatsApp kholo"),
            s("torch on karo, volume badhao aur WhatsApp kholo")
        )
        assertEquals(
            listOf("Open Settings", "go back"),
            s("Open Settings, then go back")
        )
        assertEquals(
            listOf("WhatsApp kholo", "Rahul ko message bhejo ki hello"),
            s("WhatsApp kholo, phir Rahul ko message bhejo ki hello")
        )
        assertEquals(
            listOf("Chrome kholo", "google par mausam search karo"),
            s("Chrome kholo, google par mausam search karo")
        )
    }

    @Test
    fun openedYouTubeContextAllowsUnqualifiedSearchAfterAurAndAndAndPhir() {
        listOf("aur", "and", "phir").forEach { connector ->
            assertEquals(
                listOf("YouTube kholo", "Arijit Singh search karo"),
                s("YouTube kholo $connector Arijit Singh search karo")
            )
        }
    }

    @Test
    fun instagramOpenAndSearchSplitIntoDeterministicSteps() {
        assertEquals(
            listOf("Instagram kholo", "Shahrukh Khan search karo"),
            s("Instagram kholo aur Shahrukh Khan search karo")
        )
    }

    @Test
    fun instagramInAppSearchSplitsOnAurAndAndAndPhir() {
        listOf("aur", "and", "phir").forEach { connector ->
            assertEquals(
                listOf("Instagram kholo", "Shahrukh Khan search karo"),
                s("Instagram kholo $connector Shahrukh Khan search karo")
            )
        }
    }

    @Test
    fun youtubeOpenAndSearchAreSeparateVerifiedPipelineSteps() {
        assertEquals(
            listOf("YouTube kholo", "YouTube par Arijit Singh ke latest songs search karo"),
            s("YouTube kholo, YouTube par Arijit Singh ke latest songs search karo")
        )
    }

    @Test
    fun singleAndEmpty() {
        assertEquals(listOf("torch on karo"), s("torch on karo"))
        assertEquals(emptyList<String>(), s("   "))
    }

    @Test
    fun appLaunchStepsAreRecognizedWithoutSwallowingConversation() {
        assertEquals(
            listOf("open Instagram", "open WhatsApp"),
            s("open Instagram then open WhatsApp")
        )
        assertEquals(
            listOf("open Instagram", "WhatsApp"),
            s("open Instagram and WhatsApp")
        )
        assertEquals(
            listOf("open instagram and tell me what to do dear"),
            s("open instagram and tell me what to do dear")
        )
        assertEquals(StepIntent.AppLaunch("instagram"), StepIntentParser.parse("open insta again"))
        assertEquals(
            listOf("open Instagram", "open WhatsApp"),
            s("open Instagram and then open WhatsApp")
        )
    }

    @Test
    fun messageRecipientContinuationCanSplitForContextCarry() {
        val canWithContinuation: (String) -> Boolean = { value ->
            StepIntentParser.parse(value) != null ||
                Regex("^.+\\s+(?:ko|को|ला)\\s+(?:bhi|also|too|भी|सुद्धा)$", RegexOption.IGNORE_CASE)
                    .matches(value.trim())
        }
        assertEquals(
            listOf("Rahul ko WhatsApp pe hello bhejo", "Amit ko bhi"),
            CommandSplitter.split("Rahul ko WhatsApp pe hello bhejo aur Amit ko bhi", canWithContinuation)
        )
    }
}
