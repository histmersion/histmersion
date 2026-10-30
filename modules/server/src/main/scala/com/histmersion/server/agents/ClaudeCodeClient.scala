package com.histmersion.server.agents

import com.histmersion.config.ClaudeCodeConfig
import com.histmersion.api.Agents.{ModelRef, ProviderKind}
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Whether the server listens on loopback only — the `claude-code` (and every plan-login) provider's precondition:
  * nobody else's request may spend the owner's plan.
  */
object ClaudeCodeEnv:
  /** `127.x`, `::1`, `localhost` — a bind nobody else can reach. `0.0.0.0` (every interface) is not. */
  def isLoopback(host: String): Boolean =
    val h = host.trim.toLowerCase(java.util.Locale.ROOT).stripPrefix("[").stripSuffix("]")
    h == "localhost" || h == "::1" || h.startsWith("127.")

/** The `claude-code` provider: ONE `claude -p` process per LLM step attempt, run as the step's agent from the GENERATED
  * headless plugin (docs/research-agent-runtimes.md §2.4, §3a):
  *
  *   - `--plugin-dir <agents>/claude-code/headless --agent histmersion-agents:<role>`: the role's prompt (its includes
  *     and skills inlined — a main-thread agent gets no `skills:` preload) and its tools;
  *     `--settings <roles/<role>/settings.json>`: deny-by-default permissions, NO `disableAllHooks` (it would turn the
  *     quota-guard mod off); `--setting-sources ""` keeps the person's own settings and hooks out (the plan login
  *     stays, §3a P2). `--disable-slash-commands` (config `disable-slash-commands`): no skill or slash command is
  *     loaded — the role's skills are already in its prompt, and a main-thread `--agent` gets no `skills:` preload
  *     anyway (§3a P4).
  *   - Tools: built-in tools off (`--tools ""`, or WebSearch/WebFetch for a web role), the agent MCP endpoint the only
  *     server (`--strict-mcp-config --mcp-config`, which also drops any server a plugin bundles, §3a P4), reached with
  *     a per-step RUN TOKEN that the server binds to this role's tools — the allowlist holds whatever the CLI does.
  *   - The user message — the untrusted data — on stdin; the typed answer `--json-schema`.
  *   - Quota: every `rate_limit_event` (both windows, every call, §3a P1) is recorded in the Claude plan pool as it
  *     arrives; when the pool falls below its floor mid-step the process is stopped. The quota-guard mod does the same
  *     from inside (it denies tools and aborts the turn: the result then says `terminal_reason: "aborted_streaming"`
  *     with an empty answer). Either way the step ends as the plan's limit — the chain is re-picked, and pauses with
  *     `plan-quota`.
  *
  * Without a generated plugin (generation off or failed) it falls back to `--system-prompt-file` with the same prompt.
  * It never touches a credential: a key in the server's environment is REMOVED from the CLI's unless `use-api-key` (a
  * present key always wins in `-p`).
  */
