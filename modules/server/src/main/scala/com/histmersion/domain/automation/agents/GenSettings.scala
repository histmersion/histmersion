package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.Codex

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

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
    codexToolOutputTokens: GenSettings.CodexToolOutputTokens = GenSettings.CodexToolOutputTokens(0),
    /** The Codex home carries Histmersion's own base prompt ([[AgentGenerator.CodexBaseFile]]) instead of Codex's
      * built-in one (`agents.providers.codex.base-instructions = lean`).
      */
    codexLeanBase: GenSettings.LeanBase = GenSettings.LeanBase.Full
)

object GenSettings:
  type AgentMcpUrl = AgentMcpUrl.T
  object AgentMcpUrl extends Newtype[String, Pure]
  type McpUrl = McpUrl.T
  object McpUrl extends Newtype[String, Pure]
  type QuotaUrl = QuotaUrl.T
  object QuotaUrl extends Newtype[String, Pure]
  enum ClaudeMod derives CanEqual:
    case On, Off
  object ClaudeMod extends TwoCases[ClaudeMod]:
    extension (claudeMod: ClaudeMod) def claudeMod: Boolean = claudeMod == On
  enum InteractiveMod derives CanEqual:
    case On, Off
  object InteractiveMod extends TwoCases[InteractiveMod]:
    extension (interactiveMod: InteractiveMod) def interactiveMod: Boolean = interactiveMod == On
  type Version = Version.T
  object Version extends Newtype[String, Pure]
  type CodexToolOutputTokens = CodexToolOutputTokens.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object CodexToolOutputTokens extends Newtype[Int, Pure]
  enum LeanBase derives CanEqual:
    case Lean, Full
  object LeanBase extends TwoCases[LeanBase]:
    extension (codexLeanBase: LeanBase) def codexLeanBase: Boolean = codexLeanBase == Lean
