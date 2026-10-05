package com.histmersion.server.agents

import io.circe.Json
import io.circe.syntax.*

/** What the generated trees depend on besides the definitions: where the runtimes reach this server,
  * and which optional parts to write. */
final case class GenSettings(
    /** The agent MCP endpoint (`<public-url>/mcp/agent`): one URL, the run token decides the tools. */
    agentMcpUrl: String,
    /** The full MCP endpoint (`<public-url>/mcp`) for the interactive flavours; None = leave the
      * MCP connection to the consumer (the checked-in `plugin/`, whose `.mcp.json` `just mcp-json` writes). */
    mcpUrl: Option[String],
    /** Where the quota-guard mod reports and asks (`<public-url>/api/agents/quota/claude`). */
    quotaUrl: String,
    /** The quota-guard mod in the headless Claude Code plugin (docs/research-agent-runtimes.md §3a). */
    claudeMod: Boolean,
    /** The reporting mod in the interactive Claude Code plugin: the person's own sessions report the
      * shared plan's windows (never denies). */
    interactiveMod: Boolean,
    /** The plugin version (`0.3.0`, `+<definitions sha>` for a server-generated tree). */
    version: String)

/** The per-runtime trees, as pure functions from the canonical definitions to `relative path → text`
  * (docs/research-agent-runtimes.md §2.2). Nothing here touches the disk; [[AgentTrees]] writes. */