final class ClaudeCodeClient(val model: ModelRef, cfg: ClaudeCodeConfig, rt: CliRuntime) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    CliProcess.withDir("histmersion-claude-") { dir =>
      val grant = Option.when(call.tools.nonEmpty)(rt.grant(call, call.agent, cfg.timeoutSeconds)).flatten
      try
        val mcp = grant.map(g =>
          CliProcess.privateFile(dir.resolve("mcp.json"), ClaudeCode.mcpConfig(rt.agentMcpUrl, Some(g.token)).noSpaces)
        )
        // the generated plugin and role settings, when they exist for this agent
        val plugin = for
          a <- call.agent
          p <- rt.trees.claudePlugin.filter(d => Files.isRegularFile(d.resolve("agents").resolve(s"${a.name}.md")))
          s <- rt.trees.claudeRole(a.name).filter(Files.isRegularFile(_))
        yield ClaudeCode.Launch.Agent(p.toString, a.name, s.toString)
        val launch = plugin.getOrElse(
          ClaudeCode.Launch.SystemPrompt(
            Files.writeString(dir.resolve("system.md"), call.system, StandardCharsets.UTF_8).toString
          )
        )
        val web = call.agent.map(a => AgentGenerator.claudeWebTools(a.web)).getOrElse(Nil)
        // the CLI's working directory: empty, so no project CLAUDE.md, .claude/ or .mcp.json is read
        val work = Files.createDirectories(dir.resolve("work"))
        val argv = ClaudeCode.argv(
          cfg,
          model.model,
          call.maxIterations,
          ClaudeCode.schemaJson(call.schema),
          launch,
          mcp.map(_.toString),
          call.tools.map(_.name),
          ToolName.values.toList.map(_.wire),
          web
        )
        val pool = rt.pools
        // the LLM-slot semaphore and the latency the trace shows: the interceptors see the whole process
        // as one call. Carried out of the interceptor chain in a reference (its return type is sttp-ai's).
        val out = java.util.concurrent.atomic.AtomicReference[Option[ClaudeCode.Outcome]](None)
        val stopped = java.util.concurrent.atomic.AtomicReference[Option[String]](None)
        val ctx = LlmCallContext(
          ConversationHistory.withInitialPrompt(call.user),
          call.tools.nonEmpty,
          IterationInfo(1, math.max(1, call.maxIterations))
        )
        val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
          val r = CliProcess.run(
            argv,
            work,
            env => ClaudeCode.environment(cfg, call.maxTokens, grant.map(_.token), rt.quotaUrl, env),
            call.user,
            cfg.timeoutSeconds,
            "install Claude Code or set agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND) to its absolute path",
            onLine = l => pool.flatMap(p => ClaudeCode.watch(l, p))
          )
          stopped.set(r.stopped)
          val o = ClaudeCode.parse(r.lines)
          out.set(Some(o))
          // the whole process is one intercepted call: its first model call is read from the stream
          FirstCallProbe.report(call.interceptors, o.firstCall)
          if o.result.isEmpty && r.stopped.isEmpty then
            throw java.io.IOException(
              s"claude -p exited ${r.exit} without a result" +
                (if r.stderr.nonEmpty then s": ${r.stderr.takeRight(600)}" else "")
            )
          AgentResponse(o.result.fold("")(_.text), Nil, StopReason.EndTurn, Some(o.usage), o.modelId)
        }
        val o = out.get().getOrElse(throw IllegalStateException("claude-code: no outcome"))
        // every reading of the run is in the pool (also the ones the watch already recorded: newest wins)
        pool.foreach(p => p.record(com.histmersion.api.PlanQuota.PoolId.ClaudePlan, o.readings))
        val guard = stopped
          .get()
          .orElse(o.result.filter(ClaudeCode.abortedByGuard).map(_ => "the quota-guard mod ended the turn"))
        guard match
          case Some(why) =>
            val until = pool
              .flatMap(_.blocked(com.histmersion.api.PlanQuota.PoolId.ClaudePlan, Some(model.model)))
              .flatMap(_._2)
              .map(java.time.Instant.ofEpochMilli)
            throw ModelLimitReached(model, until, s"claude -p stopped by the quota guard: $why")
          case None =>
            ClaudeCode.result(o, call.user, call.schema) match
              case Right(r)  => r
              case Left(lim) => throw ModelLimitReached(model, lim.until, lim.message)
      finally grant.foreach(g => rt.tokens.revoke(g.token))
    }

/** The pure half of the `claude-code` provider: the argument vector, the environment, the MCP config, and the
  * `stream-json` output read back into sttp-ai's result types.
  */
