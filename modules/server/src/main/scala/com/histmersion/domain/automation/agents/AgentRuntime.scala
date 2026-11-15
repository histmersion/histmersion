package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.{ClaudeCode, Codex, Opencode}

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** Where an agent definition is run, which decides how it is rendered (docs/research-agent-runtimes.md §2): the three
  * CLI runtimes, the direct API (sttp-ai, the engine's own loop), and the person's own interactive Claude Code session
  * (the plugin the research workspace installs).
  */
enum AgentRuntime(val wire: AgentRuntime.Wire):
  case ClaudeCode extends AgentRuntime(AgentRuntime.Wire("claude-code"))
  case Codex extends AgentRuntime(AgentRuntime.Wire("codex"))
  case Opencode extends AgentRuntime(AgentRuntime.Wire("opencode"))
  case Api extends AgentRuntime(AgentRuntime.Wire("api"))
  case Interactive extends AgentRuntime(AgentRuntime.Wire("interactive"))

  /** How a `{{tool:x}}` placeholder reads in this runtime. */
  def style: ToolStyle = this match
    case ClaudeCode | Interactive => ToolStyle.Claude
    case Opencode                 => ToolStyle.Opencode
    case Codex | Api              => ToolStyle.Bare

object AgentRuntime:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Option[AgentRuntime] =
    values.find(_.wire.value == s.value.trim.toLowerCase(java.util.Locale.ROOT))

  /** The runtimes the generator writes a tree for (the API reads the definitions in process). */
  val Generated: List[AgentRuntime] = List(ClaudeCode, Codex, Opencode)
