package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.ClaudeCodeConfig
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

/** The pure half of the `claude-code` provider: the argument vector, the environment, the MCP config, and the
  * `stream-json` output read back into sttp-ai's result types.
  */
object ClaudeCode:
  type Argument = Argument.T
  object Argument extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModelId = ModelId.T
  object ModelId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolId = ToolId.T
  object ToolId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolName = ToolName.T
  object ToolName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The MCP server's name as the CLI sees it; its tools are `mcp__histmersion__<tool>`. */
  type ParseMcpProblem = ParseMcpProblem.T
  object ParseMcpProblem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseFirstCall = ParseFirstCall.T
  object ParseFirstCall extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type McpConfigToken = McpConfigToken.T
  object McpConfigToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchemaJsonResult = SchemaJsonResult.T
  object SchemaJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvMaxTurns = ArgvMaxTurns.T
  object ArgvMaxTurns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvSchema = ArgvSchema.T
  object ArgvSchema extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvMcpFile = ArgvMcpFile.T
  object ArgvMcpFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvOffered = ArgvOffered.T
  object ArgvOffered extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvAll = ArgvAll.T
  object ArgvAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvWeb = ArgvWeb.T
  object ArgvWeb extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentMaxTokens = EnvironmentMaxTokens.T
  object EnvironmentMaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentToken = EnvironmentToken.T
  object EnvironmentToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProbeEnvironmentMaxTokens = ProbeEnvironmentMaxTokens.T
  object ProbeEnvironmentMaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchResult = WatchResult.T
  object WatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchLine = WatchLine.T
  object WatchLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensResult = TokensResult.T
  object TokensResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FirstCallTokensResult = FirstCallTokensResult.T
  object FirstCallTokensResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResult = TextResult.T
  object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseLines = ParseLines.T
  object ParseLines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonInTextText = JsonInTextText.T
  object JsonInTextText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResultUser = ResultUser.T
  object ResultUser extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val McpServer = AgentDefinitions.McpServer
  def toolId(name: com.histmersion.server.agents.ClaudeCode.ToolName): com.histmersion.server.agents.ClaudeCode.ToolId = ToolStyle.Claude.tool(name)

  /** The one MCP server of a run: the agent endpoint, with the step's run token. */
  def mcpConfig(url: com.histmersion.server.HttpDownload.Url, token: Option[com.histmersion.server.agents.ClaudeCode.McpConfigToken]): Json =
    val server = Json.obj(
      ("type", "http".asJson) :: ("url", url.asJson) ::
        token.toList.map(k => "headers" -> Json.obj("Authorization" -> s"Bearer $k".asJson))*
    )
    Json.obj("mcpServers" -> Json.obj(McpServer -> server))

  /** The typed answer's JSON Schema for `--json-schema`. Claude Code validates against draft-07, so the 2020-12
    * `$schema` marker sttp-ai's renderer may add is dropped (the rest is shared).
    */
  def schemaJson(s: ResponseSchema[?]): com.histmersion.server.agents.ClaudeCode.SchemaJsonResult =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema")).noSpaces

  /** How the step's prompt reaches the CLI: as the generated plugin's agent (with its role settings), or — no generated
    * tree — as a system prompt file.
    */
  enum Launch:
    case Agent(pluginDir: Launch.Agent.PluginDir, name: Launch.Agent.Name, settings: Launch.Agent.Settings)
    case SystemPrompt(file: Launch.SystemPrompt.File)

  object Launch:
    object Agent:
      type PluginDir = PluginDir.T
      object PluginDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Name = Name.T
      object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Settings = Settings.T
      object Settings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object SystemPrompt:
      type File = File.T
      object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `claude -p …`, in a fixed order. `offered` are the step's tools (MCP names, unprefixed); `all` every agent tool,
    * so the ones not offered are removed from the model's context outright; `web` the built-in web tools a web role
    * keeps.
    */
  def argv(
      cfg: ClaudeCodeConfig,
      model: com.histmersion.server.agents.ClaudeCode.ModelId,
      maxTurns: com.histmersion.server.agents.ClaudeCode.ArgvMaxTurns,
      schema: com.histmersion.server.agents.ClaudeCode.ArgvSchema,
      launch: Launch,
      mcpFile: Option[com.histmersion.server.agents.ClaudeCode.ArgvMcpFile],
      offered: List[com.histmersion.server.agents.ClaudeCode.ArgvOffered],
      all: List[com.histmersion.server.agents.ClaudeCode.ArgvAll],
      web: List[com.histmersion.server.agents.ClaudeCode.ArgvWeb] = Nil
  ): List[com.histmersion.server.agents.ClaudeCode.Argument] =
    val denied = all.filterNot(offered.contains)
    val how = launch match
      case Launch.Agent(dir, name, settings) =>
        List("--plugin-dir", dir, "--agent", s"${AgentGenerator.HeadlessPlugin}:$name", "--settings", settings)
      case Launch.SystemPrompt(file) => List("--system-prompt-file", file)
    List(
      cfg.command,
      "-p",
      "--output-format",
      "stream-json",
      "--verbose",
      "--model",
      model,
      "--max-turns",
      AgentGenerator.claudeTurns(maxTurns).toString,
      "--no-session-persistence",
      "--setting-sources",
      ""
    ) ++
      (if cfg.disableSlashCommands then List("--disable-slash-commands") else Nil) ++
      how ++ List(
        "--json-schema",
        schema,
        "--tools",
        web.mkString(","),
        "--strict-mcp-config",
        "--permission-mode",
        "dontAsk"
      ) ++
      mcpFile.toList.flatMap(f => List("--mcp-config", f)) ++
      List("--allowedTools", (offered.map(toolId) ++ web :+ AgentGenerator.ClaudeAnswerTool).mkString(",")) ++
      (if mcpFile.nonEmpty && denied.nonEmpty then List("--disallowedTools", denied.map(toolId).mkString(","))
       else Nil) ++
      cfg.extraArgs.trim.split("\\s+").toList.filter(_.nonEmpty)

  /** The CLI's environment: the server's with the configured `env` applied (a value set, an empty one removed), then —
    * whatever `env` said — minus an Anthropic key unless asked (a key always wins in `-p` and would bill the API), no
    * claude.ai connectors, the output cap, a config dir of its own when set, and what the quota-guard mod reads (the
    * run token, where to report).
    */
  def environment(
      cfg: ClaudeCodeConfig,
      maxTokens: com.histmersion.server.agents.ClaudeCode.EnvironmentMaxTokens,
      token: Option[com.histmersion.server.agents.ClaudeCode.EnvironmentToken],
      quotaUrl: com.histmersion.server.HttpDownload.Url,
      env: java.util.Map[com.histmersion.server.GazetteerPaths.EnvName, com.histmersion.server.GazetteerPaths.EnvText]
  )(using com.histmersion.util.system.JvmHome): Unit =
    cfg.envChanges.foreach {
      case (k, Some(v)) => val _ = env.put(k, v)
      case (k, None)    => val _ = env.remove(k)
    }
    if !cfg.useApiKey then
      val _ = env.remove("ANTHROPIC_API_KEY")
      val _ = env.remove("ANTHROPIC_AUTH_TOKEN")
    if cfg.configDir.trim.nonEmpty then
      val _ = env.put(com.histmersion.server.GazetteerPaths.EnvName("CLAUDE_CONFIG_DIR"), AgentTrees.expand(cfg.configDir.trim))
    val _ = env.put(com.histmersion.server.GazetteerPaths.EnvName("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), math.max(1, maxTokens).toString)
    val _ = env.put(com.histmersion.server.GazetteerPaths.EnvName("DISABLE_AUTOUPDATER"), com.histmersion.server.GazetteerPaths.EnvText("1"))
    val _ = env.put(com.histmersion.server.GazetteerPaths.EnvName("ENABLE_CLAUDEAI_MCP_SERVERS"), com.histmersion.server.GazetteerPaths.EnvText("false"))
    val _ = env.put(AgentGenerator.QuotaUrlEnv, quotaUrl)
    token.foreach(t => env.put(AgentGenerator.TokenEnv, t))

  // ── the probe (docs/research-plan-quotas.md §8) ─────────────────────────────────────────────────

  /** The probe's one-word prompt — the same word Claude Code's own quota check sends. */
  val ProbePrompt = "quota"

  /** Claude Code 2.1.289 knows the plan's windows only after a model response (§3a P3: `rateLimits` is empty at
    * `session.start`), so a fresh reading costs one turn. This is the smallest: the probe model, a one-line system
    * prompt in place of Claude Code's, no tools, no MCP server, no skills or settings, one turn, no session kept. Its
    * `rate_limit_event` is the reading.
    */
  def probeArgv(cfg: ClaudeCodeConfig, model: com.histmersion.server.agents.ClaudeCode.ModelId): List[com.histmersion.server.agents.ClaudeCode.Argument] =
    List(
      cfg.command,
      "-p",
      "--output-format",
      "stream-json",
      "--verbose",
      "--model",
      model,
      "--max-turns",
      "1",
      "--no-session-persistence",
      "--setting-sources",
      "",
      "--system-prompt",
      "Reply with one word.",
      "--tools",
      "",
      "--strict-mcp-config",
      "--disable-slash-commands",
      "--permission-mode",
      "dontAsk"
    ) ++
      cfg.extraArgs.trim.split("\\s+").toList.filter(_.nonEmpty)

  /** The probe's environment: a run's ([[environment]]: no API key unless asked, no claude.ai connectors, the config
    * dir), with the output capped at `maxTokens` and no run token or quota URL.
    */
  def probeEnvironment(cfg: ClaudeCodeConfig, maxTokens: com.histmersion.server.agents.ClaudeCode.ProbeEnvironmentMaxTokens, env: java.util.Map[com.histmersion.server.GazetteerPaths.EnvName, com.histmersion.server.GazetteerPaths.EnvText])(using com.histmersion.util.system.JvmHome): Unit =
    environment(cfg, maxTokens, None, com.histmersion.server.HttpDownload.Url(""), env)
    val _ = env.remove(AgentGenerator.QuotaUrlEnv)
    val _ = env.remove(AgentGenerator.TokenEnv)

  /** A `rate_limit_event` line as it arrives: recorded in the Claude plan pool; when the pool is now below its floor
    * (or held) the answer says why, and the process is stopped.
    */
  def watch(line: com.histmersion.server.agents.ClaudeCode.WatchLine, pools: PoolSink): Option[com.histmersion.server.agents.ClaudeCode.WatchResult] =
    if !line.contains("rate_limit_event") then None
    else
      parser.parse(line).toOption.filter(_.hcursor.downField("type").as[String].contains("rate_limit_event")).flatMap {
        j =>
          val now = System.currentTimeMillis()
          val rs = j.hcursor
            .downField("rate_limit_info")
            .focus
            .map(PlanPools.claudeReadings(_, com.histmersion.api.automation.PlanQuota.Source.Run, now))
            .getOrElse(Nil)
          pools.record(com.histmersion.api.automation.PlanQuota.PoolId.ClaudePlan, rs)
          pools.blocked(com.histmersion.api.automation.PlanQuota.PoolId.ClaudePlan, None).map(_._1)
      }

  /** A turn the quota-guard mod aborted: not an error result, but `terminal_reason: "aborted_streaming"` with an empty
    * answer (§3a P3).
    */
  def abortedByGuard(r: Result): Boolean =
    r.terminalReason.contains("aborted_streaming") && r.text.trim.isEmpty && r.structured.isEmpty

  // ── the output ─────────────────────────────────────────────────────────────────────────────────

  /** The final `result` line. `usage` follows sttp-ai's convention (input counts cached tokens too). */
  final case class Result(
      subtype: Result.Subtype,
      isError: Result.IsError,
      text: Result.Text,
      structured: Option[Json],
      turns: Result.Turns,
      costUsd: Option[Result.CostUsd],
      denials: List[Result.Denials],
      errors: List[Result.Errors],
      terminalReason: Option[Result.TerminalReason] = None
  )

  object Result:
    type Subtype = Subtype.T
    object Subtype extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsError = IsError.T
    object IsError extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Turns = Turns.T
    object Turns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Denials = Denials.T
    object Denials extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Errors = Errors.T
    object Errors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TerminalReason = TerminalReason.T
    object TerminalReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What one `claude -p` run printed: the conversation (assistant text, tool calls and their results, in order), the
    * result line, the usage and the model that served it — the result's `modelUsage` entry with the most output tokens
    * (Claude Code may give side work to a smaller model), else the init line's model; `served` is every `modelUsage`
    * entry with its own usage; `firstCall` the input of the run's FIRST model call ([[firstCallTokens]]).
    */
  final case class Outcome(
      entries: Vector[ConversationEntry],
      toolCalls: Vector[ToolCallRecord],
      result: Option[Result],
      usage: TokenUsage,
      modelId: Option[Outcome.ModelId],
      mcpProblem: Option[Outcome.McpProblem],
      readings: List[com.histmersion.api.automation.PlanQuota.Reading] = Nil,
      served: List[(Outcome.ServedPart1, TokenUsage)] = Nil,
      firstCall: Option[Outcome.FirstCall] = None
  )

  object Outcome:
    type ModelId = com.histmersion.server.agents.ClaudeCode.ModelId
    val ModelId: _root_.com.histmersion.server.agents.ClaudeCode.ModelId.type = _root_.com.histmersion.server.agents.ClaudeCode.ModelId
    type McpProblem = McpProblem.T
    object McpProblem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ServedPart1 = com.histmersion.server.agents.ClaudeCode.ModelId
    val ServedPart1: _root_.com.histmersion.server.agents.ClaudeCode.ModelId.type = _root_.com.histmersion.server.agents.ClaudeCode.ModelId
    type FirstCall = FirstCall.T
    object FirstCall extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def tokens(j: Json, keys: String*): com.histmersion.server.agents.ClaudeCode.TokensResult =
    keys.iterator.flatMap(k => j.hcursor.downField(k).as[Long].toOption).nextOption().getOrElse(0L)

  /** `usage` of the result line (input_tokens excludes cache reads/writes on Anthropic's wire). */
  private def usageOf(u: Json): TokenUsage =
    val (in, out) = (tokens(u, "input_tokens", "inputTokens"), tokens(u, "output_tokens", "outputTokens"))
    val (cr, cw) = (
      tokens(u, "cache_read_input_tokens", "cacheReadInputTokens"),
      tokens(u, "cache_creation_input_tokens", "cacheCreationInputTokens")
    )
    TokenUsage(Tokens(in + cr + cw), Tokens(out), Tokens(cr), Tokens.Zero, Tokens(cw))

  /** The input of one model call, from an `assistant` event's `message.usage` (the API's `message_start` usage, which
    * every event of that message repeats): input + cache read + cache write tokens — the system prompt, the tool
    * definitions and the conversation so far. None when the event carries no usage, or only zeros (a `<synthetic>`
    * message the CLI made up, not a call).
    */
  def firstCallTokens(message: Json): Option[com.histmersion.server.agents.ClaudeCode.FirstCallTokensResult] =
    val c = message.hcursor
    if c.downField("model").as[String].contains("<synthetic>") then None
    else
      c.downField("usage")
        .focus
        .map(u =>
          tokens(u, "input_tokens") + tokens(u, "cache_read_input_tokens") + tokens(u, "cache_creation_input_tokens")
        )
        .filter(_ > 0)

  private def text(content: Json): com.histmersion.server.agents.ClaudeCode.TextResult =
    content.asString.getOrElse(
      content.asArray.toList.flatten.flatMap(b => b.hcursor.downField("text").as[String].toOption).mkString("\n")
    )

  def parse(lines: List[com.histmersion.server.agents.ClaudeCode.ParseLines]): Outcome =
    val events = lines.map(_.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val names = scala.collection.mutable.Map.empty[String, String] // tool_use id → tool name
    // `var`s and builders: the accumulators of ONE parse, over the events in order
    var turn = 0 // the assistant message count, for the tool call records
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    val pending = scala.collection.mutable.LinkedHashMap.empty[String, ToolCall]
    var result: Option[Result] = None
    var usage: Option[TokenUsage] = None
    var model: Option[com.histmersion.server.agents.ClaudeCode.ModelId] = None
    var served: List[(com.histmersion.server.agents.ClaudeCode.ModelId, TokenUsage)] = Nil
    var mcpProblem: Option[com.histmersion.server.agents.ClaudeCode.ParseMcpProblem] = None
    var firstCall: Option[com.histmersion.server.agents.ClaudeCode.ParseFirstCall] = None
    val readings = List.newBuilder[com.histmersion.api.automation.PlanQuota.Reading]
    val now = System.currentTimeMillis()
    def strip(n: com.histmersion.server.agents.ClaudeCode.ToolName) = n.stripPrefix(s"mcp__${McpServer}__")
    events.foreach { e =>
      val c = e.hcursor
      c.downField("type").as[String].getOrElse("") match
        case "system" if c.downField("subtype").as[String].contains("init") =>
          model = c.downField("model").as[String].toOption.orElse(model)
          val servers = c.downField("mcp_servers").as[List[Json]].getOrElse(Nil)
          mcpProblem = servers
            .find(s => s.hcursor.downField("name").as[String].contains(McpServer))
            .flatMap(_.hcursor.downField("status").as[String].toOption)
            .filterNot(s => s == "connected" || s == "pending")
            .map(s => s"the agent MCP server is $s")
        case "assistant" if c.downField("parent_tool_use_id").focus.forall(_.isNull) =>
          turn += 1
          if firstCall.isEmpty then firstCall = c.downField("message").focus.flatMap(firstCallTokens)
          val blocks = c.downField("message").downField("content").as[List[Json]].getOrElse(Nil)
          val said = blocks
            .filter(_.hcursor.downField("type").as[String].contains("text"))
            .flatMap(_.hcursor.downField("text").as[String].toOption)
            .mkString("\n")
          val tcs = blocks.filter(_.hcursor.downField("type").as[String].contains("tool_use")).map { b =>
            val id = b.hcursor.downField("id").as[String].getOrElse("")
            val nm = strip(b.hcursor.downField("name").as[String].getOrElse(""))
            names(id) = nm
            val tc = ToolCall(id, nm, b.hcursor.downField("input").focus.fold("{}")(_.noSpaces))
            pending(id) = tc
            tc
          }
          entries += ConversationEntry.AssistantResponse(said, tcs)
        case "user" =>
          c.downField("message")
            .downField("content")
            .as[List[Json]]
            .getOrElse(Nil)
            .filter(_.hcursor.downField("type").as[String].contains("tool_result"))
            .foreach { b =>
              val id = b.hcursor.downField("tool_use_id").as[String].getOrElse("")
              val out = b.hcursor.downField("content").focus.fold("")(text)
              val nm = names.getOrElse(id, "")
              entries += ConversationEntry.ToolResult(id, nm, out)
              pending.remove(id).foreach(tc => calls += ToolCallRecord(id, nm, tc.input, out, turn))
            }
        case "rate_limit_event" =>
          c.downField("rate_limit_info")
            .focus
            .foreach(i =>
              readings ++= PlanPools.claudeReadings(i, com.histmersion.api.automation.PlanQuota.Source.Run, now)
            )
        // a model that backgrounds a subagent makes TWO result events: the last one is the answer (§3a)
        case "result" =>
          val modelUsage = c.downField("modelUsage").focus.flatMap(_.asObject).toList.flatMap(_.toList)
          usage = Some(if modelUsage.nonEmpty then modelUsage.map((_, u) => usageOf(u)).reduce(_ + _)
          else c.downField("usage").focus.fold(TokenUsage.Zero)(usageOf))
          served = modelUsage.map((m, u) => m -> usageOf(u)).sortBy((m, u) => (-u.outputTokens.value, m))
          model = served.headOption.map(_._1).orElse(model)
          result = Some(
            Result(
              subtype = c.downField("subtype").as[String].getOrElse(""),
              isError = c.downField("is_error").as[Boolean].getOrElse(false),
              text = c.downField("result").as[String].getOrElse(""),
              structured = c.downField("structured_output").focus.filterNot(_.isNull),
              turns = c.downField("num_turns").as[Int].getOrElse(turn),
              costUsd = c.downField("total_cost_usd").as[Double].toOption,
              denials = c
                .downField("permission_denials")
                .as[List[Json]]
                .getOrElse(Nil)
                .map(d => d.hcursor.downField("tool_name").as[String].getOrElse(d.noSpaces)),
              errors = c.downField("errors").as[List[Json]].getOrElse(Nil).map(j => j.asString.getOrElse(j.noSpaces)),
              terminalReason = c.downField("terminal_reason").as[String].toOption
            )
          )
        case _ => ()
    }
    Outcome(
      entries.result(),
      calls.result(),
      result,
      usage.getOrElse(TokenUsage.Zero),
      model,
      mcpProblem,
      readings.result(),
      served,
      firstCall
    )

  /** The plan's usage limit, as the CLI reports it: `… usage limit reached|<epoch seconds>` (older releases) or a
    * sentence about the limit (newer) — the reset time when the text carries one.
    */
  final case class Limit(message: Limit.Message, until: Option[java.time.Instant])

  object Limit:
    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def usageLimit(r: Result): Option[Limit] =
    val all = (r.text :: r.errors).mkString(" ")
    val low = all.toLowerCase(java.util.Locale.ROOT)
    Option.when(
      r.isError && (low.contains("usage limit") || low.contains("limit reached") || low.contains("hit your limit"))
    ) {
      val epoch = "\\|(\\d{9,11})".r.findFirstMatchIn(all).flatMap(_.group(1).toLongOption)
      Limit(s"claude -p: ${all.take(300)}", epoch.map(java.time.Instant.ofEpochSecond))
    }

  /** The one JSON object of a text answer: the body of a ```json fence, else the text from its first `{` to its last
    * `}`. None when that is not a JSON object.
    */
  def jsonInText(text: com.histmersion.server.agents.ClaudeCode.JsonInTextText): Option[Json] =
    val fenced = "(?s)```(?:json)?\\s*(\\{.*\\})\\s*```".r.findFirstMatchIn(text).map(_.group(1))
    val braces = Option.when(text.indexOf('{') >= 0 && text.lastIndexOf('}') > text.indexOf('{'))(
      text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
    )
    fenced.orElse(braces).flatMap(t => io.circe.parser.parse(t).toOption).filter(_.isObject)

  /** sttp-ai's result from what the CLI printed, or the plan's usage limit (Left). */
  def result[Out](
      o: Outcome,
      user: com.histmersion.server.agents.ClaudeCode.ResultUser,
      schema: ResponseSchema[Out]
  ): Either[Limit, AgentResult[Either[AgentFailure, Out]]] =
    val r = o.result.getOrElse(Result("missing", isError = true, "", None, 0, None, Nil, Nil))
    usageLimit(r) match
      case Some(l) => Left(l)
      case None    =>
        val finish = r.subtype match
          case "success" if !r.isError => FinishReason.NaturalStop
          case "error_max_turns"       => FinishReason.MaxIterations
          case "error_max_budget_usd"  => FinishReason.BudgetExceeded
          case other                   =>
            FinishReason.Error(
              (List(Option(r.text).filter(_.nonEmpty), Some(other).filter(_.nonEmpty)).flatten ++
                r.errors ++ o.mcpProblem.toList ++
                Option.when(r.denials.nonEmpty)(s"denied: ${r.denials.mkString(", ")}").toList).mkString("; ").take(600)
            )
        // a model that wrote the JSON as text instead of calling the answer tool still answered
        val structured =
          r.structured.orElse(Option.when(finish == FinishReason.NaturalStop)(jsonInText(r.text)).flatten)
        val raw = structured.fold(r.text)(_.noSpaces)
        val answer: Either[AgentFailure, Out] = structured match
          case Some(j) => schema.codec.decodeJson(j).left.map(e => AgentParseError(raw, e))
          // #941: a text answer with no decodable object keeps the parser's error as its cause
          case None =>
            Left(
              AgentIncomplete(
                raw,
                finish,
                Option.when(finish == FinishReason.NaturalStop)(CliOutcomes.textProblem(r.text)).flatten
              )
            )
        val entries = (ConversationEntry.UserPrompt(user) +: o.entries) ++
          Option.when(r.structured.isDefined)(ConversationEntry.AssistantResponse(raw, Nil))
        // one call per model that served the step, each with its own usage
        val calls = if o.served.isEmpty then Seq(LlmCallUsage(o.modelId, o.usage))
        else o.served.map((m, u) => LlmCallUsage(Some(m), u))
        Right(
          AgentResult(answer, math.max(1, r.turns), o.toolCalls, finish, o.usage, calls, ConversationHistory(entries))
        )

  /** The `claude-code` client for `ref`, or why it cannot be used here. */
  def client(cfg: ClaudeCodeConfig, rt: CliRuntime, ref: ModelRef): Either[com.histmersion.server.agents.ClaudeCode.Problem, LlmClient] =
    if ref.provider != ProviderKind.ClaudeCode then Left(s"${ref.wire} is not a claude-code model")
    else if !rt.loopback && !cfg.allowNonLoopback then
      Left(
        "claude-code is refused while the server listens beyond loopback: another client could spend " +
          "your Claude plan. Bind to 127.0.0.1 (HISTMERSION_BIND), or — when every client of this server is " +
          "you — set agents.providers.claude-code.allow-non-loopback (HISTMERSION_AGENTS_CLAUDE_ALLOW_NON_LOOPBACK)"
      )
    else if cfg.command.trim.isEmpty then Left("agents.providers.claude-code.command is empty")
    else Right(ClaudeCodeClient(ref, cfg, rt))

  /** Whether the server listens on loopback only — the `claude-code` (and every plan-login) provider's precondition:
    * nobody else's request may spend the owner's plan.
    */
  object Env:
    /** `127.x`, `::1`, `localhost` — a bind nobody else can reach. `0.0.0.0` (every interface) is not. */

    def isLoopback(host: com.histmersion.server.Composition.Host): Boolean =
      val h = host.trim.toLowerCase(java.util.Locale.ROOT).stripPrefix("[").stripSuffix("]")
      h == "localhost" || h == "::1" || h.startsWith("127.")
