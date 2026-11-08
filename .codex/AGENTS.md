# Codex-specific Jarvis guidance

- Root `AGENTS.md` is authoritative; this file adds role coordination only.
- Use the project-local `.agents/skills/` skills relevant to the task.
- Use explorer and docs-researcher as read-only evidence gatherers; use reviewer as a fresh-context read-only reviewer.
- The parent agent owns all edits and must independently verify every finding.
- Do not enable autonomous write/commit/push/merge permissions for specialist roles.
- Do not configure external MCP servers, API keys, cloud models, or remote speech services for this project.
- Keep the user's Codex model/provider and sandbox defaults inherited; this project config does not set them.
