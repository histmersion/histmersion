package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.CodexConfig
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

object Codex:

  /** The typed answer's JSON Schema as derived (opencode reads it in the message). */
  def schemaJson(s: ResponseSchema[?]): String = schema(s).noSpaces

  /** The `--output-schema` file: the derived schema in OpenAI's strict form, the only one Codex's structured output
    * accepts ([[StrictJsonSchema]] says why and how).
    */
  def outputSchemaJson(s: ResponseSchema[?]): String = StrictJsonSchema(schema(s)).noSpaces

  private def schema(s: ResponseSchema[?]): Json =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema"))

  /** `codex exec --json -p <role> …` with the prompt on stdin (`-`). Not `--ephemeral`: the rollout keeps the run's
    * rate-limit snapshot.
    */
  def argv(
      cfg: CodexConfig,
      model: String,
      agent: String,
      schemaFile: String,
      cwd: String,
      lastFile: String
  ): List[String] =
    List(
      cfg.command,
      "exec",
      "--json",
      "-p",
      agent,
      "-m",
      model,
      "--output-schema",
      schemaFile,
      "--skip-git-repo-check",
      "-C",
      cwd,
      "-o",
      lastFile,
      "-"
    )

  /** The CLI's environment: its own CODEX_HOME (its login, never a copied `auth.json`), the run token the MCP server
    * config names, and no API key unless asked (a key bills the API).
    */
  def environment(cfg: CodexConfig, home: String, token: Option[String], env: java.util.Map[String, String]): Unit =
    if !cfg.useApiKey then
      List("OPENAI_API_KEY", "CODEX_API_KEY").foreach { k =>
        val _ = env.remove(k)
      }
    val _ = env.put("CODEX_HOME", home)
    // the base config makes Histmersion's MCP server required: a step with no tools still starts it
    val _ = env.put(AgentGenerator.TokenEnv, token.getOrElse("none"))

  /** `codex exec --json` events → the outcome: `item.completed` agent messages (the last is the answer), MCP tool calls
    * with their results, reasoning; `turn.completed.usage`; `turn.failed` and `error` messages.
    */
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
              val id = it.downField("id").as[String].getOrElse("")
              val tool = it.downField("tool").as[String].getOrElse("")
              val args = it.downField("arguments").focus.fold("{}")(_.noSpaces)
              val res = it
                .downField("result")
                .downField("content")
                .as[List[Json]]
                .getOrElse(Nil)
                .flatMap(_.hcursor.downField("text").as[String].toOption)
                .mkString("\n")
              val out = it.downField("error").downField("message").as[String].toOption.fold(res)(m => s"error: $m")
              entries += ConversationEntry.AssistantResponse("", List(ToolCall(id, tool, args)))
              entries += ConversationEntry.ToolResult(id, tool, out)
              calls += ToolCallRecord(id, tool, args, out, math.max(1, turns))
            case _ => ()
        case "turn.completed" =>
          val u = c.downField("usage")
          val l = (k: String) => u.downField(k).as[Long].getOrElse(0L)
          usage = usage + TokenUsage(
            Tokens(l("input_tokens")),
            Tokens(l("output_tokens")),
            Tokens(l("cached_input_tokens")),
            Tokens.Zero,
            Tokens(l("cache_write_input_tokens"))
          )
        case "turn.failed" =>
          error = c.downField("error").downField("message").as[String].toOption.orElse(Some("turn failed"))
        case "error" => error = error.orElse(c.downField("message").as[String].toOption)
        case _       => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, turns)

  def client(cfg: CodexConfig, rt: CliRuntime, ref: ModelRef): Either[String, LlmClient] =
    if ref.provider != ProviderKind.Codex then Left(s"${ref.wire} is not a codex model")
    else if !rt.loopback then
      Left("codex is refused while the server listens beyond loopback: another client could spend your ChatGPT plan")
    else if cfg.command.trim.isEmpty then Left("agents.providers.codex.command is empty")
    else Right(CodexExecClient(ref, cfg, rt))
