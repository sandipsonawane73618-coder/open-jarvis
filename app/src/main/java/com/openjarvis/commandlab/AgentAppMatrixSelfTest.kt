package com.openjarvis.commandlab

import com.openjarvis.agent.Action

data class AgentAppMatrixResult(
    val appType: String,
    val packageName: String,
    val passed: Boolean,
    val searchAction: Action?,
    val typeAction: Action?,
    val reason: String
)

/**
 * Runs a deterministic, read-only matrix against synthetic accessibility snapshots.
 * The matrix creates proposed Action values only; it never calls Android services,
 * launches apps, taps controls, or types into a real device.
 */
object AgentAppMatrixSelfTest {
    private data class FakeApp(
        val appType: String,
        val packageName: String,
        val visibleText: String,
        val interactiveElements: String
    )

    private val fakeApps = listOf(
        FakeApp("messaging", "com.example.fake.messaging", "Chats\nSearch\nNew message", "Search\nNew message"),
        FakeApp("video", "com.example.fake.video", "Home\nSearch\nLibrary", "Search\nLibrary"),
        FakeApp("music", "com.example.fake.music", "Listen now\nSearch\nLibrary", "Search\nLibrary"),
        FakeApp("browser", "com.example.fake.browser", "Web page\nSearch", "Search\nAddress bar"),
        FakeApp("settings", "com.example.fake.settings", "Settings\nSearch\nNetwork", "Search\nNetwork"),
        FakeApp("notes", "com.example.fake.notes", "Notes\nSearch\nNew note", "Search\nNew note"),
        FakeApp("maps", "com.example.fake.maps", "Map\nSearch\nExplore", "Search\nExplore"),
        FakeApp("shopping", "com.example.fake.shopping", "Shop\nSearch\nCart", "Search\nCart"),
        FakeApp("mail", "com.example.fake.mail", "Inbox\nSearch\nCompose", "Search\nCompose"),
        FakeApp("camera", "com.example.fake.camera", "Camera\nSearch\nGallery", "Search\nGallery")
    )

    fun run(): List<AgentAppMatrixResult> = fakeApps.map(::evaluate)

    private fun evaluate(app: FakeApp): AgentAppMatrixResult {
        val visible = app.visibleText.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        val controls = app.interactiveElements.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        val searchLabel = visible.firstOrNull { it.equals("Search", ignoreCase = true) }
        val interactiveSearch = controls.firstOrNull { it.equals("Search", ignoreCase = true) }
        val search = if (searchLabel != null && interactiveSearch != null) {
            Action(action = Action.CLICK, packageName = app.packageName, text = searchLabel)
        } else null

        // Model the next screen after selecting Search: the focused search input is
        // represented by the same grounded label and is not executed by this test.
        val searchInputAvailable = search != null && controls.any {
            it.equals("Address bar", ignoreCase = true) || it.equals("Search", ignoreCase = true)
        }
        val type = if (searchInputAvailable) {
            Action(
                action = Action.TYPE_TEXT,
                packageName = app.packageName,
                text = searchLabel,
                value = "Jarvis test query"
            )
        } else null

        val passed = search != null && type != null
        val reason = when {
            passed -> "PASS: visible Search target and interactive input are grounded in this fake screen; no real action executed."
            search == null -> "FAIL: Search is not both visible and interactive on this fake screen."
            else -> "FAIL: no grounded search input is available after selecting Search."
        }
        return AgentAppMatrixResult(app.appType, app.packageName, passed, search, type, reason)
    }
}
