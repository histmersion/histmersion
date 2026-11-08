package com.histmersion
package server.agents

import com.histmersion.api.Problem
import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.OpencodeConfig
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.automation.PlanQuota
import java.nio.file.Path
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*
import io.github.iltotore.iron.constraint.any.Pure

object Opencode:
  type SchemaNote = SchemaNote.T
  object SchemaNote extends Newtype[String, Pure]

  def argv(cfg: OpencodeConfig, model: ModelRef.Model, agent: AgentDef.Name, dir: Path): List[ClaudeCode.Argument] =
    List(cfg.command.value, "run", "--agent", agent.value, "--format", "json", "-m", model.value, "--dir", dir.toString)
      .map(ClaudeCode.Argument(_))

  /** Isolation (docs/research-agent-runtimes.md §1.3): a global config of its own, the generated config dir, no
    * `.claude` skills, no autoupdate; the run token for the MCP header.
    */
  def environment(xdg: Path, configDir: Path, token: Option[RunGrant.Token], env: java.util.Map[String, String]): Unit =
    val _ = env.put("XDG_CONFIG_HOME", xdg.toString)
    val _ = env.put("OPENCODE_CONFIG_DIR", configDir.toString)
    val _ = env.put("OPENCODE_DISABLE_CLAUDE_CODE", "1")
    val _ = env.put("OPENCODE_DISABLE_AUTOUPDATE", "1")
    val _ = env.put(AgentGenerator.TokenEnv, token.fold("none")(_.value))

  def schemaNote(schema: Codex.SchemaJson): SchemaNote =
    SchemaNote(s"\n\nReply with ONE JSON object that matches this JSON Schema, and nothing else:\n$schema")

  /** `--format json` events: `text` parts (the last is the answer), `tool_use` (a tool part with its state: input,
    * output or error), `step_finish` (tokens, cost), `error`.
    */
  def parse(lines: List[CliProcess.Run.Lines]): CliOutcome =
    val events = lines.map(_.value.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    // accumulators of ONE parse, over the events in order
    var finalText: Option[CliOutcome.FinalText] = None
    var usage = TokenUsage.Zero
    var error: Option[Problem] = None
    var turns = 0
    def strip(t: String) = t.stripPrefix(s"${AgentDefinitions.McpServer}_")
    events.foreach { e =>
      val c = e.hcursor
      val part = c.downField("part")
      c.downField("type").as[String].getOrElse("") match
        case "text" =>
          val t = part.downField("text").as[String].getOrElse("")
          finalText = Some(CliOutcome.FinalText(t))
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
            .map(Problem(_))
        case _ => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, CliOutcome.Turns(turns))

  def client(cfg: OpencodeConfig, rt: CliRuntime, ref: ModelRef): Either[Problem, LlmClient] =
    if ref.provider != ProviderKind.Opencode then Left(Problem(s"${ref.wire} is not an opencode model"))
    else if !ref.model.value.contains('/') then
      Left(Problem(s"${ref.wire}: an opencode model is <provider>/<model>, e.g. opencode:openai/gpt-5"))
    else if PlanQuota
        .poolOf(ref, PlanQuota.Logins(opencodeChatgpt = PlanQuota.Logins.OpencodeChatgpt(cfg.chatgptLogin.value)))
        .isDefined && !rt.loopback
    then Left(Problem("opencode on a ChatGPT login is refused while the server listens beyond loopback"))
    else if cfg.command.value.trim.isEmpty then Left(Problem("agents.providers.opencode.command is empty"))
    else Right(OpencodeRunClient(ref, cfg, rt))
