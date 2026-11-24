package com.histmersion
package server.agents
import com.histmersion.util.json.RawJson
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.OpencodeConfig
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.automation.PlanQuota
import java.nio.file.Path
import sttp.ai.core.agent.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, FeaturePlace, atBoundary, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
@atBoundary(BoundaryKind.Process, "builds the opencode CLI arguments and reads its output")
@movesTo(FeaturePlace.AutomationAgents)
object Opencode:
  type SchemaNote = SchemaNote.T
  object SchemaNote extends Newtype[String, Pure]

  /** Who reports a failed run in its own words. */
  private val Said = Problem.Upstream.Name("opencode run")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def argv(cfg: OpencodeConfig, model: ModelRef.Model, agent: AgentDef.Name, dir: Path): List[ClaudeCode.Argument] =
    List(cfg.command.value, "run", "--agent", agent.value, "--format", "json", "-m", model.value, "--dir", dir.toString)
      .map(ClaudeCode.Argument(_))

  /** Isolation (docs/research-agent-runtimes.md §1.3): a global config of its own, the generated config dir, no
    * `.claude` skills, no autoupdate; the run token for the MCP header.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def environment(xdg: Path, configDir: Path, token: Option[RunGrant.Token], env: java.util.Map[String, String]): Unit =
    val _ = env.put("XDG_CONFIG_HOME", xdg.toString)
    val _ = env.put("OPENCODE_CONFIG_DIR", configDir.toString)
    val _ = env.put("OPENCODE_DISABLE_CLAUDE_CODE", "1")
    val _ = env.put("OPENCODE_DISABLE_AUTOUPDATE", "1")
    val _ = env.put(AgentGenerator.TokenEnv, token.fold("none")(_.value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def schemaNote(schema: Codex.SchemaJson): SchemaNote =
    SchemaNote(s"\n\nReply with ONE JSON object that matches this JSON Schema, and nothing else:\n$schema")

  /** `--format json` events: `text` parts (the last is the answer), `tool_use` (a tool part with its state: input,
    * output or error), `step_finish` (tokens, cost), `error`.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def parse(lines: List[CliProcess.Run.Lines]): CliOutcome =
    val events = lines.map(_.value.trim).filter(_.startsWith("{")).flatMap(l => RawJson.parse(l).toOption)
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    // accumulators of ONE parse, over the events in order
    var finalText: Option[CliOutcome.FinalText] = None
    var usage = TokenUsage.Zero
    var error: Option[Problem] = None
    var turns = 0
    def strip(t: String) = t.stripPrefix(s"${AgentDefinitions.McpServer}_")
    events.foreach { e =>
      val c = e.cursor
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
          val l = (cur: Cursor) => cur.as[Long].getOrElse(0L)
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
            .map(text => Problem.Upstream.Replied(Said, External.Reported.of(text)))
        case _ => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, CliOutcome.Turns(turns))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def client(cfg: OpencodeConfig, rt: CliRuntime, ref: ModelRef): Either[Problem, LlmClient] =
    if ref.provider != ProviderKind.Opencode then
      Left(
        Problem.Invalid.Broken.here(
          Problem.Invalid.Rule.ProviderOf(Problem.Raw.of(ref.wire), Problem.Raw.of(ProviderKind.Opencode.wire))
        )
      )
    else if !ref.model.value.contains('/') then
      Left(Problem.Invalid.Broken.here(Problem.Invalid.Rule.OpencodeModelShape(Problem.Raw.of(ref.wire))))
    else if PlanQuota
        .poolOf(ref, PlanQuota.Logins(opencodeChatgpt = PlanQuota.Logins.OpencodeChatgpt(cfg.chatgptLogin.value)))
        .isDefined && !rt.loopback
    then Left(Problem.Refused.Policy(Problem.Refused.Rule.OpencodeChatgptOutsideLocal))
    else if cfg.command.value.trim.isEmpty then
      Left(Problem.Config.Setting(Problem.SettingKey("agents.providers.opencode.command"), Problem.Config.Fault.Empty))
    else Right(OpencodeRunClient(ref, cfg, rt))
