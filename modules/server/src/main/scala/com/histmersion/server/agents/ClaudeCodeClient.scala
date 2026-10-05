package com.histmersion.server.agents

import com.histmersion.core.{AppConfig, ClaudeCodeConfig}
import com.histmersion.core.Agents.{ModelRef, ProviderKind}
import io.circe.{Json, parser}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

/** Where the `claude` CLI finds this server's agent tools, and whether this server may use the CLI at
  * all: `mcpUrl` is the read-only agent MCP endpoint (`/mcp/agent`), `bearer` the server's API key when
  * writes are gated (an MCP call is a POST), `loopback` whether the server listens on loopback only. */
final case class ClaudeCodeEnv(mcpUrl: String, bearer: Option[String], loopback: Boolean)

object ClaudeCodeEnv:
  def of(app: AppConfig): ClaudeCodeEnv =
    val cc = app.agents.providers.claudeCode
    val url = Option(cc.mcpUrl.trim).filter(_.nonEmpty).getOrElse(app.server.publicUrl.stripSuffix("/") + "/mcp/agent")
    ClaudeCodeEnv(url, Option(app.auth.apiKey).filter(_.nonEmpty), isLoopback(app.server.host))

  /** `127.x`, `::1`, `localhost` — a bind nobody else can reach. `0.0.0.0` (every interface) is not. */
  def isLoopback(host: String): Boolean =
    val h = host.trim.toLowerCase(java.util.Locale.ROOT).stripPrefix("[").stripSuffix("]")
    h == "localhost" || h == "::1" || h.startsWith("127.")

/** The `claude-code` provider: ONE `claude -p` process per LLM step attempt (docs/research-model-providers.md).
  *
  * The step's system prompt replaces Claude Code's own (`--system-prompt-file`); the user message —
  * which carries the untrusted data — goes in on stdin; the typed answer is `--json-schema` (Claude
  * Code validates it and re-prompts on a mismatch); tools are this server's read-only agent MCP
  * endpoint, the step's allowlist the only ones offered (every other agent tool is in
  * `--disallowedTools`, built-in tools are off with `--tools ""`, `--permission-mode dontAsk` denies
  * anything else). Usage, the model and the trace come from the `stream-json` output.
  *
  * It never touches a credential: the CLI signs in through Anthropic's own flow, and a key in the
  * server's environment is REMOVED from the CLI's unless `use-api-key` (a present key always wins in
  * `-p`, and would bill the API instead of the plan). */
