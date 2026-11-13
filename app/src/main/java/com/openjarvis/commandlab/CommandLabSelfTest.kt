package com.openjarvis.commandlab

data class CommandLabTestCase(
    val name: String,
    val command: String,
    val expectedRoute: CommandLabRoute,
    val expectedResult: String? = null
)

data class CommandLabTestResult(
    val case: CommandLabTestCase,
    val actualRoute: CommandLabRoute,
    val parsedIntent: String,
    val actualResult: String,
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
        CommandLabTestCase("Torch on", "torch on", CommandLabRoute.SKILL),
        CommandLabTestCase("Torch off", "flashlight off", CommandLabRoute.SKILL),
        CommandLabTestCase("English timer", "set timer for 5 minutes", CommandLabRoute.SKILL),
        CommandLabTestCase("Hinglish timer", "5 minute ka timer lagao", CommandLabRoute.STEP),
        CommandLabTestCase("English AM alarm", "6 am alarm", CommandLabRoute.STEP),
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

    // EXTRA phone-report cases; the original 40 above remain unchanged.
    val extraCases: List<CommandLabTestCase> = listOf(
        CommandLabTestCase("Extra 1 volume up without torch", "Awaaz badhao", CommandLabRoute.SKILL, "Volume badha diya; torch untouched"),
        CommandLabTestCase("Extra 2 WhatsApp message without banking block", "WhatsApp per Rahul ko message bhejo ki hello", CommandLabRoute.STEP, "Message(whatsapp, Rahul, hello); banking=allowed; send=approval-only"),
        CommandLabTestCase("Extra 3 YouTube play Arijeet", "YouTube per arijeet Singh play karo", CommandLabRoute.SEARCH, "query=arijeet singh; first-result-tap=required; player-verification=required"),
        CommandLabTestCase("Extra 4 play without query clarifies", "play karo", CommandLabRoute.CLARIFY, "Kya play karun?"),
        CommandLabTestCase("Extra 5 global back before navigation", "back screen per jao", CommandLabRoute.STEP, "BACK global action; navigation=not-invoked"),
        CommandLabTestCase("Extra 6 Instagram open then in-app search", "Instagram kholo aur Shahrukh Khan search karo", CommandLabRoute.PIPELINE, "open_app(Instagram) -> in_app_search(Shahrukh Khan); LLM=not-used"),
        CommandLabTestCase("Extra 7 India capital is chat", "India ki capital kya hai", CommandLabRoute.CHAT, "chat; action-loop=not-used"),
        CommandLabTestCase("Extra 8 vague command clarifies safely", "kuch bhi", CommandLabRoute.CLARIFY, "clarify; no-action; torch=untouched")
    )

    fun run(): List<CommandLabTestResult> = (cases + extraCases).map { test ->
        val actual = CommandLabRouterProbe.classify(test.command)
        val safeResult = CommandLogPolicy.redact(actual.result) ?: ""
        val routeMatches = actual.route == test.expectedRoute
        val resultMatches = test.expectedResult == null || safeResult == test.expectedResult
        val passed = routeMatches && resultMatches
        CommandLabTestResult(
            case = test,
            actualRoute = actual.route,
            parsedIntent = CommandLogPolicy.redact(actual.parsedIntent) ?: "",
            actualResult = safeResult,
            passed = passed,
            failReason = when {
                !routeMatches -> "Expected route ${test.expectedRoute.name.lowercase()}, got ${actual.route.name.lowercase()}"
                !resultMatches -> "Expected result '${test.expectedResult}', got '${safeResult}'"
                else -> null
            }
        )
    }
}
