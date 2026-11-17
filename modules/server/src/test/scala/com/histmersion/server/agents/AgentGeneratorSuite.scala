package com.histmersion
package server.agents

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.api.automation.Agents.Role

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.util.storage.SafePath
import com.histmersion.domain.automation.agents.AgentDef
import com.histmersion.domain.automation.agents.AgentDefinitions
import com.histmersion.domain.automation.agents.GenSettings
import com.histmersion.config.AppConfig
import com.histmersion.config.CodexConfig
import com.histmersion.util.storage.Location
import com.histmersion.domain.automation.workflows.ToolName

/** The canonical definitions and the per-runtime trees generated from them (docs/research-agent-runtimes.md §2):
  * parsing, placeholders, the system prompt every runtime gets, the trees' restrictions, the writer — and that the
  * checked-in `plugin/` is the generator's output. Pure (no database).
  */
class AgentGeneratorSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private lazy val defs = AgentDefinitions(new Prompts(Prompts.OverrideDir("")), EngineContributions.roster)
  private lazy val d = Generated.defs()
  private def agent(s: StepAgent) = d.agents.find(_.name == s.wire).getOrElse(fail(s"no ${s.wire}"))

  test("every pipeline step has a definition that parses, and every bundled file is in the index"):
    StepAgent.values.foreach(s => assert(defs.agent(s).isRight, s"${s.wire}: ${defs.agent(s)}"))
    assertEquals(defs.agents.map(_.size), Right(StepAgent.values.length))
    assertEquals(defs.skills.map(_.size), Right(19))
    assert(Prompts.bundled.map(_.value).contains("index.txt") || Prompts.bundled.nonEmpty)
    Prompts.bundled.foreach(rel =>
      assert(
        new Prompts(Prompts.OverrideDir("")).load(SafePath.Candidate(rel.value)).exists(_.trim.nonEmpty),
        s"missing or empty: agents/$rel"
      )
    )

  test("the manifest: role, tools, includes, interactive spec"):
    val sk = agent(StepAgent.Skeptic)
    assertEquals(sk.role, Role.Skeptic)
    assertEquals(
      sk.tools.map(t => t.value: String),
      List("get_source", "passages", "neighbors", "search", "query", "followups")
    )
    assertEquals(sk.include, List("review-common", "review-skeptic", "writing-rules"))
    assertEquals(sk.interactive.map(_.tools), Some(List("Bash", "Read")))
    assertEquals(agent(StepAgent.ColdReader).tools, Nil, "the cold reader reads the article and nothing else")
    assertEquals(agent(StepAgent.EnrichClassify).tools.map(t => t.value: String), List("facets", "search"))
    assert(!agent(StepAgent.EnrichClassify).runsOn(AgentRuntime.Interactive))

  test("the fixer answers with edits and dry-runs them; the compile writer may lint its draft"):
    val fx = agent(StepAgent.Fixer)
    assertEquals(
      fx.tools.map(t => t.value: String),
      List("get_source", "passages", "neighbors", "search", "query", "check_edits")
    )
    assertEquals(fx.maxTurns, 12, "the base; the pipeline adds turns per finding")
    val sys = d.system(fx, ToolStyle.Claude)
    assert(sys.contains("`mcp__histmersion__check_edits`") && sys.contains("Edits, not a rewrite"), sys.takeRight(3000))
    assert(!sys.contains("WHOLE revised body"), "the whole-body answer is gone")
    assert(
      d.system(fx, ToolStyle.Bare).contains("evidence: [[citekey]] @offset"),
      "the fixer is told how evidence reads"
    )
    val wr = agent(StepAgent.CompileWriter)
    assertEquals((wr.tools.map(t => t.value: String), wr.maxTurns), (List("check_edits"), 4))
    assert(d.system(wr, ToolStyle.Bare).contains("check_edits"))
    // the reviewers are asked for their evidence
    for r <- List(StepAgent.Skeptic, StepAgent.RulesReviewer) do
      assert(d.system(agent(r), ToolStyle.Bare).contains("`{citekey, offset, quote}`"), r.wire)
    assert(!d.system(agent(StepAgent.ColdReader), ToolStyle.Bare).contains("offset, quote"))

  test("#958: nobody writes the Not used list or the Method note — the writer and the fixer give reasons"):
    val wr = d.system(agent(StepAgent.CompileWriter), ToolStyle.Bare)
    assert(wr.contains("`reasons`: for each source of the claim table you do NOT cite"), wr)
    assert(
      !wr.contains("Open questions, Not used, Glossary") && !wr.contains("`_Method_` note at the end stating"),
      "the old instruction is gone"
    )
    val fx = d.system(agent(StepAgent.Fixer), ToolStyle.Bare)
    assert(
      fx.contains("**Coverage reasons.**") && fx.contains("<generated-sections>") && fx.contains(
        "never written into the body"
      ),
      fx
    )
    // the CLI form of the same rule is the person's own fixer's
    assert(AgentGenerator.interactiveAgent(agent(StepAgent.Fixer)).exists(_.contains("--reason <citekey>=<why>")))
    assert(
      !fx.contains("*Unresolved / Open questions*, and *Not used*"),
      "the fixer is no longer told to write Not used"
    )
    val rules = agent(StepAgent.RulesReviewer)
    // facets: never called in 36 traced rules steps (2026-10-09), so no longer offered
    assertEquals(
      rules.tools.map(t => t.value: String),
      List("get_source", "passages", "neighbors", "search", "query", "article_coverage")
    )
    val rs = d.system(rules, ToolStyle.Claude)
    assert(rs.contains("`mcp__histmersion__article_coverage`") && rs.contains("check the REASONS"), rs.takeRight(4000))
    assert(
      d.system(agent(StepAgent.Skeptic), ToolStyle.Bare).contains("A source dismissed\n  there whose record bears")
    )
    // the bundled writing guide says so too
    assert(wr.contains("never write those by hand") && wr.contains("histmersion article coverage <slug>"))

  test("a definition that names a tool an agent cannot have, or misses its name, is refused with why"):
    val bad = AgentDefinitions.Markdown(
      "---\nname: x\ndescription: d\nmodel-role: reviewer\ntools:\n  histmersion: [get_source, patch_source]\n---\nbody"
    )
    def refused(name: String, md: AgentDefinitions.Markdown) =
      AgentDefinitions.parse(AgentDef.Name(name), md, ToolboxOrder.names).left.map(_.sentence)
    assertEquals(
      refused("x", bad),
      Left(
        "agents/x.md: tools.histmersion names tools an agent cannot be offered: patch_source " +
          s"(one of ${ToolboxOrder.names.mkString(", ")})"
      )
    )
    assertEquals(refused("y", bad), Left("agents/y.md: name 'x' is not the file's"))
    assertEquals(refused("x", AgentDefinitions.Markdown("no header")), Left("agents/x.md: no YAML header (--- … ---)"))
    val noRole = AgentDefinitions.Markdown(bad.value.replace("model-role: reviewer", "model-role: boss"))
    assertEquals(
      refused("x", noRole),
      Left(s"agents/x.md: model-role is not one of ${Role.values.map(_.wire).mkString(", ")}")
    )

  test("placeholders render per runtime"):
    val t = "call {{tool:get_source}} then {{tool:*}}"
    assertEquals(
      AgentDefinitions.render(t, ToolStyle.Claude),
      "call mcp__histmersion__get_source then mcp__histmersion__*"
    )
    assertEquals(AgentDefinitions.render(t, ToolStyle.Opencode), "call histmersion_get_source then histmersion_*")
    assertEquals(AgentDefinitions.render(t, ToolStyle.Bare), "call get_source then the histmersion tools")
    assertEquals(AgentDefinitions.placeholders(t), List("get_source"))

  test("the headless prompt: body, includes in order (the guide under its heading), skills inlined"):
    val sk = agent(StepAgent.Skeptic)
    val sys = d.system(sk, ToolStyle.Bare)
    assert(sys.startsWith("You are the **specialist skeptic**"), sys.take(80))
    val (common, notes, guide) = (
      sys.indexOf("## Running inside the Histmersion server"),
      sys.indexOf("Database errors you notice go in `proposals`"),
      sys.indexOf("# The writing guide (docs/writing-guide.md)")
    )
    assert(common > 0 && notes > common && guide > notes, (common, notes, guide))
    assert(!d.system(agent(StepAgent.ColdReader), ToolStyle.Bare).contains("# The writing guide"))
    assert(d.system(sk, ToolStyle.Claude).contains("`mcp__histmersion__get_source`"), "placeholders follow the runtime")
    val withSkill = sk.copy(skills = List(AgentDef.Skills("histmersion-query")))
    assert(d.system(withSkill, ToolStyle.Bare).contains("# Skill: histmersion-query"))
    assert(
      AgentDefinitions
        .system(sk.copy(include = List(AgentDef.Include("nope"))), d.fragments, d.skills, ToolStyle.Bare)
        .isLeft
    )

  private val Settings = GenSettings(
    GenSettings.AgentMcpUrl("http://127.0.0.1:18000/mcp/agent"),
    Some(GenSettings.McpUrl("http://127.0.0.1:18000/mcp")),
    GenSettings.QuotaUrl("http://127.0.0.1:18000/api/agents/quota/claude"),
    claudeMod = GenSettings.ClaudeMod(true),
    interactiveMod = GenSettings.InteractiveMod(false),
    GenSettings.Version("0.3.0+t")
  )

  test("Claude Code: the generated agent carries EXACTLY the prompt the engine sends, its tools, and the mod"):
    val tree = AgentGenerator.claudeCode(d, Settings)
    d.agents.filter(_.runsOn(AgentRuntime.ClaudeCode)).foreach { a =>
      val file = tree(s"claude-code/headless/agents/${a.name}.md")
      val body = AgentDefinitions.split(AgentDefinitions.Markdown(file)).toOption.get._2
      assertEquals(
        body,
        d.system(a, ToolStyle.Claude) + "\n",
        s"${a.name}: the generated agent drifted from the engine's prompt"
      )
    }
    val sk = tree("claude-code/headless/agents/article-skeptic.md")
    assert(
      sk.contains("tools: mcp__histmersion__get_source, mcp__histmersion__passages, mcp__histmersion__neighbors"),
      sk.take(400)
    )
    assert(sk.contains(", StructuredOutput\nmaxTurns: 21\n"), s"the answer tool, and a turn for it: ${sk.take(600)}")
    val settings = tree("claude-code/roles/article-skeptic/settings.json")
    assert(
      settings.contains("\"defaultMode\": \"dontAsk\"") && settings.contains("\"Bash\"") && settings.contains(
        "\"StructuredOutput\""
      ) && !settings.contains("disableAllHooks"),
      settings
    )
    assert(tree.contains("claude-code/headless/hooks/quota-guard.ts"))
    assert(tree("claude-code/headless/hooks/quota-guard.ts").contains("$.turn.abort"))
    assert(
      !tree.keys.exists(_.startsWith("claude-code/headless/.mcp.json")),
      "a bundled server would be dropped by --strict-mcp-config anyway"
    )
    assert(!tree("claude-code/headless/skills/review-article/SKILL.md").contains("allowed-tools"))
    assert(tree("claude-code/histmersion/skills/review-article/SKILL.md").contains("allowed-tools"))
    assert(tree("claude-code/histmersion/.mcp.json").contains("http://127.0.0.1:18000/mcp\""))
    assert(tree("claude-code/.claude-plugin/marketplace.json").contains("\"histmersion-local\""))
    assert(!tree.values.exists(_.contains("{{tool:")), "no placeholder survives")
    val noMod = AgentGenerator.claudeCode(d, Settings.modify(_.claudeMod).setTo(GenSettings.ClaudeMod(false)))
    assert(!noMod.keys.exists(_.contains("quota-guard")))

  test("Codex: everything off in the base, one profile per role with its prompt and tools"):
    val tree = AgentGenerator.codex(d, Settings)
    val base = tree("codex/home/config.toml")
    List(
      "shell_tool = false",
      "web_search = \"disabled\"",
      "sandbox_mode = \"read-only\"",
      "approval_policy = \"never\"",
      "[agents]\nenabled = false",
      "enabled_tools = []",
      "bearer_token_env_var = \"HISTMERSION_RUN_TOKEN\""
    )
      .foreach(s => assert(base.contains(s), s))
    val sk = tree("codex/home/article-skeptic.config.toml")
    assert(
      sk.contains(
        "enabled_tools = [\"get_source\", \"passages\", \"neighbors\", \"search\", \"query\", \"followups\"]"
      ),
      sk.takeRight(300)
    )
    // #1493: approval_policy = "never" refuses a tool that asks, so each of the role's tools is approved in advance
    val approved = "(?m)^\\[mcp_servers\\.histmersion\\.tools\\.([a-z_]+)\\]\\napproval_mode = \"approve\"$".r
      .findAllMatchIn(sk)
      .map(_.group(1))
      .toList
    assertEquals(approved, List("get_source", "passages", "neighbors", "search", "query", "followups"))
    assert(!tree("codex/home/article-cold-reader.config.toml").contains("approval_mode"), "no tools: nothing approved")
    assert(
      sk.contains(
        s"developer_instructions = ${AgentGenerator.tomlString(d.system(agent(StepAgent.Skeptic), ToolStyle.Bare))}"
      ),
      "the profile carries the engine's prompt"
    )
    assert(
      tree("codex/home/article-cold-reader.config.toml").contains("enabled = false"),
      "no tools: no MCP server at all"
    )
    assert(
      tree.contains("codex/plugin/.codex-plugin/plugin.json") && tree.contains(
        "codex/home/skills/histmersion-query/SKILL.md"
      )
    )

  test("Codex: the base keeps the skills list and big tool outputs out of every request"):
    // `codex debug prompt-input` on a scratch home (codex-cli 0.163.0): without it, the developer message
    // carries an 8,070-character <skills_instructions> list of home/skills on every request
    val base = AgentGenerator.codexBase(Settings)
    assert(base.contains("[skills]\ninclude_instructions = false\n\n[skills.bundled]\nenabled = false"), base)
    assert(!base.contains("tool_output_token_limit"), "0: the model's own limit")
    val limited =
      AgentGenerator.codexBase(Settings.modify(_.codexToolOutputTokens).setTo(GenSettings.CodexToolOutputTokens(6000)))
    assert(limited.contains("\ntool_output_token_limit = 6000\n"), limited)
    assert(
      limited.indexOf("tool_output_token_limit") < limited.indexOf("[skills]"),
      "a top-level key, before the first table"
    )

  test("Codex lean: the home carries Histmersion's base prompt and names it; builtin writes neither"):
    val lean = Settings
      .modify(_.codexLeanBase)
      .setTo(GenSettings.CodexLeanBase(true))
      .modify(_.codexToolOutputTokens)
      .setTo(GenSettings.CodexToolOutputTokens(6000))
    val tree = AgentGenerator.codex(d, lean)
    val base = tree("codex/home/config.toml")
    assert(base.contains("\nmodel_instructions_file = \"base-instructions.md\"\n"), base)
    assert(
      base.indexOf("model_instructions_file") < base.indexOf("[skills]"),
      "a top-level key, before the first table"
    )
    val file = tree("codex/home/base-instructions.md")
    // what a headless, shell-less step acts on survives …
    List(
      "headless step of the Histmersion research server",
      "never ask a question",
      "`functions.exec`",
      "`await Promise.allSettled([...])`",
      "a few at a time",
      "the JSON object alone",
      "- Start every script with `// @exec: {\"max_output_tokens\": 6000}`.\n\n# Answer"
    )
      .foreach(x => assert(file.contains(x), s"lost: $x\n$file"))
    // … and Codex's coding-session guidance does not come back
    List("PR", "rg", "SKILL.md", "plugin", "commentary` channel", "Visualizations", "{{").foreach(x =>
      assert(!file.contains(x), s"carries '$x'")
    )
    assert(file.length < 1600, s"${file.length} characters")
    assertEquals(
      AgentGenerator
        .codexBaseInstructions(d, Settings.modify(_.codexLeanBase).setTo(GenSettings.CodexLeanBase(true)))
        .contains("@exec"),
      false,
      "no output limit: no budget line"
    )
    val builtin = AgentGenerator.codex(d, Settings)
    assert(!builtin("codex/home/config.toml").contains("model_instructions_file"))
    assert(!builtin.contains("codex/home/base-instructions.md"))

  test("agents.providers.codex.base-instructions: lean by default, builtin by config"):
    val c = AppConfig.load().agents.providers.codex
    assertEquals((c.baseInstructions, c.leanBase), ("lean", true))
    assert(
      !c.modify(_.baseInstructions).setTo(CodexConfig.BaseInstructions(" Builtin ")).leanBase && c
        .modify(_.baseInstructions)
        .setTo(CodexConfig.BaseInstructions("lean"))
        .leanBase
    )

  test("TOML strings hold any text"):
    assertEquals(AgentGenerator.tomlString("a \"b\" \\ c\nd\te\u0001"), "\"a \\\"b\\\" \\\\ c\\nd\\te\\u0001\"")

  test("opencode: deny everything, allow the role's tools; the MCP header reads the run token"):
    val tree = AgentGenerator.opencode(d, Settings)
    val sk = tree("opencode/config/agents/article-skeptic.md")
    assert(sk.contains("  \"*\": deny\n  \"histmersion_get_source\": allow"), sk.take(400))
    assert(sk.contains("mode: primary"))
    val conf = tree("opencode/config/opencode.json")
    assert(conf.contains("Bearer {env:HISTMERSION_RUN_TOKEN}") && conf.contains("\"*\": \"deny\""), conf)
    assert(tree("opencode/config/skills/histmersion-query/SKILL.md").contains("histmersion_"), "opencode's tool names")

  test("the writer: atomic per file, stale generated files removed, foreign files (a Codex login) never touched"):
    val dir = Files.createTempDirectory("hm-gen-")
    val auth = dir.resolve("codex/home/auth.json")
    Files.createDirectories(auth.getParent)
    Files.writeString(auth, "{\"never\":\"read\"}")
    val r1 = AgentTrees
      .write(
        summon[Stores].at(Location.AgentHomes, dir),
        Map("codex/home/a.toml" -> "1", "codex/home/b.toml" -> "2"),
        List("codex"),
        Sha256.assume("sha1")
      )
      .toOption
      .get
    assertEquals((r1.files, r1.written, r1.removed), (2, 2, 0))
    val r2 = AgentTrees
      .write(
        summon[Stores].at(Location.AgentHomes, dir),
        Map("codex/home/a.toml" -> "1"),
        List("codex"),
        Sha256.assume("sha2")
      )
      .toOption
      .get
    assertEquals((r2.written, r2.removed), (0, 1), "unchanged files are not rewritten; the stale one goes")
    assert(!Files.exists(dir.resolve("codex/home/b.toml")))
    assertEquals(Files.readString(auth), "{\"never\":\"read\"}")
    assert(Files.readString(dir.resolve("manifest.json")).contains("\"definitions\": \"sha2\""))
    val r3 = AgentTrees
      .write(
        summon[Stores].at(Location.AgentHomes, dir),
        Map("claude-code/x" -> "y"),
        List("claude-code"),
        Sha256.assume("s")
      )
      .toOption
      .get
    assert(Files.isDirectory(dir.resolve("claude-code/login")), "the empty login dir for CLAUDE_CONFIG_DIR")
    assert(Files.exists(dir.resolve("codex/home/a.toml")), "another runtime's files stay")
    assertEquals(r3.removed, 0)

  test("the whole tree generates under a server's runtimes dir"):
    val dir = Files.createTempDirectory("hm-gen-all-")
    val rt = CliRuntime(FakeCli.app(dir), EngineContributions.roster)
    val r = rt.trees.generate(Generated.known(rt.definitions)).fold(m => fail(m.sentences.mkString("; ")), identity)
    assertEquals(r.runtimes, List("claude-code", "codex", "opencode"))
    assert(Files.isRegularFile(dir.resolve("codex/home/article-skeptic.config.toml")))
    assert(Files.isRegularFile(dir.resolve("opencode/config/agents/enrich-classify.md")))
    assertEquals(rt.trees.codexHome, Some(dir.resolve("codex/home")))
    val unknown = rt.trees.generate(Set(ToolName("get_source")))
    assert(
      unknown.left.exists(_.exists {
        case Problem.Config.Definition(_, Problem.Config.Flaw.ToolUnknown(_)) => true
        case _                                                                => false
      }),
      "a placeholder naming nothing fails generation"
    )