object ClaudeCode:

  /** The MCP server's name as the CLI sees it; its tools are `mcp__histmersion__<tool>`. */
  val McpServer = AgentDefinitions.McpServer
  def toolId(name: String): String = ToolStyle.Claude.tool(name)

  /** The one MCP server of a run: the agent endpoint, with the step's run token. */
  def mcpConfig(url: String, token: Option[String]): Json =
    val server = Json.obj(
      ("type", "http".asJson) :: ("url", url.asJson) ::
        token.toList.map(k => "headers" -> Json.obj("Authorization" -> s"Bearer $k".asJson))*
    )
    Json.obj("mcpServers" -> Json.obj(McpServer -> server))

  /** The typed answer's JSON Schema for `--json-schema`. Claude Code validates against draft-07, so the 2020-12
    * `$schema` marker sttp-ai's renderer may add is dropped (the rest is shared).
    */
  def schemaJson(s: ResponseSchema[?]): String =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema")).noSpaces

  /** How the step's prompt reaches the CLI: as the generated plugin's agent (with its role settings), or — no generated
    * tree — as a system prompt file.
    */
  enum Launch:
    case Agent(pluginDir: String, name: String, settings: String)
    case SystemPrompt(file: String)

  /** `claude -p …`, in a fixed order. `offered` are the step's tools (MCP names, unprefixed); `all` every agent tool,
    * so the ones not offered are removed from the model's context outright; `web` the built-in web tools a web role
    * keeps.
    */
  def argv(
      cfg: ClaudeCodeConfig,
      model: String,
      maxTurns: Int,
      schema: String,
      launch: Launch,
      mcpFile: Option[String],
      offered: List[String],
      all: List[String],
      web: List[String] = Nil
  ): List[String] =
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
      maxTokens: Int,
      token: Option[String],
      quotaUrl: String,
      env: java.util.Map[String, String]
  ): Unit =
    cfg.envChanges.foreach {
      case (k, Some(v)) => val _ = env.put(k, v)
      case (k, None)    => val _ = env.remove(k)
    }
    if !cfg.useApiKey then
      val _ = env.remove("ANTHROPIC_API_KEY")
      val _ = env.remove("ANTHROPIC_AUTH_TOKEN")
    if cfg.configDir.trim.nonEmpty then
      val _ = env.put("CLAUDE_CONFIG_DIR", AgentTrees.expand(cfg.configDir.trim))
    val _ = env.put("CLAUDE_CODE_MAX_OUTPUT_TOKENS", math.max(1, maxTokens).toString)
    val _ = env.put("DISABLE_AUTOUPDATER", "1")
    val _ = env.put("ENABLE_CLAUDEAI_MCP_SERVERS", "false")
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
  def probeArgv(cfg: ClaudeCodeConfig, model: String): List[String] =
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
  def probeEnvironment(cfg: ClaudeCodeConfig, maxTokens: Int, env: java.util.Map[String, String]): Unit =
    environment(cfg, maxTokens, None, "", env)
    val _ = env.remove(AgentGenerator.QuotaUrlEnv)
    val _ = env.remove(AgentGenerator.TokenEnv)

  /** A `rate_limit_event` line as it arrives: recorded in the Claude plan pool; when the pool is now below its floor
    * (or held) the answer says why, and the process is stopped.
    */
  def watch(line: String, pools: PoolSink): Option[String] =
    if !line.contains("rate_limit_event") then None
    else
      parser.parse(line).toOption.filter(_.hcursor.downField("type").as[String].contains("rate_limit_event")).flatMap {
        j =>
          val now = System.currentTimeMillis()
          val rs = j.hcursor
            .downField("rate_limit_info")
            .focus
            .map(PlanPools.claudeReadings(_, com.histmersion.api.PlanQuota.Source.Run, now))
            .getOrElse(Nil)
          pools.record(com.histmersion.api.PlanQuota.PoolId.ClaudePlan, rs)
          pools.blocked(com.histmersion.api.PlanQuota.PoolId.ClaudePlan, None).map(_._1)
      }

  /** A turn the quota-guard mod aborted: not an error result, but `terminal_reason: "aborted_streaming"` with an empty
    * answer (§3a P3).
    */
  def abortedByGuard(r: Result): Boolean =
    r.terminalReason.contains("aborted_streaming") && r.text.trim.isEmpty && r.structured.isEmpty

  // ── the output ─────────────────────────────────────────────────────────────────────────────────

  /** The final `result` line. `usage` follows sttp-ai's convention (input counts cached tokens too). */
  final case class Result(
      subtype: String,
      isError: Boolean,
      text: String,
      structured: Option[Json],
      turns: Int,
      costUsd: Option[Double],
      denials: List[String],
      errors: List[String],
      terminalReason: Option[String] = None
  )

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
      modelId: Option[String],
      mcpProblem: Option[String],
      readings: List[com.histmersion.api.PlanQuota.Reading] = Nil,
      served: List[(String, TokenUsage)] = Nil,
      firstCall: Option[Long] = None
  )

  private def tokens(j: Json, keys: String*): Long =
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
  def firstCallTokens(message: Json): Option[Long] =
    val c = message.hcursor
    if c.downField("model").as[String].contains("<synthetic>") then None
    else
      c.downField("usage")
        .focus
        .map(u =>
          tokens(u, "input_tokens") + tokens(u, "cache_read_input_tokens") + tokens(u, "cache_creation_input_tokens")
        )
        .filter(_ > 0)

  private def text(content: Json): String =
    content.asString.getOrElse(
      content.asArray.toList.flatten.flatMap(b => b.hcursor.downField("text").as[String].toOption).mkString("\n")
    )

  def parse(lines: List[String]): Outcome =
    val events = lines.map(_.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val names = scala.collection.mutable.Map.empty[String, String] // tool_use id → tool name
    // `var`s and builders: the accumulators of ONE parse, over the events in order
    var turn = 0 // the assistant message count, for the tool call records
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    val pending = scala.collection.mutable.LinkedHashMap.empty[String, ToolCall]
    var result: Option[Result] = None
    var usage: Option[TokenUsage] = None
    var model: Option[String] = None
    var served: List[(String, TokenUsage)] = Nil
    var mcpProblem: Option[String] = None
    var firstCall: Option[Long] = None
    val readings = List.newBuilder[com.histmersion.api.PlanQuota.Reading]
    val now = System.currentTimeMillis()
    def strip(n: String) = n.stripPrefix(s"mcp__${McpServer}__")
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
            .foreach(i => readings ++= PlanPools.claudeReadings(i, com.histmersion.api.PlanQuota.Source.Run, now))
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
  final case class Limit(message: String, until: Option[java.time.Instant])

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
  def jsonInText(text: String): Option[Json] =
    val fenced = "(?s)```(?:json)?\\s*(\\{.*\\})\\s*```".r.findFirstMatchIn(text).map(_.group(1))
    val braces = Option.when(text.indexOf('{') >= 0 && text.lastIndexOf('}') > text.indexOf('{'))(
      text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
    )
    fenced.orElse(braces).flatMap(t => io.circe.parser.parse(t).toOption).filter(_.isObject)

  /** sttp-ai's result from what the CLI printed, or the plan's usage limit (Left). */
  def result[Out](
      o: Outcome,
      user: String,
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
  def client(cfg: ClaudeCodeConfig, rt: CliRuntime, ref: ModelRef): Either[String, LlmClient] =
    if ref.provider != ProviderKind.ClaudeCode then Left(s"${ref.wire} is not a claude-code model")
    else if !rt.loopback && !cfg.allowNonLoopback then
      Left(
        "claude-code is refused while the server listens beyond loopback: another client could spend " +
          "your Claude plan. Bind to 127.0.0.1 (HISTMERSION_BIND), or — when every client of this server is " +
          "you — set agents.providers.claude-code.allow-non-loopback (HISTMERSION_AGENTS_CLAUDE_ALLOW_NON_LOOPBACK)"
      )
    else if cfg.command.trim.isEmpty then Left("agents.providers.claude-code.command is empty")
    else Right(ClaudeCodeClient(ref, cfg, rt))
