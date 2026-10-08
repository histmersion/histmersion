package com.histmersion.server.agents

import com.histmersion.core.{CodexConfig, OpencodeConfig}
import com.histmersion.core.Agents.{ModelRef, ProviderKind}
import com.histmersion.core.PlanQuota
import io.circe.{Json, parser}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** What a CLI run printed, read into sttp-ai's result types: the conversation (text, tool calls and
  * their results, in order), the final text, usage, and an error or the plan's limit if it ended on one. */
final case class CliOutcome(entries: Vector[ConversationEntry], toolCalls: Vector[ToolCallRecord], finalText: Option[String],
    usage: TokenUsage, error: Option[String], turns: Int)

object CliOutcomes:

  /** The answer as the step's schema: the final text is JSON (code fences tolerated), a `null` field
    * the same as an absent one. */
  def result[Out](o: CliOutcome, user: String, schema: ResponseSchema[Out], modelId: Option[String]): AgentResult[Either[AgentFailure, Out]] =
    val raw = o.finalText.getOrElse("")
    val json = parser.parse(stripFence(raw)).toOption
    val finish = o.error.fold(FinishReason.NaturalStop)(e => FinishReason.Error(e.take(600)))
    val answer: Either[AgentFailure, Out] = json match
      // a null decodes as an absent field: Codex's strict schema makes the model answer `null` for
      // every optional field it would have left out (StrictJsonSchema), and a field with a default
      // (`tags: List[String] = Nil`) would otherwise refuse it
      case Some(j) => schema.codec.decodeJson(j.deepDropNullValues).left.map(e => AgentParseError(raw, e))
      case None    => Left(AgentIncomplete(raw, finish, None))
    val entries = ConversationEntry.UserPrompt(user) +: o.entries
    AgentResult(answer, math.max(1, o.turns), o.toolCalls, finish, o.usage, Seq(LlmCallUsage(modelId, o.usage)),
      ConversationHistory(entries))

  def stripFence(s: String): String =
    val t = s.trim
    if t.startsWith("```") then t.dropWhile(_ != '\n').drop(1).reverse.dropWhile(_ != '`').dropWhile(_ == '`').reverse.trim
    else t

  /** A plan's usage limit as a CLI words it ("You've hit your usage limit", "rate limit reached"). */
  def isLimit(message: String): Boolean =
    val low = message.toLowerCase(java.util.Locale.ROOT)
    low.contains("usage limit") || low.contains("rate limit reached") || low.contains("rate_limit_reached") ||
      low.contains("hit your limit") || low.contains("quota exceeded")

/** The `codex` provider: `codex exec --json -p <role>` per step attempt, on the GENERATED Codex home
  * (docs/research-agent-runtimes.md §2.4): the role's profile carries its prompt (includes and skills
  * inlined — with the shell off the model could not open a listed skill) and narrows Histmersion's MCP
  * server to the role's tools; the base config switches the shell, web search, images, apps, plugins,
  * memories and sub-agents off, read-only sandbox, no approvals. The empty working directory keeps any
  * project `AGENTS.md` and `.codex/` out; the run token decides the tools the endpoint serves.
  * After each step the ChatGPT plan pool is read from the app-server (throttled). */