/** The checked-in `plugin/` and `.claude-plugin/marketplace.json` are the generator's interactive Claude Code flavour,
  * file for file. Out of date: `just plugin`.
  */
class PluginTreeSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private lazy val root: Path =
    Iterator
      .iterate(Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .find(d => Files.exists(d.resolve("build.sbt")) && Files.isDirectory(d.resolve("plugin")))
      .get

  test("plugin/ is up to date with agents/ and skills/ (run `just plugin` when this fails)"):
    val tree = PluginTree.tree(EngineContributions.roster).fold(m => fail(m.sentences.mkString("; ")), identity)
    val stale = tree.toList.sortBy(_._1.value).collect {
      case (rel, text)
          if !Files.isRegularFile(root.resolve(rel.value)) || Files.readString(root.resolve(rel.value)) != text =>
        rel
    }
    assertEquals(stale, Nil)
    val extra = PluginTree.Owned
      .map(o => root.resolve(o.value))
      .filter(Files.exists(_))
      .flatMap { d =>
        val all = Files.walk(d)
        try
          all.iterator.asScala
            .filter(p => Files.isRegularFile(p) || Files.isSymbolicLink(p))
            .map(p => root.relativize(p).toString)
            .toList
        finally all.close()
      }
      .filterNot(p => tree.contains(SafePath.Candidate(p)))
    assertEquals(extra, Nil, "files in plugin/ the generator does not write")

  test(".claude/skills links every plugin skill; only the repo's own skills (scalafix) are real directories"):
    def names(d: Path) =
      val all = Files.list(d)
      try all.iterator.asScala.map(_.getFileName.toString).filterNot(_.startsWith(".")).toList.sorted
      finally all.close()
    val skills = root.resolve(".claude/skills")
    val plugin = names(root.resolve("plugin/skills"))
    val (links, own) = names(skills).partition(n => Files.isSymbolicLink(skills.resolve(n)))
    assertEquals(links, plugin, "one link per plugin skill: ln -s ../../plugin/skills/<name> .claude/skills/<name>")
    links.foreach(n => assertEquals(Files.readSymbolicLink(skills.resolve(n)).toString, s"../../plugin/skills/$n"))
    assertEquals(own, List("scalafix"), "repo-only skills, never shipped in plugin/")

  test("the interactive review agents are the hand-written definitions, byte for byte"):
    val sk = Files.readString(root.resolve("plugin/agents/article-skeptic.md"))
    assert(sk.startsWith("---\nname: article-skeptic\ndescription: A specialist adversary"), sk.take(120))
    assert(sk.contains("\nmodel: claude-opus-5-5\ntools: Bash, Read\n---\n"), sk.take(600))
