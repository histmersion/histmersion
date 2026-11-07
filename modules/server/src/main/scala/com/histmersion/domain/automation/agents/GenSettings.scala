package com.histmersion.domain.automation.agents

import com.histmersion.server.agents.Codex

import io.circe.syntax.*

/** What the generated trees depend on besides the definitions: where the runtimes reach this server, and which optional
  * parts to write.
  */
final case class GenSettings(
    /** The agent MCP endpoint (`<public-url>/mcp/agent`): one URL, the run token decides the tools. */
    agentMcpUrl: GenSettings.AgentMcpUrl,
    /** The full MCP endpoint (`<public-url>/mcp`) for the interactive flavours; None = leave the MCP connection to the
      * consumer (the checked-in `plugin/`, whose `.mcp.json` `just mcp-json` writes).
      */
    mcpUrl: Option[GenSettings.McpUrl],
    /** Where the quota-guard mod reports and asks (`<public-url>/api/agents/quota/claude`). */
    quotaUrl: GenSettings.QuotaUrl,
    /** The quota-guard mod in the headless Claude Code plugin (docs/research-agent-runtimes.md §3a). */
    claudeMod: GenSettings.ClaudeMod,
    /** The reporting mod in the interactive Claude Code plugin: the person's own sessions report the shared plan's
      * windows (never denies).
      */
    interactiveMod: GenSettings.InteractiveMod,
    /** The plugin version (`0.3.0`, `+<definitions sha>` for a server-generated tree). */
    version: GenSettings.Version,
    /** The Codex home's `tool_output_token_limit` (`agents.providers.codex.tool-output-tokens`); 0 = the model's own.
      */
    codexToolOutputTokens: GenSettings.CodexToolOutputTokens = com.histmersion.domain.automation.agents.GenSettings.CodexToolOutputTokens(0),
    /** The Codex home carries Histmersion's own base prompt ([[AgentGenerator.CodexBaseFile]]) instead of Codex's
      * built-in one (`agents.providers.codex.base-instructions = lean`).
      */
    codexLeanBase: GenSettings.CodexLeanBase = com.histmersion.domain.automation.agents.GenSettings.CodexLeanBase(false)
)

object GenSettings:
  type AgentMcpUrl = AgentMcpUrl.T
  object AgentMcpUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type McpUrl = McpUrl.T
  object McpUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotaUrl = QuotaUrl.T
  object QuotaUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClaudeMod = ClaudeMod.T
  object ClaudeMod extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InteractiveMod = InteractiveMod.T
  object InteractiveMod extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Version = Version.T
  object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CodexToolOutputTokens = CodexToolOutputTokens.T
  object CodexToolOutputTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CodexLeanBase = CodexLeanBase.T
  object CodexLeanBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
