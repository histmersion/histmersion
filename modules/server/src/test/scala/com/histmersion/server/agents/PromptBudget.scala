package com.histmersion
package server.agents
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.research.workflows.*

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.{
  AgentDef,
  AgentGenerator,
  AgentRuntime,
  AnswerSchemas,
  GenSettings,
  Toolbox,
  ToolStyle
}

import com.histmersion.domain.automation.workflows.ToolName

import sttp.ai.core.agent.ResponseSchema

import java.nio.file.{Files, Path}
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome

/** What every turn of an agent step re-sends before the model reads the user message: the role's system prompt, its MCP
  * tool definitions (as `/mcp/agent/<role>` lists them) and the typed answer's schema (Claude Code's `StructuredOutput`
  * tool). Pure and offline:
  *
  * sbt "server/Test/runMain com.histmersion.server.agents.promptBudgetReport" the table sbt "server/Test/runMain
  * com.histmersion.server.agents.promptBudgetReport export <dir>" sbt "server/Test/runMain
  * com.histmersion.server.agents.promptBudgetReport codex <dir>" the Codex tree
  *
  * `export` writes what `scripts/agents/claude-overhead.sh --from <dir>` launches: the generated Claude Code tree
  * (`claude-code/`), each role's MCP tool list (`mcp-tools/<agent>.json`, the `tools/list` result) and its answer
  * schema (`answer-schemas/<agent>.json`, the `--json-schema` text) — so the harness measures the whole first call, the
  * role's MCP tools included.
  *
  * Tokens are an estimate: characters / 2.7, the ratio the live first-call counts of the review panel show (the
  * skeptic's and the cold reader's prompts differ by ~27,100 characters and ~9,990 tokens).
  */
@main def promptBudgetReport(args: String*): Unit = PromptBudget.run(args.toList)

object PromptBudget:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  final case class Row(agent: AgentDef.Name, system: Int, tools: Map[ToolName, Int], answer: Int):
    def toolChars: Int = tools.values.sum
    def total: Int = system + toolChars + answer

  val CharsPerToken = 2.7
  def tokens(chars: Int): Int = math.round(chars / CharsPerToken).toInt

  /** The answer schema each agent definition's steps use (a reviewer's verification pass: `recheck`). */
  val answers: Map[AgentDef.Name, ResponseSchema[?]] = Map(
    AgentDef.Name("probe") -> AnswerSchemas.probe,
    AgentDef.Name("enrich-classify") -> LibraryAnswers.classification,
    AgentDef.Name("enrich-findings") -> LibraryAnswers.findings,
    AgentDef.Name("enrich-notes") -> LibraryAnswers.notes,
    AgentDef.Name("compile-extractor") -> ResearchAnswers.extract,
    AgentDef.Name("compile-writer") -> ResearchAnswers.draft,
    AgentDef.Name("article-rules-reviewer") -> ResearchAnswers.rules,
    AgentDef.Name("article-cold-reader") -> ResearchAnswers.coldReader,
    AgentDef.Name("article-skeptic") -> ResearchAnswers.skeptic,
    AgentDef.Name("article-fixer") -> ResearchAnswers.fixer,
    AgentDef.Name("goal-planner") -> AnswerSchemas.plan,
    AgentDef.Name("acquisition-ranker") -> LibraryAnswers.ranking
  )

  private lazy val toolbox = TestToolbox(null)

  /** One tool as the agent MCP endpoint lists it (`tools/list`). */
  extension (t: ToolName)
    def toolJson: Json =
      val at = toolbox.tool(t).get
      Json.obj(
        "name" -> at.name.asJson,
        "description" -> at.description.asJson,
        "inputSchema" -> Toolbox.inputSchema(at)
      )

    def toolChars: Int = t.toolJson.noSpaces.length

  def rows(d: AgentGenerator.Defs): List[Row] =
    d.agents.sortBy(_.name.value).map { a =>
      Row(
        a.name,
        d.system(a, ToolStyle.Claude).length,
        a.tools.map(t => t -> t.toolChars).toMap,
        answers.get(a.name).fold(0)(s => ClaudeCode.schemaJson(s).value.length)
      )
    }

  def table(rows: List[Row]): String =
    val head =
      "| agent | system | ~tok | tools | ~tok | answer | ~tok | total | ~tok |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|"
    (head :: rows.map(r =>
      s"| ${r.agent} | ${r.system} | ${tokens(r.system)} | ${r.toolChars} | ${tokens(r.toolChars)} | ${r.answer} | " +
        s"${tokens(r.answer)} | ${r.total} | ${tokens(r.total)} |"
    )).mkString("\n")

  def toolTable: String =
    "| tool | chars | ~tok |\n|---|---:|---:|\n" +
      ToolboxOrder.names.map(t => s"| ${t.value} | ${t.toolChars} | ${tokens(t.toolChars)} |").mkString("\n")

  /** The files `export` writes, relative to its directory. */
  def exportTree(d: AgentGenerator.Defs): Map[String, String] =
    val settings = GenSettings(
      GenSettings.AgentMcpUrl("http://127.0.0.1:9/mcp/agent"),
      None,
      GenSettings.QuotaUrl("http://127.0.0.1:9/quota"),
      claudeMod = GenSettings.ClaudeMod(false),
      interactiveMod = GenSettings.InteractiveMod(false),
      GenSettings.Version(AgentGenerator.PluginVersion)
    )
    val runs = d.agents.filter(_.runsOn(AgentRuntime.ClaudeCode))
    AgentGenerator.claudeCode(d, settings) ++
      runs.map(a => s"mcp-tools/${a.name}.json" -> Json.obj("tools" -> a.tools.map(_.toolJson).asJson).spaces2) ++
      runs.flatMap(a =>
        answers.get(a.name).map(s => s"answer-schemas/${a.name}.json" -> ClaudeCode.schemaJson(s).value)
      )

  def run(args: List[String]): Unit =
    val d = Generated.defs()
    args match
      case "codex" :: dir :: Nil => // the generated Codex home (lean base prompt, 6000-token outputs), for `codex debug prompt-input`
        val root = Path.of(dir).toAbsolutePath
        val s = GenSettings(
          GenSettings.AgentMcpUrl("http://127.0.0.1:9/mcp/agent"),
          None,
          GenSettings.QuotaUrl("http://127.0.0.1:9/quota"),
          claudeMod = GenSettings.ClaudeMod(false),
          interactiveMod = GenSettings.InteractiveMod(false),
          GenSettings.Version(AgentGenerator.PluginVersion),
          codexToolOutputTokens = GenSettings.CodexToolOutputTokens(6000),
          codexLeanBase = GenSettings.CodexLeanBase(true)
        )
        AgentGenerator.codex(d, s).foreach { (rel, text) =>
          val p = root.resolve(rel)
          Files.createDirectories(p.getParent)
          Files.writeString(p, text)
        }
        println(s"exported to $root")
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
        println(s"recheck answer schema: ${ClaudeCode.schemaJson(ResearchAnswers.recheck).value.length} chars")
