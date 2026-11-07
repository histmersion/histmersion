package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{
  AgentDef,
  AgentDefinitions,
  AgentsControllers,
  AgentTrees,
  LlmCall,
  PoolSink,
  Prompts,
  RunGrant,
  RunTokens
}

import com.histmersion.config.{AppConfig, UserPaths}
import com.histmersion.domain.automation.agents.AgentRoster

import scala.jdk.CollectionConverters.*

/** What the CLI runtimes (`claude-code`, `codex`, `opencode`) share: the definitions, the generated trees they launch,
  * the run tokens their steps reach the agent MCP endpoint with, and the plan pools their streams report to. One per
  * server.
  */
final class CliRuntime(
    val app: AppConfig,
    val definitions: AgentDefinitions,
    paths: UserPaths = AgentTrees.NoEnvironment
)(using stores: com.histmersion.util.storage.Stores, resources: com.histmersion.util.storage.Resources, clock: java.time.Clock, val jvmHome: com.histmersion.util.system.JvmHome):
  val tokens: RunTokens = RunTokens()
  val trees: AgentTrees = AgentTrees(app, definitions, paths)

  /** Wired by [[AgentsControllers]] (it needs the DataSource); `var` only for that late attachment. */
  @volatile var pools: Option[PoolSink] = None

  /** `127.x`, `::1`, `localhost`: nobody else can reach the server, so nobody else can spend a plan. */
  def loopback: Boolean = ClaudeCode.Env.isLoopback(app.server.host)

  /** The agent MCP endpoint as a CLI reaches it (one URL; the run token decides the tools). */
  def agentMcpUrl: com.histmersion.domain.automation.agents.GenSettings.AgentMcpUrl = trees.settings(com.histmersion.domain.automation.agents.AgentTrees.SettingsVersion("")).agentMcpUrl
  def quotaUrl: com.histmersion.domain.automation.agents.GenSettings.QuotaUrl = trees.settings(com.histmersion.domain.automation.agents.AgentTrees.SettingsVersion("")).quotaUrl

  /** A token for one step attempt, alive a little longer than the CLI may run. */
  def grant(call: LlmCall[?], agent: Option[AgentDef], timeoutSeconds: com.histmersion.server.agents.CliProcess.RunTimeoutSeconds): Option[RunGrant] =
    agent.map(a => tokens.issue(a, call.scope, com.histmersion.domain.automation.agents.RunTokens.TtlMillis((math.max(1, timeoutSeconds.value).toLong + 60L) * 1000L)))

object CliRuntime:
  /** `roster`: the agent tools the engine's toolbox holds and the contributed roles (what a definition may list and
    * take).
    */

  def apply(app: AppConfig, roster: AgentRoster, paths: UserPaths = AgentTrees.NoEnvironment)(using stores: com.histmersion.util.storage.Stores, resources: com.histmersion.util.storage.Resources, clock: java.time.Clock, jvmHome: com.histmersion.util.system.JvmHome): CliRuntime =
    new CliRuntime(app, AgentDefinitions(Prompts(Prompts.OverrideDir(app.agents.promptsDir.value)), roster), paths)