object AgentGenerator:
  import AgentDefinitions.render

  val PluginVersion = "0.3.0"
  val HeadlessPlugin = "histmersion-agents"
  val InteractivePlugin = "histmersion"
  /** The marketplace a server-generated interactive plugin is installed from (`histmersion@histmersion-local`):
    * not `histmersion`, which is the repository's own marketplace. */
  val LocalMarketplace = "histmersion-local"
  val TokenEnv = "HISTMERSION_RUN_TOKEN"
  val QuotaUrlEnv = "HISTMERSION_QUOTA_URL"

  /** The canonical inputs, loaded and checked. */
  final case class Defs(agents: List[AgentDef], fragments: Map[String, String], skills: Map[String, SkillDef]):
    def system(d: AgentDef, style: ToolStyle): String =
      AgentDefinitions.system(d, fragments, skills, style).fold(m => throw IllegalStateException(m), identity)

  /** Problems that would make a generated tree lie: an include or skill that does not exist, a
    * placeholder naming no MCP tool (`known`), a skill whose name or description runtimes reject. */
  def check(d: Defs, known: Set[String]): List[String] =
    val agentProblems = d.agents.flatMap { a =>
      AgentDefinitions.system(a, d.fragments, d.skills, ToolStyle.Bare).left.toOption.toList ++
        (AgentDefinitions.placeholders(a.body) ++ a.include.flatMap(i => d.fragments.get(i).toList.flatMap(AgentDefinitions.placeholders)))
          .filterNot(known).map(t => s"agents/${a.name}.md names {{tool:$t}}, which is not an MCP tool")
    }
    val skillProblems = d.skills.values.toList.sortBy(_.name).flatMap { s =>
      AgentDefinitions.placeholders(s.header.mkString("\n") + "\n" + s.body).filterNot(known)
        .map(t => s"skills/${s.name}/SKILL.md names {{tool:$t}}, which is not an MCP tool") ++
        Option.when(s.description.length > 1024)(s"skills/${s.name}/SKILL.md: the description is over 1,024 characters (opencode refuses it)")
    }
    agentProblems ++ skillProblems

  // ── shared pieces ──────────────────────────────────────────────────────────────────────────────

  /** JSON as people write it (`"key": value`, two spaces) — the checked-in files diff cleanly. */
  private val Printer = io.circe.Printer.spaces2.copy(colonLeft = "")
  def pretty(j: Json): String = Printer.print(j) + "\n"

  /** A skill as a runtime reads it: placeholders rendered; `allowed-tools` kept only for the
    * person's own Claude Code (permissions belong to the agent role everywhere else). */
  def skillFile(s: SkillDef, style: ToolStyle, keepAllowedTools: Boolean): String =
    val header = s.header.filter(l => keepAllowedTools || !l.startsWith("allowed-tools:"))
    render(s"---\n${header.mkString("\n")}\n---\n${s.body}", style)

  private def skills(d: Defs, prefix: String, style: ToolStyle, keepAllowedTools: Boolean): Map[String, String] =
    d.skills.values.map(s => s"$prefix/${s.name}/SKILL.md" -> skillFile(s, style, keepAllowedTools)).toMap

  private def yamlString(s: String): String = s.asJson.noSpaces

  // ── Claude Code ────────────────────────────────────────────────────────────────────────────────

  /** The built-in tools a role gets besides Histmersion's MCP tools. */
  def claudeWebTools(w: WebAccess): List[String] = w match
    case WebAccess.Search => List("WebSearch", "WebFetch")
    case WebAccess.Fetch  => List("WebFetch")
    case _                => Nil

  /** A headless agent: the role's MCP tools and nothing else; the prompt with its includes and skills
    * inlined (a main-thread `--agent` gets no `skills:` preload, §3a). */
  def claudeAgent(d: Defs, a: AgentDef): String =
    val tools = a.tools.map(t => ToolStyle.Claude.tool(t.wire)) ++ claudeWebTools(a.web)
    // no stripMargin around the prompt: a Markdown table row starts with `|`
    s"---\nname: ${a.name}\ndescription: ${yamlString(a.description)}\ntools: ${tools.mkString(", ")}\n" +
      s"maxTurns: ${a.maxTurns}\n---\n${d.system(a, ToolStyle.Claude)}\n"

  /** The settings layer of one role's run: deny-by-default permissions. NOT `disableAllHooks` — it
    * also turns the quota-guard mod off (§3a P5); the person's own hooks stay out through
    * `--setting-sources ""` instead. */
  def claudeSettings(a: AgentDef): String =
    val allow = a.tools.map(t => ToolStyle.Claude.tool(t.wire)) ++ claudeWebTools(a.web)
    val deny  = List("Bash", "Edit", "Write", "NotebookEdit", "Agent") ++
      List("WebFetch", "WebSearch").filterNot(claudeWebTools(a.web).contains)
    pretty(Json.obj(
      "disableClaudeAiConnectors" -> Json.True,
      "permissions" -> Json.obj("defaultMode" -> "dontAsk".asJson, "allow" -> allow.asJson, "deny" -> deny.asJson)))

  def pluginJson(name: String, version: String, description: String): String =
    pretty(Json.obj("name" -> name.asJson, "description" -> description.asJson, "version" -> version.asJson,
      "author" -> Json.obj("name" -> "Mateusz Kubuszok".asJson), "homepage" -> "https://github.com/histmersion/histmersion".asJson,
      "keywords" -> List("research", "history", "archaeology", "mythology", "mcp", "postgis").asJson))

  private val InteractiveDescription =
    "Query and enrich the Histmersion research corpus (comparative mythology, folklore, Bronze-Age history, " +
      "Indo-European origins) over MCP and the histmersion CLI, instead of grepping markdown files."

  def marketplaceJson(name: String, source: String, version: String): String =
    pretty(Json.obj(
      "name" -> name.asJson,
      "owner" -> Json.obj("name" -> "Mateusz Kubuszok".asJson),
      "metadata" -> Json.obj("description" -> "Local marketplace for the Histmersion plugin — MCP tools and skills for the research corpus.".asJson),
      "plugins" -> Json.arr(Json.obj("name" -> InteractivePlugin.asJson, "source" -> source.asJson,
        "description" -> InteractiveDescription.asJson, "version" -> version.asJson, "category" -> "research".asJson))))

  def mcpJson(url: String): String =
    pretty(Json.obj("mcpServers" -> Json.obj(AgentDefinitions.McpServer -> Json.obj("type" -> "http".asJson, "url" -> url.asJson))))

  /** An agent as the person's own Claude Code shows it: the hand-written frontmatter (name,
    * description, model, built-in tools) and the body as written — it still speaks the `histmersion`
    * CLI the interactive panel runs in Bash. */
  def interactiveAgent(a: AgentDef): Option[String] =
    a.interactive.filter(_ => a.runsOn(AgentRuntime.Interactive)).map { i =>
      s"---\nname: ${a.name}\ndescription: ${a.description}\nmodel: ${i.model}\ntools: ${i.tools.mkString(", ")}\n---\n" +
        render(a.body, ToolStyle.Claude)
    }

  /** The interactive plugin (the repo's `plugin/`, and `claude-code/histmersion/` under the data dir). */
  def interactivePlugin(d: Defs, s: GenSettings): Map[String, String] =
    Map(".claude-plugin/plugin.json" -> pluginJson(InteractivePlugin, s.version, InteractiveDescription)) ++
      d.agents.flatMap(a => interactiveAgent(a).map(t => s"agents/${a.name}.md" -> t)) ++
      skills(d, "skills", ToolStyle.Claude, keepAllowedTools = true) ++
      s.mcpUrl.map(u => ".mcp.json" -> mcpJson(u)) ++
      (if s.interactiveMod then modFiles(QuotaMod.Reporting) else Map.empty)

  /** The checked-in tree of the repository: `plugin/` and the root marketplace. */
  def repoPlugin(d: Defs): Map[String, String] =
    val s = GenSettings("", None, "", claudeMod = false, interactiveMod = false, PluginVersion)
    interactivePlugin(d, s).map((k, v) => s"plugin/$k" -> v) +
      (".claude-plugin/marketplace.json" -> marketplaceJson(InteractivePlugin, "./plugin", PluginVersion))

  enum QuotaMod:
    case Guard, Reporting

  private def modFiles(m: QuotaMod): Map[String, String] =
    Map("hooks/hooks.json" -> "{ \"modules\": [\"./quota-guard.ts\"] }\n", "hooks/quota-guard.ts" -> QuotaGuard.source(m))

  def claudeCode(d: Defs, s: GenSettings): Map[String, String] =
    val headless = d.agents.filter(_.runsOn(AgentRuntime.ClaudeCode))
    val hl = Map("headless/.claude-plugin/plugin.json" -> pluginJson(HeadlessPlugin, s.version,
        "Generated by Histmersion from agents/*.md and skills/*: the embedded agents' roles, MCP-only.")) ++
      headless.map(a => s"headless/agents/${a.name}.md" -> claudeAgent(d, a)) ++
      skills(d, "headless/skills", ToolStyle.Claude, keepAllowedTools = false) ++
      (if s.claudeMod then modFiles(QuotaMod.Guard).map((k, v) => s"headless/$k" -> v) else Map.empty)
    val roles = headless.map(a => s"roles/${a.name}/settings.json" -> claudeSettings(a))
    val market = Map(".claude-plugin/marketplace.json" -> marketplaceJson(LocalMarketplace, s"./$InteractivePlugin", s.version)) ++
      interactivePlugin(d, s).map((k, v) => s"$InteractivePlugin/$k" -> v)
    (hl ++ roles ++ market).map((k, v) => s"claude-code/$k" -> v)

  // ── Codex ──────────────────────────────────────────────────────────────────────────────────────

  /** A TOML basic string: every value we write goes through here, so a prompt can hold any text. */
  def tomlString(s: String): String =
    val b = StringBuilder("\"")
    s.foreach {
      case '"'  => b.append("\\\"")
      case '\\' => b.append("\\\\")
      case '\n' => b.append("\\n")
      case '\r' => b.append("\\r")
      case '\t' => b.append("\\t")
      case c if c < 0x20 || c == 0x7f => b.append(f"\\u${c.toInt}%04x")
      case c    => b.append(c)
    }
    b.append('"').toString

  private def tomlList(xs: List[String]): String = xs.map(tomlString).mkString("[", ", ", "]")

  /** The base of every agent run: everything off but Histmersion's MCP server, which grants nothing
    * until a role's profile names its tools (docs/research-agent-runtimes.md §1.2). */
  def codexBase(s: GenSettings): String =
    s"""# Generated by Histmersion — the CODEX_HOME of its agent runs. Do not edit: regenerated on start.
       |web_search = "disabled"
       |sandbox_mode = "read-only"
       |approval_policy = "never"
       |check_for_update_on_startup = false
       |include_environment_context = false
       |include_permissions_instructions = false
       |include_collaboration_mode_instructions = false
       |
       |[skills.bundled]
       |enabled = false
       |
       |[agents]
       |enabled = false
       |
       |[features]
       |shell_tool = false
       |unified_exec = false
       |view_image = false
       |image_generation = false
       |browser_use = false
       |computer_use = false
       |apps = false
       |plugins = false
       |multi_agent = false
       |memories = false
       |tool_suggest = false
       |
       |[mcp_servers.${AgentDefinitions.McpServer}]
       |url = ${tomlString(s.agentMcpUrl)}
       |bearer_token_env_var = "$TokenEnv"
       |required = true
       |enabled_tools = []
       |""".stripMargin

  /** One role's profile (`codex -p <role>`): its prompt as developer instructions — includes and
    * skills inlined, since with the shell off the model cannot open a listed `SKILL.md` — and its tools. */
  def codexProfile(d: Defs, a: AgentDef): String =
    val web = a.web match
      case WebAccess.Search | WebAccess.Fetch => "web_search = \"live\"\n"
      case _                                  => ""
    val mcp =
      if a.tools.isEmpty then s"[mcp_servers.${AgentDefinitions.McpServer}]\nenabled = false\nrequired = false\n"
      else s"[mcp_servers.${AgentDefinitions.McpServer}]\nenabled_tools = ${tomlList(a.tools.map(_.wire))}\n"
    s"# Generated by Histmersion from agents/${a.name}.md — `codex exec -p ${a.name}`.\n" +
      s"developer_instructions = ${tomlString(d.system(a, ToolStyle.Bare))}\n$web\n$mcp"

  /** The same role as a Codex custom agent, for interactive multi-agent use in a home that has it. */
  def codexAgent(d: Defs, a: AgentDef): String =
    s"name = ${tomlString(a.name)}\ndescription = ${tomlString(a.description)}\n" +
      s"developer_instructions = ${tomlString(d.system(a, ToolStyle.Bare))}\n"

  def codex(d: Defs, s: GenSettings): Map[String, String] =
    val roles = d.agents.filter(_.runsOn(AgentRuntime.Codex))
    val home = Map("home/config.toml" -> codexBase(s)) ++
      roles.map(a => s"home/${a.name}.config.toml" -> codexProfile(d, a)) ++
      roles.map(a => s"home/agents/${a.name}.toml" -> codexAgent(d, a)) ++
      skills(d, "home/skills", ToolStyle.Bare, keepAllowedTools = false)
    val plugin = Map("plugin/.codex-plugin/plugin.json" -> pretty(Json.obj("name" -> InteractivePlugin.asJson,
        "version" -> s.version.asJson, "description" -> InteractiveDescription.asJson,
        "skills" -> "./skills".asJson) deepMerge s.mcpUrl.fold(Json.obj())(_ => Json.obj("mcpServers" -> "./.mcp.json".asJson)))) ++
      skills(d, "plugin/skills", ToolStyle.Bare, keepAllowedTools = false) ++
      s.mcpUrl.map(u => "plugin/.mcp.json" -> mcpJson(u))
    (home ++ plugin).map((k, v) => s"codex/$k" -> v)

  // ── opencode ───────────────────────────────────────────────────────────────────────────────────

  /** The permission map of a role: deny everything, allow its tools (last match wins). */
  def opencodePermissions(a: AgentDef): List[(String, String)] =
    ("*" -> "deny") :: a.tools.map(t => ToolStyle.Opencode.tool(t.wire) -> "allow") ++ (a.web match
      case WebAccess.Search => List("webfetch" -> "allow", "websearch" -> "allow")
      case WebAccess.Fetch  => List("webfetch" -> "allow")
      case _                => Nil)

  def opencodeAgent(d: Defs, a: AgentDef): String =
    val perms = opencodePermissions(a).map((k, v) => s"  ${yamlString(k)}: $v").mkString("\n")
    s"---\ndescription: ${yamlString(a.description)}\nmode: primary\nsteps: ${a.maxTurns}\npermission:\n$perms\n---\n" +
      d.system(a, ToolStyle.Opencode) + "\n"

  def opencodeConfig(url: String, headless: Boolean): String =
    val server = Json.obj("type" -> "remote".asJson, "url" -> url.asJson, "enabled" -> Json.True) deepMerge
      (if headless then Json.obj("headers" -> Json.obj("Authorization" -> s"Bearer {env:$TokenEnv}".asJson)) else Json.obj())
    pretty(Json.obj("$schema" -> "https://opencode.ai/config.json".asJson, "autoupdate" -> Json.False,
      "share" -> "disabled".asJson, "mcp" -> Json.obj(AgentDefinitions.McpServer -> server)) deepMerge
      (if headless then Json.obj("permission" -> Json.obj("*" -> "deny".asJson)) else Json.obj()))

  def opencode(d: Defs, s: GenSettings): Map[String, String] =
    val roles = d.agents.filter(_.runsOn(AgentRuntime.Opencode))
    val runs = Map("xdg/opencode/opencode.json" -> pretty(Json.obj("$schema" -> "https://opencode.ai/config.json".asJson)),
        "config/opencode.json" -> opencodeConfig(s.agentMcpUrl, headless = true)) ++
      roles.map(a => s"config/agents/${a.name}.md" -> opencodeAgent(d, a)) ++
      skills(d, "config/skills", ToolStyle.Opencode, keepAllowedTools = false)
    val interactive = s.mcpUrl.map(u => "interactive/opencode.json" -> opencodeConfig(u, headless = false)).toMap ++
      skills(d, "interactive/skills", ToolStyle.Opencode, keepAllowedTools = false)
    (runs ++ interactive).map((k, v) => s"opencode/$k" -> v)

  /** Every tree for `runtimes`. */
  def tree(d: Defs, s: GenSettings, runtimes: List[AgentRuntime]): Map[String, String] =
    runtimes.distinct.map {
      case AgentRuntime.ClaudeCode => claudeCode(d, s)
      case AgentRuntime.Codex      => codex(d, s)
      case AgentRuntime.Opencode   => opencode(d, s)
      case _                       => Map.empty[String, String]
    }.foldLeft(Map.empty[String, String])(_ ++ _)

