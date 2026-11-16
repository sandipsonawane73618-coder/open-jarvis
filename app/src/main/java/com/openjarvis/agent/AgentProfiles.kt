package com.openjarvis.agent

/**
 * Phone-native specialist profile routing, adapted from the agent profile model in
 * AnaaySampat/jarvis (MIT; see THIRD_PARTY_NOTICES.md).
 *
 * Cloud/PC-only agents are intentionally excluded from execution until a separately
 * tested local capability or authenticated bridge exists.
 */
enum class SpecialistAgent { AUTO, PHONE, RESEARCH, FILES, SCHEDULER, CONTENT }

data class AgentProfile(
    val id: SpecialistAgent,
    val displayName: String,
    val handles: Set<String>,
    val guidance: String
)

object AgentProfiles {
    val all: List<AgentProfile> = listOf(
        AgentProfile(SpecialistAgent.AUTO, "Auto", setOf("auto"), "Choose the safest available local tools for the request."),
        AgentProfile(SpecialistAgent.PHONE, "Phone", setOf("phone", "mobile"), "Handle supported Android phone actions only. Ground UI actions in the current screen and installed apps. Never send messages, place calls, or confirm sensitive actions without explicit approval."),
        AgentProfile(SpecialistAgent.RESEARCH, "Research", setOf("research", "researcher"), "Structure research questions and evaluate only evidence actually available in the current context or through an enabled tool. If web/source retrieval is unavailable, say so; never claim to have read a page you cannot access."),
        AgentProfile(SpecialistAgent.FILES, "Files", setOf("files", "file"), "Work only with files exposed by a supported Android file action and explicitly granted by the user. Never invent file listings or contents; explain when file access is unavailable."),
        AgentProfile(SpecialistAgent.SCHEDULER, "Scheduler", setOf("scheduler", "schedule", "calendar"), "Use only supported Android alarm and timer actions. Confirm ambiguous date/time details and never claim a calendar event or reminder was saved unless a real handler confirms it."),
        AgentProfile(SpecialistAgent.CONTENT, "Content", setOf("content", "writer", "writing"), "Draft messages and documents. Do not send or publish without explicit user approval.")
    )

    fun byId(id: SpecialistAgent): AgentProfile = all.first { it.id == id }

    fun parseMention(command: String): Pair<SpecialistAgent?, String> {
        val atMatch = Regex("""(?:^|\s)@([a-z]+)\b""", RegexOption.IGNORE_CASE).find(command)
        if (atMatch != null) {
            val handle = atMatch.groupValues[1].lowercase()
            val profile = all.firstOrNull { handle in it.handles }
            if (profile != null) {
                val cleaned = (command.removeRange(atMatch.range)).trim().replace(Regex("""\s+"""), " ")
                return (if (cleaned.isBlank()) null else profile.id) to cleaned
            }
        }
        val spoken = Regex("""^\s*(?:ask|tell|use|get)?\s*(?:the\s+)?([a-z]+)\s+agent\b(?:\s+to)?\s*(.*)$""", RegexOption.IGNORE_CASE)
            .matchEntire(command)
        if (spoken != null) {
            val handle = spoken.groupValues[1].lowercase()
            val remainder = spoken.groupValues[2].trim()
            val profile = all.firstOrNull { handle in it.handles }
            if (profile != null && remainder.isNotBlank()) return profile.id to remainder
        }
        return null to command
    }
}
