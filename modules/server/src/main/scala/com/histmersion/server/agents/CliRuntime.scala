package com.histmersion.server.agents

import com.histmersion.config.AppConfig

import scala.jdk.CollectionConverters.*

/** What the CLI runtimes (`claude-code`, `codex`, `opencode`) share: the definitions, the generated trees they launch,
  * the run tokens their steps reach the agent MCP endpoint with, and the plan pools their streams report to. One per
  * server.
  */
final class CliRuntime(val app: AppConfig, val definitions: AgentDefinitions):
  val tokens: RunTokens = RunTokens()
  val trees: AgentTrees = AgentTrees(app, definitions)

  /** Wired by [[AgentsHandlers]] (it needs the DataSource); `var` only for that late attachment. */
  @volatile var pools: Option[PoolSink] = None

  /** `127.x`, `::1`, `localhost`: nobody else can reach the server, so nobody else can spend a plan. */
  def loopback: Boolean = ClaudeCodeEnv.isLoopback(app.server.host)

  /** The agent MCP endpoint as a CLI reaches it (one URL; the run token decides the tools). */
  def agentMcpUrl: String = trees.settings("").agentMcpUrl
  def quotaUrl: String = trees.settings("").quotaUrl

  /** A token for one step attempt, alive a little longer than the CLI may run. */
  def grant(call: LlmCall[?], agent: Option[AgentDef], timeoutSeconds: Int): Option[RunGrant] =
    agent.map(a => tokens.issue(a, call.scope, (math.max(1, timeoutSeconds).toLong + 60L) * 1000L))

object CliRuntime:
  def apply(app: AppConfig): CliRuntime =
    new CliRuntime(app, AgentDefinitions(Prompts(app.agents.promptsDir)))
