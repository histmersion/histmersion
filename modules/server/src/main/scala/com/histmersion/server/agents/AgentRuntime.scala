package com.histmersion.server.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** Where an agent definition is run, which decides how it is rendered (docs/research-agent-runtimes.md §2): the three
  * CLI runtimes, the direct API (sttp-ai, the engine's own loop), and the person's own interactive Claude Code session
  * (the plugin the research workspace installs).
  */
enum AgentRuntime(val wire: String):
  case ClaudeCode extends AgentRuntime("claude-code")
  case Codex extends AgentRuntime("codex")
  case Opencode extends AgentRuntime("opencode")
  case Api extends AgentRuntime("api")
  case Interactive extends AgentRuntime("interactive")

  /** How a `{{tool:x}}` placeholder reads in this runtime. */
  def style: ToolStyle = this match
    case ClaudeCode | Interactive => ToolStyle.Claude
    case Opencode                 => ToolStyle.Opencode
    case Codex | Api              => ToolStyle.Bare

object AgentRuntime:
  def parse(s: String): Option[AgentRuntime] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** The runtimes the generator writes a tree for (the API reads the definitions in process). */
  val Generated: List[AgentRuntime] = List(ClaudeCode, Codex, Opencode)
