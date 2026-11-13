package com.openjarvis.commandlab

data class CommandLabTestCase(
    val name: String,
    val command: String,
    val expectedRoute: CommandLabRoute
)

data class CommandLabTestResult(
    val case: CommandLabTestCase,
    val actualRoute: CommandLabRoute,
    val parsedIntent: String,
    val passed: Boolean,
    val failReason: String?
)

object CommandLabSelfTest {
    val cases: List<CommandLabTestCase> = listOf(
        CommandLabTestCase("English time", "what time is it", CommandLabRoute.SKILL),
        CommandLabTestCase("Hindi time", "time kya hua hai", CommandLabRoute.SKILL),
        CommandLabTestCase("Hinglish date", "aaj ki date kya hai", CommandLabRoute.SKILL),
        CommandLabTestCase("Hindi date", "आज की तारीख क्या है", CommandLabRoute.SKILL),
        CommandLabTestCase("Marathi date", "आजची तारीख सांगा", CommandLabRoute.SKILL),
        CommandLabTestCase("English volume up", "volume up", CommandLabRoute.SKILL),
        CommandLabTestCase("Hinglish volume up", "awaaz badhao", CommandLabRoute.SKILL),
        CommandLabTestCase("Hindi volume down", "आवाज़ कम करो", CommandLabRoute.SKILL),
        CommandLabTestCase("Torch on", "torch on karo", CommandLabRoute.SKILL),
        CommandLabTestCase("Torch off", "flashlight off", CommandLabRoute.SKILL),
        CommandLabTestCase("English timer", "set timer for 5 minutes", CommandLabRoute.SKILL),
        CommandLabTestCase("Hinglish timer", "5 minute ka timer lagao", CommandLabRoute.STEP),
        CommandLabTestCase("Marathi alarm", "सकाळी ६ वाजता अलार्म लावा", CommandLabRoute.STEP),
        CommandLabTestCase("Hindi alarm", "subah 6 baje alarm lagao", CommandLabRoute.SKILL),
        CommandLabTestCase("Open WhatsApp", "open WhatsApp", CommandLabRoute.SKILL),
        CommandLabTestCase("WhatsApp kholo", "WhatsApp kholo", CommandLabRoute.STEP),
        CommandLabTestCase("Instagram kholo", "Instagram kholo", CommandLabRoute.STEP),
        CommandLabTestCase("Chrome kholo", "Chrome kholo", CommandLabRoute.STEP),
        CommandLabTestCase("YouTube kholo", "YouTube kholo", CommandLabRoute.STEP),
        CommandLabTestCase("Hindi WhatsApp prefill", "WhatsApp par Rahul ko message bhejo ki hello", CommandLabRoute.STEP),
        CommandLabTestCase("Hinglish WhatsApp prefill", "Rahul ko WhatsApp pe message bhejo ki main aa raha hoon", CommandLabRoute.STEP),
        CommandLabTestCase("Roman Marathi WhatsApp prefill", "Amit la WhatsApp message dhad: mi yetoy", CommandLabRoute.STEP),
        CommandLabTestCase("WhatsApp message draft", "WhatsApp Rahul ko message bhejo: kal milte hain", CommandLabRoute.STEP),
        CommandLabTestCase("Call contact", "Rahul ko call karo", CommandLabRoute.STEP),
        CommandLabTestCase("English call", "call Amit", CommandLabRoute.STEP),
        CommandLabTestCase("Dial number", "dial 9991234567", CommandLabRoute.STEP),
        CommandLabTestCase("YouTube search", "youtube par srk song search karo", CommandLabRoute.SEARCH),
        CommandLabTestCase("YouTube open and search", "YouTube kholo aur SRK song search karo", CommandLabRoute.SEARCH),
        CommandLabTestCase("Google search", "google par weather search karo", CommandLabRoute.SEARCH),
        CommandLabTestCase("Chrome search", "Chrome par cricket score search karo", CommandLabRoute.SEARCH),
        CommandLabTestCase("English joke", "tell me a joke", CommandLabRoute.CHAT),
        CommandLabTestCase("Hinglish joke", "ek joke sunao", CommandLabRoute.CHAT),
        CommandLabTestCase("Hindi kahani", "kahani sunao", CommandLabRoute.CHAT),
        CommandLabTestCase("English story", "tell me a short story", CommandLabRoute.CHAT),
        CommandLabTestCase("English question", "why is the sky blue?", CommandLabRoute.CHAT),
        CommandLabTestCase("Hindi remember", "yaad rakho ki mera favourite colour blue", CommandLabRoute.STEP),
        CommandLabTestCase("English remember", "remember that I like tea", CommandLabRoute.STEP),
        CommandLabTestCase("YouTube play", "YouTube par Arijit Singh play karo", CommandLabRoute.SEARCH),
        CommandLabTestCase("Multi-step UI task", "Instagram kholo aur Shah Rukh Khan search karo", CommandLabRoute.LOOP),
        CommandLabTestCase("Mute", "mute", CommandLabRoute.SKILL)
    )

    fun run(): List<CommandLabTestResult> = cases.map { test ->
        val actual = CommandLabRouterProbe.classify(test.command)
        val passed = actual.route == test.expectedRoute
        CommandLabTestResult(
            case = test,
            actualRoute = actual.route,
            parsedIntent = CommandLogPolicy.redact(actual.parsedIntent) ?: "",
            passed = passed,
            failReason = if (passed) null else "Expected ${test.expectedRoute.name.lowercase()}, got ${actual.route.name.lowercase()}"
        )
    }
}