final class ClaudeCodeClient(val model: ModelRef, cfg: ClaudeCodeConfig, env: ClaudeCodeEnv) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val dir = Files.createTempDirectory("histmersion-claude-")
    try
      val system = Files.writeString(dir.resolve("system.md"), call.system, StandardCharsets.UTF_8)
      val offered = call.tools.map(_.name)
      val mcp = Option.when(offered.nonEmpty) {
        val f = dir.resolve("mcp.json")
        Files.writeString(f, ClaudeCode.mcpConfig(env).noSpaces, StandardCharsets.UTF_8)
        restrict(f)
        f
      }
      val argv = ClaudeCode.argv(cfg, model.model, call.maxIterations, ClaudeCode.schemaJson(call.schema),
        system.toString, mcp.map(_.toString), offered, ToolName.values.toList.map(_.wire))
      // the LLM-slot semaphore and the latency the trace shows: the interceptors see the whole process
      // as one call (the CLI's own turns are inside it). A `var`: the interceptor chain returns sttp-ai's
      // response type, and the process outcome is carried out of it here.
      @volatile var out: Option[ClaudeCode.Outcome] = None
      val ctx = LlmCallContext(ConversationHistory.withInitialPrompt(call.user), offered.nonEmpty,
        IterationInfo(1, math.max(1, call.maxIterations)))
      val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
        val o = exec(argv, dir, call.user, call.maxTokens)
        out = Some(o)
        AgentResponse(o.result.fold("")(_.text), Nil, StopReason.EndTurn, Some(o.usage), o.modelId)
      }
      val o = out.getOrElse(throw IllegalStateException("claude-code: no outcome"))
      ClaudeCode.result(o, call.user, call.schema) match
        case Right(r)  => r
        case Left(lim) => throw ModelLimitReached(model, lim.until, lim.message)
    finally
      val files = Files.walk(dir)
      try files.sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })
      finally files.close()

  /** The MCP config holds the server's API key when there is one: owner-only. */
  private def restrict(f: Path): Unit =
    try { val _ = Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")) }
    catch case _: UnsupportedOperationException => ()

  private def exec(argv: List[String], dir: Path, stdin: String, maxTokens: Int): ClaudeCode.Outcome =
    val pb = ProcessBuilder(argv.asJava).directory(dir.toFile)
    ClaudeCode.environment(cfg, maxTokens, pb.environment())
    val p = try pb.start()
      catch case e: java.io.IOException =>
        throw java.io.IOException(s"cannot run '${cfg.command}' (${e.getMessage}) — install Claude Code or set " +
          "agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND) to its absolute path")
    // stdout and stderr are drained on their own threads so a large answer can never block the CLI
    val stdout = java.util.concurrent.ConcurrentLinkedQueue[String]()
    val stderr = StringBuilder()
    val tOut = Thread.ofVirtual().start(() => p.inputReader(StandardCharsets.UTF_8).lines().forEach(l => { val _ = stdout.add(l) }))
    val tErr = Thread.ofVirtual().start(() => {
      val s = String(p.getErrorStream.readAllBytes(), StandardCharsets.UTF_8)
      stderr.synchronized { val _ = stderr.append(s.takeRight(4000)) }
    })
    try
      val w = p.outputWriter(StandardCharsets.UTF_8)
      try w.write(stdin) finally w.close()
      if !p.waitFor(math.max(1, cfg.timeoutSeconds).toLong, TimeUnit.SECONDS) then
        kill(p)
        throw java.util.concurrent.TimeoutException(s"claude -p did not finish within ${cfg.timeoutSeconds} s")
      tOut.join(); tErr.join()
      val parsed = ClaudeCode.parse(stdout.asScala.toList)
      if parsed.result.isEmpty then
        val err = stderr.synchronized(stderr.toString).trim
        throw java.io.IOException(s"claude -p exited ${p.exitValue()} without a result" +
          (if err.nonEmpty then s": ${err.takeRight(600)}" else ""))
      parsed
    catch
      case e: InterruptedException =>
        kill(p) // a cancelled run: the CLI and whatever it started go with it
        throw e
    finally if p.isAlive then kill(p)

  private def kill(p: Process): Unit =
    p.descendants().forEach(d => { val _ = d.destroy() })
    p.destroy()
    if !p.waitFor(5, TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }

/** The pure half of the `claude-code` provider: the argument vector, the environment, the MCP
  * config, and the `stream-json` output read back into sttp-ai's result types. */
object ClaudeCode:

  /** The MCP server's name as the CLI sees it; its tools are `mcp__histmersion__<tool>`. */
  val McpServer = "histmersion"
  def toolId(name: String): String = s"mcp__${McpServer}__$name"

  def mcpConfig(env: ClaudeCodeEnv): Json =
    val server = Json.obj(("type", "http".asJson) :: ("url", env.mcpUrl.asJson) ::
      env.bearer.toList.map(k => "headers" -> Json.obj("Authorization" -> s"Bearer $k".asJson))*)
    Json.obj("mcpServers" -> Json.obj(McpServer -> server))

  /** The typed answer's JSON Schema for `--json-schema`. Claude Code validates against draft-07, so
    * the 2020-12 `$schema` marker sttp-ai's renderer may add is dropped (the rest is shared). */
  def schemaJson(s: ResponseSchema[?]): String =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema")).noSpaces

  /** `claude -p …`, in a fixed order. `offered` are the step's tools (MCP names, unprefixed); `all`
    * every agent tool, so the ones not offered are removed from the model's context outright. */
  def argv(cfg: ClaudeCodeConfig, model: String, maxTurns: Int, schema: String, systemFile: String,
      mcpFile: Option[String], offered: List[String], all: List[String]): List[String] =
    val denied = all.filterNot(offered.contains)
    List(cfg.command, "-p", "--output-format", "stream-json", "--verbose", "--model", model,
      "--max-turns", math.max(1, maxTurns).toString, "--no-session-persistence",
      "--system-prompt-file", systemFile, "--json-schema", schema,
      "--tools", "", "--strict-mcp-config", "--permission-mode", "dontAsk",
      "--disable-slash-commands", "--settings", """{"disableAllHooks":true}""") ++
      mcpFile.toList.flatMap(f => List("--mcp-config", f)) ++
      (if offered.nonEmpty then List("--allowedTools", offered.map(toolId).mkString(",")) else Nil) ++
      (if mcpFile.nonEmpty && denied.nonEmpty then List("--disallowedTools", denied.map(toolId).mkString(",")) else Nil) ++
      cfg.extraArgs.trim.split("\\s+").toList.filter(_.nonEmpty)

  /** The CLI's environment: the server's, minus an Anthropic key unless asked (a key always wins in
    * `-p` and would bill the API), plus the output cap and a config dir of its own when set. */
  def environment(cfg: ClaudeCodeConfig, maxTokens: Int, env: java.util.Map[String, String]): Unit =
    if !cfg.useApiKey then
      val _ = env.remove("ANTHROPIC_API_KEY")
      val _ = env.remove("ANTHROPIC_AUTH_TOKEN")
    if cfg.configDir.trim.nonEmpty then
      val _ = env.put("CLAUDE_CONFIG_DIR", cfg.configDir.trim.replaceFirst("^~", System.getProperty("user.home")))
    val _ = env.put("CLAUDE_CODE_MAX_OUTPUT_TOKENS", math.max(1, maxTokens).toString)
    val _ = env.put("DISABLE_AUTOUPDATER", "1")

  // ── the output ─────────────────────────────────────────────────────────────────────────────────

  /** The final `result` line. `usage` follows sttp-ai's convention (input counts cached tokens too). */
  final case class Result(subtype: String, isError: Boolean, text: String, structured: Option[Json], turns: Int,
      costUsd: Option[Double], denials: List[String], errors: List[String])

  /** What one `claude -p` run printed: the conversation (assistant text, tool calls and their
    * results, in order), the result line, the usage and the model that served it. */
  final case class Outcome(entries: Vector[ConversationEntry], toolCalls: Vector[ToolCallRecord],
      result: Option[Result], usage: TokenUsage, modelId: Option[String], mcpProblem: Option[String])

  private def tokens(j: Json, keys: String*): Long =
    keys.iterator.flatMap(k => j.hcursor.downField(k).as[Long].toOption).nextOption().getOrElse(0L)

  /** `usage` of the result line (input_tokens excludes cache reads/writes on Anthropic's wire). */
  private def usageOf(u: Json): TokenUsage =
    val (in, out) = (tokens(u, "input_tokens", "inputTokens"), tokens(u, "output_tokens", "outputTokens"))
    val (cr, cw)  = (tokens(u, "cache_read_input_tokens", "cacheReadInputTokens"),
      tokens(u, "cache_creation_input_tokens", "cacheCreationInputTokens"))
    TokenUsage(Tokens(in + cr + cw), Tokens(out), Tokens(cr), Tokens.Zero, Tokens(cw))

  private def text(content: Json): String =
    content.asString.getOrElse(
      content.asArray.toList.flatten.flatMap(b => b.hcursor.downField("text").as[String].toOption).mkString("\n"))

  def parse(lines: List[String]): Outcome =
    val events = lines.map(_.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val names  = scala.collection.mutable.Map.empty[String, String] // tool_use id → tool name
    // `var`s and builders: the accumulators of ONE parse, over the events in order
    var turn = 0 // the assistant message count, for the tool call records
    val entries = Vector.newBuilder[ConversationEntry]
    val calls   = Vector.newBuilder[ToolCallRecord]
    val pending = scala.collection.mutable.LinkedHashMap.empty[String, ToolCall]
    var result: Option[Result] = None
    var usage: Option[TokenUsage] = None
    var model: Option[String] = None
    var mcpProblem: Option[String] = None
    def strip(n: String) = n.stripPrefix(s"mcp__${McpServer}__")
    events.foreach { e =>
      val c = e.hcursor
      c.downField("type").as[String].getOrElse("") match
        case "system" if c.downField("subtype").as[String].contains("init") =>
          model = c.downField("model").as[String].toOption.orElse(model)
          val servers = c.downField("mcp_servers").as[List[Json]].getOrElse(Nil)
          mcpProblem = servers.find(s => s.hcursor.downField("name").as[String].contains(McpServer))
            .flatMap(_.hcursor.downField("status").as[String].toOption).filterNot(s => s == "connected" || s == "pending")
            .map(s => s"the agent MCP server is $s")
        case "assistant" if c.downField("parent_tool_use_id").focus.forall(_.isNull) =>
          turn += 1
          val blocks = c.downField("message").downField("content").as[List[Json]].getOrElse(Nil)
          val said = blocks.filter(_.hcursor.downField("type").as[String].contains("text"))
            .flatMap(_.hcursor.downField("text").as[String].toOption).mkString("\n")
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
          c.downField("message").downField("content").as[List[Json]].getOrElse(Nil)
            .filter(_.hcursor.downField("type").as[String].contains("tool_result")).foreach { b =>
              val id  = b.hcursor.downField("tool_use_id").as[String].getOrElse("")
              val out = b.hcursor.downField("content").focus.fold("")(text)
              val nm  = names.getOrElse(id, "")
              entries += ConversationEntry.ToolResult(id, nm, out)
              pending.remove(id).foreach(tc => calls += ToolCallRecord(id, nm, tc.input, out, turn))
            }
        case "result" =>
          val modelUsage = c.downField("modelUsage").focus.flatMap(_.asObject).toList.flatMap(_.toList)
          usage = Some(
            if modelUsage.nonEmpty then modelUsage.map((_, u) => usageOf(u)).reduce(_ + _)
            else c.downField("usage").focus.fold(TokenUsage.Zero)(usageOf))
          model = modelUsage.headOption.map(_._1).orElse(model)
          result = Some(Result(
            subtype = c.downField("subtype").as[String].getOrElse(""),
            isError = c.downField("is_error").as[Boolean].getOrElse(false),
            text = c.downField("result").as[String].getOrElse(""),
            structured = c.downField("structured_output").focus.filterNot(_.isNull),
            turns = c.downField("num_turns").as[Int].getOrElse(turn),
            costUsd = c.downField("total_cost_usd").as[Double].toOption,
            denials = c.downField("permission_denials").as[List[Json]].getOrElse(Nil)
              .map(d => d.hcursor.downField("tool_name").as[String].getOrElse(d.noSpaces)),
            errors = c.downField("errors").as[List[Json]].getOrElse(Nil).map(j => j.asString.getOrElse(j.noSpaces))))
        case _ => ()
    }
    Outcome(entries.result(), calls.result(), result, usage.getOrElse(TokenUsage.Zero), model, mcpProblem)

  /** The plan's usage limit, as the CLI reports it: `… usage limit reached|<epoch seconds>` (older
    * releases) or a sentence about the limit (newer) — the reset time when the text carries one. */
  final case class Limit(message: String, until: Option[java.time.Instant])

  def usageLimit(r: Result): Option[Limit] =
    val all = (r.text :: r.errors).mkString(" ")
    val low = all.toLowerCase(java.util.Locale.ROOT)
    Option.when(r.isError && (low.contains("usage limit") || low.contains("limit reached") || low.contains("hit your limit"))) {
      val epoch = "\\|(\\d{9,11})".r.findFirstMatchIn(all).flatMap(_.group(1).toLongOption)
      Limit(s"claude -p: ${all.take(300)}", epoch.map(java.time.Instant.ofEpochSecond))
    }

  /** sttp-ai's result from what the CLI printed, or the plan's usage limit (Left). */
  def result[Out](o: Outcome, user: String, schema: ResponseSchema[Out]): Either[Limit, AgentResult[Either[AgentFailure, Out]]] =
    val r = o.result.getOrElse(Result("missing", isError = true, "", None, 0, None, Nil, Nil))
    usageLimit(r) match
      case Some(l) => Left(l)
      case None =>
        val finish = r.subtype match
          case "success" if !r.isError    => FinishReason.NaturalStop
          case "error_max_turns"          => FinishReason.MaxIterations
          case "error_max_budget_usd"     => FinishReason.BudgetExceeded
          case other =>
            FinishReason.Error((List(Option(r.text).filter(_.nonEmpty), Some(other).filter(_.nonEmpty)).flatten ++
              r.errors ++ o.mcpProblem.toList ++
              Option.when(r.denials.nonEmpty)(s"denied: ${r.denials.mkString(", ")}").toList).mkString("; ").take(600))
        val raw = r.structured.fold(r.text)(_.noSpaces)
        val answer: Either[AgentFailure, Out] = r.structured match
          case Some(j) => schema.codec.decodeJson(j).left.map(e => AgentParseError(raw, e))
          case None    => Left(AgentIncomplete(raw, finish, None))
        val entries = (ConversationEntry.UserPrompt(user) +: o.entries) ++
          Option.when(r.structured.isDefined)(ConversationEntry.AssistantResponse(raw, Nil))
        Right(AgentResult(answer, math.max(1, r.turns), o.toolCalls, finish, o.usage,
          Seq(LlmCallUsage(o.modelId, o.usage)), ConversationHistory(entries)))

  /** The `claude-code` client for `ref`, or why it cannot be used here. */
  def client(cfg: ClaudeCodeConfig, env: ClaudeCodeEnv, ref: ModelRef): Either[String, LlmClient] =
    if ref.provider != ProviderKind.ClaudeCode then Left(s"${ref.wire} is not a claude-code model")
    else if !env.loopback && !cfg.allowNonLoopback then
      Left("claude-code is refused while the server listens beyond loopback: another client could spend " +
        "your Claude plan. Bind to 127.0.0.1 (HISTMERSION_BIND), or — when every client of this server is " +
        "you — set agents.providers.claude-code.allow-non-loopback (HISTMERSION_AGENTS_CLAUDE_ALLOW_NON_LOOPBACK)")
    else if cfg.command.trim.isEmpty then Left("agents.providers.claude-code.command is empty")
    else Right(ClaudeCodeClient(ref, cfg, env))
