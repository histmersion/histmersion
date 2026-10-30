package com.histmersion.server.agents

import io.circe.syntax.*

/** What the generated trees depend on besides the definitions: where the runtimes reach this server, and which optional
  * parts to write.
  */
final case class GenSettings(
    /** The agent MCP endpoint (`<public-url>/mcp/agent`): one URL, the run token decides the tools. */
    agentMcpUrl: String,
    /** The full MCP endpoint (`<public-url>/mcp`) for the interactive flavours; None = leave the MCP connection to the
      * consumer (the checked-in `plugin/`, whose `.mcp.json` `just mcp-json` writes).
      */
    mcpUrl: Option[String],
    /** Where the quota-guard mod reports and asks (`<public-url>/api/agents/quota/claude`). */
    quotaUrl: String,
    /** The quota-guard mod in the headless Claude Code plugin (docs/research-agent-runtimes.md §3a). */
    claudeMod: Boolean,
    /** The reporting mod in the interactive Claude Code plugin: the person's own sessions report the shared plan's
      * windows (never denies).
      */
    interactiveMod: Boolean,
    /** The plugin version (`0.3.0`, `+<definitions sha>` for a server-generated tree). */
    version: String,
    /** The Codex home's `tool_output_token_limit` (`agents.providers.codex.tool-output-tokens`); 0 = the model's own.
      */
    codexToolOutputTokens: Int = 0,
    /** The Codex home carries Histmersion's own base prompt ([[AgentGenerator.CodexBaseFile]]) instead of Codex's
      * built-in one (`agents.providers.codex.base-instructions = lean`).
      */
    codexLeanBase: Boolean = false
)