final class CodexExecClient(val model: ModelRef, cfg: CodexConfig, rt: CliRuntime) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val agent = call.agent.getOrElse(throw java.io.IOException("codex: a step must run as an agent definition"))
    val home = rt.trees.codexHome.getOrElse(throw java.io.IOException("codex: no CODEX_HOME — set agents.runtimes.dir or agents.providers.codex.home"))
    if !Files.isRegularFile(home.resolve(s"${agent.name}.config.toml")) then
      throw java.io.IOException(s"codex: no profile for ${agent.name} in $home — run `histmersion agent generate`")
    CliProcess.withDir("histmersion-codex-") { dir =>
      val grant = rt.grant(call, Some(agent), cfg.timeoutSeconds)
      try
        val schema = CliProcess.privateFile(dir.resolve("schema.json"), Codex.outputSchemaJson(call.schema))
        val work = Files.createDirectories(dir.resolve("work"))
        val last = dir.resolve("last.txt")
        val argv = Codex.argv(cfg, model.model, agent.name, schema.toString, work.toString, last.toString)
        val out = java.util.concurrent.atomic.AtomicReference[Option[CliOutcome]](None)
        val ctx = LlmCallContext(ConversationHistory.withInitialPrompt(call.user), call.tools.nonEmpty,
          IterationInfo(1, math.max(1, call.maxIterations)))
        val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
          val r = CliProcess.run(argv, work, env => Codex.environment(cfg, home.toString, grant.map(_.token), env), call.user,
            cfg.timeoutSeconds, "install the Codex CLI or set agents.providers.codex.command (HISTMERSION_AGENTS_CODEX_COMMAND)")
          val o = Codex.parse(r.lines)
          val lastText = Option.when(Files.isRegularFile(last))(Files.readString(last, StandardCharsets.UTF_8)).filter(_.trim.nonEmpty)
          val withLast = o.copy(finalText = lastText.orElse(o.finalText))
          out.set(Some(withLast))
          if withLast.finalText.isEmpty && withLast.error.isEmpty then
            throw java.io.IOException(s"codex exec exited ${r.exit} without an answer" +
              (if r.stderr.nonEmpty then s": ${r.stderr.takeRight(600)}" else ""))
          AgentResponse(withLast.finalText.getOrElse(""), Nil, StopReason.EndTurn, Some(withLast.usage), Some(model.model))
        }
        val o = out.get().getOrElse(throw IllegalStateException("codex: no outcome"))
        rt.pools.foreach(_.refresh(System.currentTimeMillis()))
        o.error.filter(CliOutcomes.isLimit) match
          case Some(m) =>
            val until = rt.pools.flatMap(_.blocked(PlanQuota.PoolId.ChatgptPlan, Some(model.model)))
              .flatMap(_._2).map(java.time.Instant.ofEpochMilli)
            throw ModelLimitReached(model, until, s"codex exec: $m".take(300))
          case None => CliOutcomes.result(o, call.user, call.schema, Some(model.model))
      finally grant.foreach(g => rt.tokens.revoke(g.token))
    }