/** The quota-guard mod (docs/research-agent-runtimes.md §2.5, §3a P3): a Claude Code mod module that
  * POSTs every rate-limit reading to Histmersion. In a headless run it carries the run token, and when
  * Histmersion answers that the plan pool is below its floor (or held) it denies further tool calls
  * and aborts the turn before the next model request — the engine then reads `terminal_reason:
  * "aborted_streaming"` as "stopped by the quota guard". The interactive flavour only reports. */
object QuotaGuard:
  def source(m: AgentGenerator.QuotaMod): String =
    val guard = m == AgentGenerator.QuotaMod.Guard
    s"""// Generated by Histmersion — the ${if guard then "quota guard of its headless runs" else "plan-usage reporter of your own sessions"}.
       |// Do not edit: regenerated by the server (histmersion agent generate).
       |import type { Register } from 'claude-code'
       |
       |let blocked: string | undefined
       |
       |async function report($$: any, rateLimits: unknown) {
       |  const url = await $$.env.get('${AgentGenerator.QuotaUrlEnv}')
       |  if (!url) return
       |  const token = ${if guard then s"await $$.env.get('${AgentGenerator.TokenEnv}')" else "await $.env.get('HISTMERSION_API_KEY')"}
       |  const headers: Record<string, string> = { 'content-type': 'application/json' }
       |  if (token) headers['authorization'] = 'Bearer ' + token
       |  try {
       |    const res = await $$.http.fetch(url, { method: 'POST', headers,
       |      body: JSON.stringify({ source: '${if guard then "mod" else "interactive"}', rateLimits }) })
       |    if (res.ok) {
       |      const answer = JSON.parse(res.text)
       |      if (answer.allow === false) blocked = answer.reason || 'the plan pool is below its floor'
       |    }
       |  } catch (err) { /* the server decides; an unreachable one stops nothing */ }
       |}
       |
       |export const register: Register = (on) => {
       |  on('session.measure', async ($$, e, next) => {
       |    if (e.rateLimits) await report($$, e.rateLimits)
       |    return next(e)
       |  })
       |""".stripMargin + (if guard then
      s"""  on('tool.call', async ($$, e, next) => {
         |    if (blocked) return { deny: 'histmersion quota guard: ' + blocked }
         |    return next(e)
         |  })
         |  on('turn.step', async function* ($$, e, next) {
         |    if (blocked && e.index > 0) {
         |      try { await $$.turn.abort({ turnId: e.turnId }) } catch (err) { /* already ending */ }
         |    }
         |    return yield* next(e)
         |  })
         |}
         |""".stripMargin else "}\n")
