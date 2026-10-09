package com.histmersion.server.agents

import io.circe.Json
import io.circe.syntax.*
import sttp.ai.core.agent.ResponseSchema

import java.nio.file.{Files, Path}

/** What every turn of an agent step re-sends before the model reads the user message: the role's system
  * prompt, its MCP tool definitions (as `/mcp/agent/<role>` lists them) and the typed answer's schema
  * (Claude Code's `StructuredOutput` tool). Pure and offline:
  *
  *   sbt "server/Test/runMain com.histmersion.server.agents.PromptBudget"            the table
  *   sbt "server/Test/runMain com.histmersion.server.agents.PromptBudget export <dir>"
  *
  * `export` writes what `scripts/agents/claude-overhead.sh --from <dir>` launches: the generated Claude
  * Code tree (`claude-code/`), each role's MCP tool list (`mcp-tools/<agent>.json`, the `tools/list`
  * result) and its answer schema (`answer-schemas/<agent>.json`, the `--json-schema` text) — so the
  * harness measures the whole first call, the role's MCP tools included.
  *
  * Tokens are an estimate: characters / 2.7, the ratio the live first-call counts of the review panel
  * show (the skeptic's and the cold reader's prompts differ by ~27,100 characters and ~9,990 tokens). */
object PromptBudget:

  final case class Row(agent: String, system: Int, tools: Map[String, Int], answer: Int):
    def toolChars: Int = tools.values.sum
    def total: Int     = system + toolChars + answer

  val CharsPerToken = 2.7
  def tokens(chars: Int): Int = math.round(chars / CharsPerToken).toInt

  /** The answer schema each agent definition's steps use (a reviewer's verification pass: `recheck`). */
  val answers: Map[String, ResponseSchema[?]] = Map(
    "probe" -> AnswerSchemas.probe, "enrich-classify" -> AnswerSchemas.classification,
    "enrich-findings" -> AnswerSchemas.findings, "enrich-notes" -> AnswerSchemas.notes,
    "compile-extractor" -> AnswerSchemas.extract, "compile-writer" -> AnswerSchemas.draft,
    "article-rules-reviewer" -> AnswerSchemas.rules, "article-cold-reader" -> AnswerSchemas.coldReader,
    "article-skeptic" -> AnswerSchemas.skeptic, "article-fixer" -> AnswerSchemas.fixer,
    "goal-planner" -> AnswerSchemas.plan, "acquisition-ranker" -> AnswerSchemas.ranking)

  private lazy val toolbox = Toolbox(null, () => AgentUpstream.Offline)

  /** One tool as the agent MCP endpoint lists it (`tools/list`). */
  def toolJson(t: ToolName): Json =
    val at = toolbox.tool(t)
    Json.obj("name" -> at.name.asJson, "description" -> at.description.asJson, "inputSchema" -> Toolbox.inputSchema(at))

  def toolChars(t: ToolName): Int = toolJson(t).noSpaces.length

  def rows(d: AgentGenerator.Defs): List[Row] =
    d.agents.sortBy(_.name).map { a =>
      Row(a.name, d.system(a, ToolStyle.Claude).length, a.tools.map(t => t.wire -> toolChars(t)).toMap,
        answers.get(a.name).fold(0)(s => ClaudeCode.schemaJson(s).length))
    }

  def table(rows: List[Row]): String =
    val head = "| agent | system | ~tok | tools | ~tok | answer | ~tok | total | ~tok |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|"
    (head :: rows.map(r =>
      s"| ${r.agent} | ${r.system} | ${tokens(r.system)} | ${r.toolChars} | ${tokens(r.toolChars)} | ${r.answer} | " +
        s"${tokens(r.answer)} | ${r.total} | ${tokens(r.total)} |")).mkString("\n")

  def toolTable: String =
    "| tool | chars | ~tok |\n|---|---:|---:|\n" +
      ToolName.values.map(t => s"| ${t.wire} | ${toolChars(t)} | ${tokens(toolChars(t))} |").mkString("\n")

  /** The files `export` writes, relative to its directory. */
  def exportTree(d: AgentGenerator.Defs): Map[String, String] =
    val settings = GenSettings("http://127.0.0.1:9/mcp/agent", None, "http://127.0.0.1:9/quota", claudeMod = false,
      interactiveMod = false, AgentGenerator.PluginVersion)
    val runs = d.agents.filter(_.runsOn(AgentRuntime.ClaudeCode))
    AgentGenerator.claudeCode(d, settings) ++
      runs.map(a => s"mcp-tools/${a.name}.json" -> Json.obj("tools" -> a.tools.map(toolJson).asJson).spaces2) ++
      runs.flatMap(a => answers.get(a.name).map(s => s"answer-schemas/${a.name}.json" -> ClaudeCode.schemaJson(s)))

  def main(args: Array[String]): Unit =
    val d = Generated.defs()
    args.toList match
      case "export" :: dir :: Nil =>
        val root = Path.of(dir).toAbsolutePath
        exportTree(d).foreach { (rel, text) =>
          val p = root.resolve(rel)
          Files.createDirectories(p.getParent)
          Files.writeString(p, text)
        }
        println(s"exported to $root")
      case _ =>
        println(table(rows(d)))
        println()
        println(toolTable)
        println()
        println(s"recheck answer schema: ${ClaudeCode.schemaJson(AnswerSchemas.recheck).length} chars")