object Codex:

  /** The typed answer's JSON Schema as derived (opencode reads it in the message). */
  def schemaJson(s: ResponseSchema[?]): String = schema(s).noSpaces

  /** The `--output-schema` file: the derived schema in OpenAI's strict form, the only one Codex's
    * structured output accepts ([[StrictJsonSchema]] says why and how). */
  def outputSchemaJson(s: ResponseSchema[?]): String = StrictJsonSchema(schema(s)).noSpaces

  private def schema(s: ResponseSchema[?]): Json =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema"))

  /** `codex exec --json -p <role> …` with the prompt on stdin (`-`). Not `--ephemeral`: the rollout
    * keeps the run's rate-limit snapshot. */
  def argv(cfg: CodexConfig, model: String, agent: String, schemaFile: String, cwd: String, lastFile: String): List[String] =
    List(cfg.command, "exec", "--json", "-p", agent, "-m", model, "--output-schema", schemaFile,
      "--skip-git-repo-check", "-C", cwd, "-o", lastFile, "-")

  /** The CLI's environment: its own CODEX_HOME (its login, never a copied `auth.json`), the run token
    * the MCP server config names, and no API key unless asked (a key bills the API). */
  def environment(cfg: CodexConfig, home: String, token: Option[String], env: java.util.Map[String, String]): Unit =
    if !cfg.useApiKey then List("OPENAI_API_KEY", "CODEX_API_KEY").foreach(k => { val _ = env.remove(k) })
    val _ = env.put("CODEX_HOME", home)
    // the base config makes Histmersion's MCP server required: a step with no tools still starts it
    val _ = env.put(AgentGenerator.TokenEnv, token.getOrElse("none"))

  /** `codex exec --json` events → the outcome: `item.completed` agent messages (the last is the
    * answer), MCP tool calls with their results, reasoning; `turn.completed.usage`; `turn.failed` and
    * `error` messages. */
  def parse(lines: List[String]): CliOutcome =
    val events = lines.map(_.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    // accumulators of ONE parse, over the events in order
    var finalText: Option[String] = None
    var usage = TokenUsage.Zero
    var error: Option[String] = None
    var turns = 0
    events.foreach { e =>
      val c = e.hcursor
      c.downField("type").as[String].getOrElse("") match
        case "item.completed" =>
          val it = c.downField("item")
          it.downField("type").as[String].getOrElse("") match
            case "agent_message" =>
              val t = it.downField("text").as[String].getOrElse("")
              turns += 1
              finalText = Some(t)
              entries += ConversationEntry.AssistantResponse(t, Nil)
            case "mcp_tool_call" =>
              val id   = it.downField("id").as[String].getOrElse("")
              val tool = it.downField("tool").as[String].getOrElse("")
              val args = it.downField("arguments").focus.fold("{}")(_.noSpaces)
              val res  = it.downField("result").downField("content").as[List[Json]].getOrElse(Nil)
                .flatMap(_.hcursor.downField("text").as[String].toOption).mkString("\n")
              val out  = it.downField("error").downField("message").as[String].toOption.fold(res)(m => s"error: $m")
              entries += ConversationEntry.AssistantResponse("", List(ToolCall(id, tool, args)))
              entries += ConversationEntry.ToolResult(id, tool, out)
              calls += ToolCallRecord(id, tool, args, out, math.max(1, turns))
            case _ => ()
        case "turn.completed" =>
          val u = c.downField("usage")
          val l = (k: String) => u.downField(k).as[Long].getOrElse(0L)
          usage = usage + TokenUsage(Tokens(l("input_tokens")), Tokens(l("output_tokens")), Tokens(l("cached_input_tokens")),
            Tokens.Zero, Tokens(l("cache_write_input_tokens")))
        case "turn.failed" => error = c.downField("error").downField("message").as[String].toOption.orElse(Some("turn failed"))
        case "error"       => error = error.orElse(c.downField("message").as[String].toOption)
        case _ => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, turns)

  def client(cfg: CodexConfig, rt: CliRuntime, ref: ModelRef): Either[String, LlmClient] =
    if ref.provider != ProviderKind.Codex then Left(s"${ref.wire} is not a codex model")
    else if !rt.loopback then Left("codex is refused while the server listens beyond loopback: another client could spend your ChatGPT plan")
    else if cfg.command.trim.isEmpty then Left("agents.providers.codex.command is empty")
    else Right(CodexExecClient(ref, cfg, rt))

/** The `opencode` provider: `opencode run --agent <role> --format json -m <provider/model>` on the
  * GENERATED config (`XDG_CONFIG_HOME` and `OPENCODE_CONFIG_DIR` pointed at it, the Claude Code
  * folders and autoupdate off, an empty working directory): the role's agent denies every tool but its
  * own. opencode has no schema flag, so the schema travels in the message and the final text is
  * parsed as JSON (the validators send a miss back, as for every provider). */
final class OpencodeRunClient(val model: ModelRef, cfg: OpencodeConfig, rt: CliRuntime) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val agent = call.agent.getOrElse(throw java.io.IOException("opencode: a step must run as an agent definition"))
    val (xdg, conf) = (for x <- rt.trees.opencodeXdg; c <- rt.trees.opencodeConfig yield (x, c))
      .filter(_._2.resolve("agents").resolve(s"${agent.name}.md").toFile.isFile)
      .getOrElse(throw java.io.IOException(s"opencode: no generated agent ${agent.name} — run `histmersion agent generate`"))
    CliProcess.withDir("histmersion-opencode-") { dir =>
      val grant = rt.grant(call, Some(agent), cfg.timeoutSeconds)
      try
        val argv = Opencode.argv(cfg, model.model, agent.name, dir.toString)
        val user = call.user + Opencode.schemaNote(Codex.schemaJson(call.schema))
        val out = java.util.concurrent.atomic.AtomicReference[Option[CliOutcome]](None)
        val ctx = LlmCallContext(ConversationHistory.withInitialPrompt(call.user), call.tools.nonEmpty,
          IterationInfo(1, math.max(1, call.maxIterations)))
        val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
          val r = CliProcess.run(argv, dir, env => Opencode.environment(xdg.toString, conf.toString, grant.map(_.token), env), user,
            cfg.timeoutSeconds, "install opencode or set agents.providers.opencode.command (HISTMERSION_AGENTS_OPENCODE_COMMAND)")
          val o = Opencode.parse(r.lines)
          out.set(Some(o))
          if o.finalText.isEmpty && o.error.isEmpty then
            throw java.io.IOException(s"opencode run exited ${r.exit} without an answer" +
              (if r.stderr.nonEmpty then s": ${r.stderr.takeRight(600)}" else ""))
          AgentResponse(o.finalText.getOrElse(""), Nil, StopReason.EndTurn, Some(o.usage), Some(model.model))
        }
        val o = out.get().getOrElse(throw IllegalStateException("opencode: no outcome"))
        o.error.filter(CliOutcomes.isLimit) match
          case Some(m) => throw ModelLimitReached(model, None, s"opencode run: $m".take(300))
          case None    => CliOutcomes.result(o, call.user, call.schema, Some(model.model))
      finally grant.foreach(g => rt.tokens.revoke(g.token))
    }

