package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.OpencodeConfig
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.automation.PlanQuota
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

object Opencode:

  type ParseFinalText = ParseFinalText.T
  object ParseFinalText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseError = ParseError.T
  object ParseError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseStripT = ParseStripT.T
  object ParseStripT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ArgvResult = ArgvResult.T
  object ArgvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvModel = ArgvModel.T
  object ArgvModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvAgent = ArgvAgent.T
  object ArgvAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvDir = ArgvDir.T
  object ArgvDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentXdg = EnvironmentXdg.T
  object EnvironmentXdg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentConfigDir = EnvironmentConfigDir.T
  object EnvironmentConfigDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentToken = EnvironmentToken.T
  object EnvironmentToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentEnvPart1 = EnvironmentEnvPart1.T
  object EnvironmentEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentEnvPart2 = EnvironmentEnvPart2.T
  object EnvironmentEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchemaNoteResult = SchemaNoteResult.T
  object SchemaNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchemaNoteSchema = SchemaNoteSchema.T
  object SchemaNoteSchema extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseLines = ParseLines.T
  object ParseLines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClientResultPart1 = ClientResultPart1.T
  object ClientResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def argv(cfg: OpencodeConfig, model: com.histmersion.server.agents.Opencode.ArgvModel, agent: com.histmersion.server.agents.Opencode.ArgvAgent, dir: com.histmersion.server.agents.Opencode.ArgvDir): List[com.histmersion.server.agents.Opencode.ArgvResult] =
    List(cfg.command, "run", "--agent", agent, "--format", "json", "-m", model, "--dir", dir)

  /** Isolation (docs/research-agent-runtimes.md §1.3): a global config of its own, the generated config dir, no
    * `.claude` skills, no autoupdate; the run token for the MCP header.
    */
  def environment(xdg: com.histmersion.server.agents.Opencode.EnvironmentXdg, configDir: com.histmersion.server.agents.Opencode.EnvironmentConfigDir, token: Option[com.histmersion.server.agents.Opencode.EnvironmentToken], env: java.util.Map[com.histmersion.server.agents.Opencode.EnvironmentEnvPart1, com.histmersion.server.agents.Opencode.EnvironmentEnvPart2]): Unit =
    val _ = env.put("XDG_CONFIG_HOME", xdg)
    val _ = env.put("OPENCODE_CONFIG_DIR", configDir)
    val _ = env.put("OPENCODE_DISABLE_CLAUDE_CODE", "1")
    val _ = env.put("OPENCODE_DISABLE_AUTOUPDATE", "1")
    val _ = env.put(AgentGenerator.TokenEnv, token.getOrElse("none"))

  def schemaNote(schema: com.histmersion.server.agents.Opencode.SchemaNoteSchema): com.histmersion.server.agents.Opencode.SchemaNoteResult =
    s"\n\nReply with ONE JSON object that matches this JSON Schema, and nothing else:\n$schema"

  /** `--format json` events: `text` parts (the last is the answer), `tool_use` (a tool part with its state: input,
    * output or error), `step_finish` (tokens, cost), `error`.
    */
  def parse(lines: List[com.histmersion.server.agents.Opencode.ParseLines]): CliOutcome =
    val events = lines.map(_.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    // accumulators of ONE parse, over the events in order
    var finalText: Option[com.histmersion.server.agents.Opencode.ParseFinalText] = None
    var usage = TokenUsage.Zero
    var error: Option[com.histmersion.server.agents.Opencode.ParseError] = None
    var turns = 0
    def strip(t: com.histmersion.server.agents.Opencode.ParseStripT) = t.stripPrefix(s"${AgentDefinitions.McpServer}_")
    events.foreach { e =>
      val c = e.hcursor
      val part = c.downField("part")
      c.downField("type").as[String].getOrElse("") match
        case "text" =>
          val t = part.downField("text").as[String].getOrElse("")
          finalText = Some(t)
          entries += ConversationEntry.AssistantResponse(t, Nil)
        case "tool_use" =>
          val id = part.downField("callID").as[String].orElse(part.downField("id").as[String]).getOrElse("")
          val tool = strip(part.downField("tool").as[String].getOrElse(""))
          val state = part.downField("state")
          val input = state.downField("input").focus.fold("{}")(_.noSpaces)
          val out = state
            .downField("output")
            .as[String]
            .toOption
            .orElse(state.downField("error").as[String].toOption.map(m => s"error: $m"))
            .getOrElse("")
          entries += ConversationEntry.AssistantResponse("", List(ToolCall(id, tool, input)))
          entries += ConversationEntry.ToolResult(id, tool, out)
          calls += ToolCallRecord(id, tool, input, out, math.max(1, turns))
        case "step_finish" =>
          turns += 1
          val t = part.downField("tokens")
          val l = (cur: io.circe.ACursor) => cur.as[Long].getOrElse(0L)
          val (in, out) = (l(t.downField("input")), l(t.downField("output")) + l(t.downField("reasoning")))
          val (cr, cw) = (l(t.downField("cache").downField("read")), l(t.downField("cache").downField("write")))
          usage = usage + TokenUsage(Tokens(in + cr + cw), Tokens(out), Tokens(cr), Tokens.Zero, Tokens(cw))
        case "error" =>
          val err = c.downField("error")
          error = err
            .downField("data")
            .downField("message")
            .as[String]
            .toOption
            .orElse(err.downField("message").as[String].toOption)
            .orElse(err.focus.map(_.noSpaces))
            .orElse(Some("error"))
        case _ => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, turns)

  def client(cfg: OpencodeConfig, rt: CliRuntime, ref: ModelRef): Either[com.histmersion.server.agents.Opencode.ClientResultPart1, LlmClient] =
    if ref.provider != ProviderKind.Opencode then Left(s"${ref.wire} is not an opencode model")
    else if !ref.model.contains('/') then
      Left(s"${ref.wire}: an opencode model is <provider>/<model>, e.g. opencode:openai/gpt-5")
    else if PlanQuota.poolOf(ref, PlanQuota.Logins(opencodeChatgpt = cfg.chatgptLogin)).isDefined && !rt.loopback then
      Left("opencode on a ChatGPT login is refused while the server listens beyond loopback")
    else if cfg.command.trim.isEmpty then Left("agents.providers.opencode.command is empty")
    else Right(OpencodeRunClient(ref, cfg, rt))