object Opencode:

  def argv(cfg: OpencodeConfig, model: String, agent: String, dir: String): List[String] =
    List(cfg.command, "run", "--agent", agent, "--format", "json", "-m", model, "--dir", dir)

  /** Isolation (docs/research-agent-runtimes.md §1.3): a global config of its own, the generated
    * config dir, no `.claude` skills, no autoupdate; the run token for the MCP header. */
  def environment(xdg: String, configDir: String, token: Option[String], env: java.util.Map[String, String]): Unit =
    val _ = env.put("XDG_CONFIG_HOME", xdg)
    val _ = env.put("OPENCODE_CONFIG_DIR", configDir)
    val _ = env.put("OPENCODE_DISABLE_CLAUDE_CODE", "1")
    val _ = env.put("OPENCODE_DISABLE_AUTOUPDATE", "1")
    val _ = env.put(AgentGenerator.TokenEnv, token.getOrElse("none"))

  def schemaNote(schema: String): String =
    s"\n\nReply with ONE JSON object that matches this JSON Schema, and nothing else:\n$schema"

  /** `--format json` events: `text` parts (the last is the answer), `tool_use` (a tool part with its
    * state: input, output or error), `step_finish` (tokens, cost), `error`. */
  def parse(lines: List[String]): CliOutcome =
    val events = lines.map(_.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    // accumulators of ONE parse, over the events in order
    var finalText: Option[String] = None
    var usage = TokenUsage.Zero
    var error: Option[String] = None
    var turns = 0
    def strip(t: String) = t.stripPrefix(s"${AgentDefinitions.McpServer}_")
    events.foreach { e =>
      val c = e.hcursor
      val part = c.downField("part")
      c.downField("type").as[String].getOrElse("") match
        case "text" =>
          val t = part.downField("text").as[String].getOrElse("")
          finalText = Some(t)
          entries += ConversationEntry.AssistantResponse(t, Nil)
        case "tool_use" =>
          val id    = part.downField("callID").as[String].orElse(part.downField("id").as[String]).getOrElse("")
          val tool  = strip(part.downField("tool").as[String].getOrElse(""))
          val state = part.downField("state")
          val input = state.downField("input").focus.fold("{}")(_.noSpaces)
          val out   = state.downField("output").as[String].toOption
            .orElse(state.downField("error").as[String].toOption.map(m => s"error: $m")).getOrElse("")
          entries += ConversationEntry.AssistantResponse("", List(ToolCall(id, tool, input)))
          entries += ConversationEntry.ToolResult(id, tool, out)
          calls += ToolCallRecord(id, tool, input, out, math.max(1, turns))
        case "step_finish" =>
          turns += 1
          val t = part.downField("tokens")
          val l = (cur: io.circe.ACursor) => cur.as[Long].getOrElse(0L)
          val (in, out) = (l(t.downField("input")), l(t.downField("output")) + l(t.downField("reasoning")))
          val (cr, cw)  = (l(t.downField("cache").downField("read")), l(t.downField("cache").downField("write")))
          usage = usage + TokenUsage(Tokens(in + cr + cw), Tokens(out), Tokens(cr), Tokens.Zero, Tokens(cw))
        case "error" =>
          val err = c.downField("error")
          error = err.downField("data").downField("message").as[String].toOption
            .orElse(err.downField("message").as[String].toOption).orElse(err.focus.map(_.noSpaces)).orElse(Some("error"))
        case _ => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, turns)

  def client(cfg: OpencodeConfig, rt: CliRuntime, ref: ModelRef): Either[String, LlmClient] =
    if ref.provider != ProviderKind.Opencode then Left(s"${ref.wire} is not an opencode model")
    else if !ref.model.contains('/') then Left(s"${ref.wire}: an opencode model is <provider>/<model>, e.g. opencode:openai/gpt-5")
    else if PlanQuota.poolOf(ref, PlanQuota.Logins(opencodeChatgpt = cfg.chatgptLogin)).isDefined && !rt.loopback then
      Left("opencode on a ChatGPT login is refused while the server listens beyond loopback")
    else if cfg.command.trim.isEmpty then Left("agents.providers.opencode.command is empty")
    else Right(OpencodeRunClient(ref, cfg, rt))
