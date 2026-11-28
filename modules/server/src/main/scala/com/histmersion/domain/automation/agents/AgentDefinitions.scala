package com.histmersion
package domain.automation.agents

import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.domain.automation.agents.AgentDefinitions.render

import com.histmersion.util.problem.{External, Problem, Problems}
import com.histmersion.util.problem.Problems.traverseAll
import com.histmersion.server.agents.Codex

import scala.annotation.tailrec
import com.histmersion.api.automation.Agents.Role
import com.histmersion.domain.automation.workflows.ToolName
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.AgentRole
import com.histmersion.util.storage.SafePath
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AgentDefinitions:
  type FragmentBody = FragmentBody.T
  object FragmentBody extends Newtype[String, Pure]
  type FragmentName = FragmentName.T
  object FragmentName extends Newtype[String, Pure]
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]
  type ResourcePath = ResourcePath.T
  object ResourcePath extends Newtype[String, Pure]

  val McpServer = "histmersion"

  val WritingGuide = "writing-guide"
  private val GuideHeading = "# The writing guide (docs/writing-guide.md)"

  val WritingRules = "writing-rules"
  private val RulesHeading = "# The writing guide (docs/writing-guide.md): the rules an article is held to"

  private val Marked = """(?s)<!-- only:([a-z]+) -->\n?(.*?)<!-- /only:\1 -->\n?""".r
  private val Marker = """<!-- (/?)only:([a-z]+) -->""".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def forAudience(text: AgentDef.Body, keep: Set[Audience]): AgentDef.Body =
    if !text.value.contains("<!-- only:") then text
    else
      val out = Marked.replaceAllIn(
        text.value,
        m =>
          java.util.regex.Matcher.quoteReplacement(if keep.exists(_.wire.value == m.group(1)) then m.group(2) else "")
      )
      AgentDef.Body(out.replaceAll("\n{3,}", "\n\n"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markerProblems(text: AgentDef.Body): List[Problem.Config.Flaw] =
    val ms = Marker.findAllMatchIn(text.value).map(m => (m.group(1) == "/", m.group(2))).toList
    val unknown =
      ms.map(_._2)
        .distinct
        .filterNot(a => Audience.values.exists(_.wire.value == a))
        .map(a => Problem.Config.Flaw.AudienceUnknown(Problem.Raw(a)))
    @tailrec def pair(
        rest: List[(Boolean, String)],
        open: Option[String],
        errs: List[Problem.Config.Flaw]
    ): List[Problem.Config.Flaw] =
      (open, rest) match
        case (_, Nil) => errs ++ open.map(o => Problem.Config.Flaw.MarkerUnclosed(Problem.Raw(o))).toList
        case (None, (false, a) :: tail)             => pair(tail, Some(a), errs)
        case (Some(o), (true, a) :: tail) if o == a => pair(tail, None, errs)
        case (Some(o), (false, a) :: tail)          =>
          pair(tail, Some(a), errs :+ Problem.Config.Flaw.MarkerNested(Problem.Raw(a), Problem.Raw(o)))
        case (o, (true, a) :: tail) => pair(tail, o, errs :+ Problem.Config.Flaw.MarkerUnopened(Problem.Raw(a)))
    unknown ++ pair(ms, None, Nil)

  private val Placeholder = """\{\{tool:([a-z_]+|\*)\}\}""".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def render(text: String, style: ToolStyle): String =
    Placeholder.replaceAllIn(
      text,
      m =>
        java.util.regex.Matcher.quoteReplacement(m.group(1) match
          case "*" =>
            style match
              case ToolStyle.Bare => "the histmersion tools"
              case s              => s.tool(ToolUse.Tool("*"))
          case t => style.tool(ToolUse.Tool(t)))
    )

  def placeholders(text: String): List[String] =
    Placeholder.findAllMatchIn(text).map(_.group(1)).filter(_ != "*").toList.distinct

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def split(md: Markdown): Either[Problem.Config.Flaw, (String, String)] =
    val t = md.value.replace("\r\n", "\n")
    if !t.startsWith("---\n") then Left(Problem.Config.Flaw.NoHeader)
    else
      t.indexOf("\n---\n", 3) match
        case -1 => Left(Problem.Config.Flaw.HeaderUnclosed)
        case i  => Right((t.substring(4, i), t.substring(i + 5)))

  private def yaml(text: String): Either[Problem.Config.Flaw, Map[String, Any]] =
    try
      Option(Yaml(SafeConstructor(LoaderOptions())).load[Any](text)).map(_.asMatchable) match
        case Some(m: java.util.Map[?, ?]) => Right(m.asScala.toMap.map((k, v) => k.toString -> v))
        case _                            => Left(Problem.Config.Flaw.HeaderNotMapping)
    catch
      case e: Exception =>
        Left(Problem.Config.Flaw.HeaderUnparsable(External.Reported.of(Option(e.getMessage).getOrElse(""))))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def strings(field: String, v: Any): Either[Problem.Config.Flaw, List[String]] =
    Option(v).map(_.asMatchable) match
      case None                              => Right(Nil)
      case Some(l: java.util.List[?])        => Right(l.asScala.toList.map(String.valueOf))
      case Some(s: String) if s.trim.isEmpty => Right(Nil)
      case Some(other) => Left(Problem.Config.Flaw.NotList(Problem.Field(field), Problem.Raw(s"$other")))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[agents] def agentFile(name: AgentDef.Name): Problem.Raw = Problem.Raw(s"agents/$name.md")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def skillFile(name: SkillDef.Name): Problem.Raw = Problem.Raw(s"skills/$name/SKILL.md")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(name: AgentDef.Name, md: Markdown, known: List[ToolName]): Either[Problem, AgentDef] =
    val file = agentFile(name)
    def fail[A](flaw: Problem.Config.Flaw): Either[Problem, A] = Left(Problem.Config.Definition(file, flaw))
    split(md).flatMap((h, body) => yaml(h).map(_ -> body)).left.flatMap(fail).flatMap { (m, body) =>
      def str(k: String) = m.get(k).map(String.valueOf).map(_.trim).filter(_.nonEmpty)
      val tools = m.get("tools").map(_.asMatchable) match
        case Some(t: java.util.Map[?, ?]) => t.asScala.toMap.map((k, v) => k.toString -> v)
        case _                            => Map.empty[String, Any]
      val interactive = m.get("interactive").map(_.asMatchable) match
        case Some(i: java.util.Map[?, ?]) =>
          val im = i.asScala.toMap.map((k, v) => k.toString -> v)
          strings("interactive.tools", im.getOrElse("tools", null))
            .map(ts =>
              Some(
                InteractiveSpec(
                  InteractiveSpec.Model(String.valueOf(im.getOrElse("model", ""))),
                  ts.map(InteractiveSpec.Tools(_))
                )
              )
            )
        case _ => Right(None)
      for
        n <- str("name")
          .toRight(Problem.Config.Flaw.NoName)
          .flatMap(n => Either.cond(n == name.value, n, Problem.Config.Flaw.NameNotFile(Problem.Raw(n))))
          .left
          .flatMap(fail)
        desc <- str("description").toRight(Problem.Config.Flaw.NoDescription).left.flatMap(fail)
        role <- str("model-role")
          .flatMap(r => Role.parse(AgentRole.Wire(r)))
          .toRight(Problem.Config.Flaw.RoleUnknown(Role.values.toVector.map(r => Problem.Raw.of(r.wire))))
          .left
          .flatMap(fail)
        sk <- strings("skills", m.getOrElse("skills", null)).left.flatMap(fail)
        inc <- strings("include", m.getOrElse("include", null)).left.flatMap(fail)
        hm <- strings("tools.histmersion", tools.getOrElse("histmersion", null)).left.flatMap(fail)
        ts <- hm.map(t => known.find(_.value == t).toRight(t)).partitionMap(identity) match
          case (Nil, ok) => Right(ok)
          case (bad, _)  =>
            fail(
              Problem.Config.Flaw
                .ToolsUnoffered(bad.toVector.map(Problem.Raw(_)), known.toVector.map(Problem.Raw.of(_)))
            )
        web <- WebAccess.values
          .find(w => tools.get("web").map(String.valueOf).getOrElse("none") == w.wire.value)
          .toRight(Problem.Config.Flaw.WebUnknown)
          .left
          .flatMap(fail)
        wr <- Writes.values
          .find(w => str("writes").getOrElse("none") == w.wire.value)
          .toRight(Problem.Config.Flaw.WritesUnknown)
          .left
          .flatMap(fail)
        turns <- str("max-turns")
          .map(_.toIntOption.filter(_ > 0).toRight(Problem.Config.Flaw.TurnsNotPositive))
          .getOrElse(Right(6))
          .left
          .flatMap(fail)
        rts <- strings("runtimes", m.getOrElse("runtimes", null)).left.flatMap(fail).flatMap { rs =>
          rs.map(r => AgentRuntime.parse(AgentRuntime.Wire(r)).toRight(r)).partitionMap(identity) match
            case (Nil, ok) if ok.nonEmpty => Right(ok.toSet)
            case (Nil, _)                 => Right(AgentRuntime.values.toSet - AgentRuntime.Interactive)
            case (bad, _)                 => fail(Problem.Config.Flaw.RuntimesUnknown(bad.toVector.map(Problem.Raw(_))))
        }
        it <- interactive.left.flatMap(fail)
        _ <- Either
          .cond(!rts.contains(AgentRuntime.Interactive) || it.isDefined, (), Problem.Config.Flaw.InteractiveUnset)
          .left
          .flatMap(fail)
      yield AgentDef(
        AgentDef.Name(n),
        AgentDef.Description(desc),
        role,
        sk.map(AgentDef.Skills(_)),
        inc.map(AgentDef.Include(_)),
        ts,
        web,
        wr,
        AgentDef.MaxTurns(turns),
        str("answer").map(AgentDef.Answer(_)),
        rts,
        it,
        AgentDef.Body(body)
      )
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseSkill(name: SkillDef.Name, md: Markdown): Either[Problem, SkillDef] =
    val file = skillFile(name)
    split(md)
      .flatMap { (h, body) =>
        yaml(h).flatMap { m =>
          Either.cond(
            m.get("name").map(String.valueOf).contains(name.value),
            SkillDef(name, h.split('\n').toList.map(SkillDef.Header(_)), SkillDef.Body(body)),
            Problem.Config.Flaw.SkillNotNamed(Problem.Raw.of(name))
          )
        }
      }
      .left
      .map(Problem.Config.Definition(file, _))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def system(
      d: AgentDef,
      fragments: Map[FragmentName, FragmentBody],
      skills: Map[SkillDef.Name, SkillDef],
      style: ToolStyle
  ): Either[Problems, String] =
    val keep = headlessAudience(d)
    val file = agentFile(d.name)
    val parts = d.include.map { i =>
      val name = if i.value == WritingRules then WritingGuide else i.value
      fragments
        .get(FragmentName(name))
        .toRight(Problem.Config.Definition(file, Problem.Config.Flaw.IncludeMissing(Problem.Raw.of(i))))
        .map(f => forAudience(AgentDef.Body(f.value), keep).value.trim)
        .map(f =>
          if i.value == WritingGuide then s"$GuideHeading\n\n$f"
          else if i.value == WritingRules then s"$RulesHeading\n\n$f"
          else f
        )
    } ++ d.skills.map { s =>
      skills
        .get(SkillDef.Name(s.value))
        .toRight(Problem.Config.Definition(file, Problem.Config.Flaw.SkillMissing(Problem.Raw.of(s))))
        .map(sk => s"# Skill: ${sk.name}\n\n${forAudience(sk.body, keep).value.trim}")
    }
    Problems.from(parts.collect { case Left(m) => m }.toVector) match
      case None =>
        Right(
          render((forAudience(d.body, keep).value.trim :: parts.collect { case Right(p) => p }).mkString("\n\n"), style)
        )
      case Some(problems) => Left(problems)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def headlessAudience(d: AgentDef): Set[Audience] =
    if d.include.exists(_.value == WritingGuide) then Set(Audience.Headless, Audience.Writer)
    else Set(Audience.Headless)

  val PluginVersion = "0.3.0"

  val HeadlessPlugin = "histmersion-agents"

  val InteractivePlugin = "histmersion"

  val LocalMarketplace = "histmersion-local"

  val TokenEnv = "HISTMERSION_RUN_TOKEN"

  val QuotaUrlEnv = "HISTMERSION_QUOTA_URL"

  final case class Defs(
      agents: List[AgentDef],
      fragments: Map[FragmentName, FragmentBody],
      skills: Map[SkillDef.Name, SkillDef]
  ):
    def system(d: AgentDef, style: ToolStyle): String =
      AgentDefinitions
        .system(d, fragments, skills, style)
        .fold(ps => throw IllegalStateException(ps.toVector.map(_.sentence).mkString("; ")), identity)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def check(d: Defs, known: Set[String]): List[Problem] =
    def definition(file: String, flaw: Problem.Config.Flaw): Problem =
      Problem.Config.Definition(Problem.Raw(file), flaw)
    val markerFlaws =
      (d.agents.map(a => s"agents/${a.name}.md" -> a.body) ++ d.fragments.toList
        .sortBy(_._1.value)
        .map((n, t) => s"agents/_shared/$n.md" -> AgentDef.Body(t.value)))
        .flatMap((where, text) => markerProblems(text).map(definition(where, _)))
    val agentProblems = markerFlaws ++ d.agents.flatMap { a =>
      system(a, d.fragments, d.skills, ToolStyle.Bare).left.toOption.toList.flatMap(_.toVector) ++
        (placeholders(a.body.value) ++ a.include.flatMap(i =>
          d.fragments
            .get(FragmentName(i.value))
            .toList
            .flatMap(f => placeholders(f.value))
        ))
          .filterNot(known)
          .map(t => definition(s"agents/${a.name}.md", Problem.Config.Flaw.ToolUnknown(Problem.Raw(t))))
    }
    val skillProblems = d.skills.values.toList.sortBy(_.name.value).flatMap { s =>
      placeholders(s.header.mkString("\n") + "\n" + s.body)
        .filterNot(known)
        .map(t => definition(s"skills/${s.name}/SKILL.md", Problem.Config.Flaw.ToolUnknown(Problem.Raw(t)))) ++
        Option.when(s.description.length > 1024)(
          definition(s"skills/${s.name}/SKILL.md", Problem.Config.Flaw.DescriptionLong)
        )
    }
    agentProblems ++ skillProblems

  def pretty(j: Json): String = j.written + "\n"

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def skillFile(s: SkillDef, style: ToolStyle, keepAllowedTools: Boolean): String =
    val header = s.header.filter(l => keepAllowedTools || !l.value.startsWith("allowed-tools:"))
    render(s"---\n${header.mkString("\n")}\n---\n${s.body}", style)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def skills(d: Defs, prefix: String, style: ToolStyle, keepAllowedTools: Boolean): Map[String, String] =
    d.skills.values.map(s => s"$prefix/${s.name}/SKILL.md" -> skillFile(s, style, keepAllowedTools)).toMap

  private def yamlString(s: String): String = s.asJson.noSpaces

  def claudeWebTools(w: WebAccess): List[String] = w match
    case WebAccess.WebSearch => List("WebSearch", "WebFetch")
    case WebAccess.Fetch     => List("WebFetch")
    case _                   => Nil

  val ClaudeAnswerTool = "StructuredOutput"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def claudeTurns(maxTurns: AgentDef.MaxTurns): AgentDef.MaxTurns =
    AgentDef.MaxTurns(math.max(1, maxTurns.value) + 1)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def claudeAgent(d: Defs, a: AgentDef): String =
    val tools =
      a.tools.map(t => ToolStyle.Claude.tool(ToolUse.Tool(t.value))) ++ claudeWebTools(a.web) :+ ClaudeAnswerTool
    s"---\nname: ${a.name}\ndescription: ${yamlString(a.description.value)}\ntools: ${tools.mkString(", ")}\n" +
      s"maxTurns: ${claudeTurns(a.maxTurns)}\n---\n${d.system(a, ToolStyle.Claude)}\n"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def claudeSettings(a: AgentDef): String =
    val allow =
      a.tools.map(t => ToolStyle.Claude.tool(ToolUse.Tool(t.value))) ++ claudeWebTools(a.web) :+ ClaudeAnswerTool
    val deny = List("Bash", "Edit", "Write", "NotebookEdit", "Agent") ++
      List("WebFetch", "WebSearch").filterNot(claudeWebTools(a.web).contains)
    pretty(
      Json.obj(
        "disableClaudeAiConnectors" -> Json.True,
        "permissions" -> Json.obj("defaultMode" -> "dontAsk".asJson, "allow" -> allow.asJson, "deny" -> deny.asJson)
      )
    )

  def pluginJson(name: String, version: String, description: String): String =
    pretty(
      Json.obj(
        "name" -> name.asJson,
        "description" -> description.asJson,
        "version" -> version.asJson,
        "author" -> Json.obj("name" -> "Mateusz Kubuszok".asJson),
        "homepage" -> "https://github.com/histmersion/histmersion".asJson,
        "keywords" -> List("research", "history", "archaeology", "mythology", "mcp", "postgis").asJson
      )
    )

  private val InteractiveDescription =
    "Query and enrich the Histmersion research corpus (comparative mythology, folklore, Bronze-Age history, " +
      "Indo-European origins) over MCP and the histmersion CLI, instead of grepping markdown files."

  def marketplaceJson(name: String, source: String, version: String): String =
    pretty(
      Json.obj(
        "name" -> name.asJson,
        "owner" -> Json.obj("name" -> "Mateusz Kubuszok".asJson),
        "metadata" -> Json.obj(
          "description" -> "Local marketplace for the Histmersion plugin — MCP tools and skills for the research corpus.".asJson
        ),
        "plugins" -> Json.arr(
          Json.obj(
            "name" -> InteractivePlugin.asJson,
            "source" -> source.asJson,
            "description" -> InteractiveDescription.asJson,
            "version" -> version.asJson,
            "category" -> "research".asJson
          )
        )
      )
    )

  def mcpJson(url: String): String =
    pretty(
      Json.obj(
        "mcpServers" -> Json.obj(McpServer -> Json.obj("type" -> "http".asJson, "url" -> url.asJson))
      )
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def interactiveAgent(a: AgentDef): Option[String] =
    a.interactive.filter(_ => a.runsOn(AgentRuntime.Interactive)).map { i =>
      s"---\nname: ${a.name}\ndescription: ${a.description}\nmodel: ${i.model}\ntools: ${i.tools.mkString(", ")}\n---\n" +
        render(forAudience(a.body, Set(Audience.Interactive)).value, ToolStyle.Claude)
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def interactivePlugin(d: Defs, s: GenSettings): Map[String, String] =
    Map(".claude-plugin/plugin.json" -> pluginJson(InteractivePlugin, s.version.value, InteractiveDescription)) ++
      d.agents.flatMap(a => interactiveAgent(a).map(t => s"agents/${a.name}.md" -> t)) ++
      skills(d, "skills", ToolStyle.Claude, keepAllowedTools = true) ++
      s.mcpUrl.map(u => ".mcp.json" -> mcpJson(u.value)) ++
      (if s.interactiveMod.value then modFiles(QuotaMod.Reporting) else Map.empty)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def repoPlugin(d: Defs): Map[String, String] =
    val s = GenSettings(
      GenSettings.AgentMcpUrl(""),
      None,
      GenSettings.QuotaUrl(""),
      claudeMod = GenSettings.ClaudeMod(false),
      interactiveMod = GenSettings.InteractiveMod(false),
      GenSettings.Version(PluginVersion)
    )
    interactivePlugin(d, s).map((k, v) => s"plugin/$k" -> v) +
      (".claude-plugin/marketplace.json" -> marketplaceJson(InteractivePlugin, "./plugin", PluginVersion))

  enum QuotaMod derives CanEqual:
    case Guard, Reporting

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def modFiles(m: QuotaMod): Map[String, String] =
    Map(
      "hooks/hooks.json" -> "{ \"modules\": [\"./quota-guard.ts\"] }\n",
      "hooks/quota-guard.ts" -> source(m).value
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def claudeCode(d: Defs, s: GenSettings): Map[String, String] =
    val headless = d.agents.filter(_.runsOn(AgentRuntime.ClaudeCode))
    val hl = Map(
      "headless/.claude-plugin/plugin.json" -> pluginJson(
        HeadlessPlugin,
        s.version.value,
        "Generated by Histmersion from agents/*.md and skills/*: the embedded agents' roles, MCP-only."
      )
    ) ++
      headless.map(a => s"headless/agents/${a.name}.md" -> claudeAgent(d, a)) ++
      skills(d, "headless/skills", ToolStyle.Claude, keepAllowedTools = false) ++
      (if s.claudeMod.value then modFiles(QuotaMod.Guard).map((k, v) => s"headless/$k" -> v) else Map.empty)
    val roles = headless.map(a => s"roles/${a.name}/settings.json" -> claudeSettings(a))
    val market =
      Map(
        ".claude-plugin/marketplace.json" -> marketplaceJson(LocalMarketplace, s"./$InteractivePlugin", s.version.value)
      ) ++
        interactivePlugin(d, s).map((k, v) => s"$InteractivePlugin/$k" -> v)
    (hl ++ roles ++ market).map((k, v) => s"claude-code/$k" -> v)

  def tomlString(s: String): String =
    val b = StringBuilder("\"")
    s.foreach {
      case '"'                        => b.append("\\\"")
      case '\\'                       => b.append("\\\\")
      case '\n'                       => b.append("\\n")
      case '\r'                       => b.append("\\r")
      case '\t'                       => b.append("\\t")
      case c if c < 0x20 || c == 0x7f => b.append(f"\\u${c.toInt}%04x")
      case c                          => b.append(c)
    }
    b.append('"').toString

  private def tomlList(xs: List[String]): String = xs.map(tomlString).mkString("[", ", ", "]")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def codexBase(s: GenSettings): String =
    val outputLimit =
      if s.codexToolOutputTokens.value > 0 then s"tool_output_token_limit = ${s.codexToolOutputTokens}\n" else ""
    val instructions = if s.codexLeanBase.value then s"model_instructions_file = ${tomlString(CodexBaseFile)}\n" else ""
    s"""# Generated by Histmersion — the CODEX_HOME of its agent runs. Do not edit: regenerated on start.
       |web_search = "disabled"
       |sandbox_mode = "read-only"
       |approval_policy = "never"
       |check_for_update_on_startup = false
       |include_environment_context = false
       |include_permissions_instructions = false
       |include_collaboration_mode_instructions = false
       |$outputLimit$instructions
       |[skills]
       |include_instructions = false
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
       |[mcp_servers.$McpServer]
       |url = ${tomlString(s.agentMcpUrl.value)}
       |bearer_token_env_var = "$TokenEnv"
       |required = true
       |enabled_tools = []
       |""".stripMargin

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def codexProfile(d: Defs, a: AgentDef): String =
    val web = a.web match
      case WebAccess.WebSearch | WebAccess.Fetch => "web_search = \"live\"\n"
      case _                                     => ""
    val mcp =
      if a.tools.isEmpty then s"[mcp_servers.$McpServer]\nenabled = false\nrequired = false\n"
      else
        s"[mcp_servers.$McpServer]\nenabled_tools = ${tomlList(a.tools.map(_.value))}\n" +
          a.tools
            .map(t => s"\n[mcp_servers.$McpServer.tools.${t.value}]\napproval_mode = \"approve\"\n")
            .mkString
    s"# Generated by Histmersion from agents/${a.name}.md — `codex exec -p ${a.name}`.\n" +
      s"developer_instructions = ${tomlString(d.system(a, ToolStyle.Bare))}\n$web\n$mcp"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def codexAgent(d: Defs, a: AgentDef): String =
    s"name = ${tomlString(a.name.value)}\ndescription = ${tomlString(a.description.value)}\n" +
      s"developer_instructions = ${tomlString(d.system(a, ToolStyle.Bare))}\n"

  val CodexBaseFile = "base-instructions.md"

  val CodexBaseFragment = "codex-base-instructions"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def codexBaseInstructions(d: Defs, s: GenSettings): String =
    val text = d.fragments.getOrElse(
      FragmentName(CodexBaseFragment),
      throw IllegalStateException(s"agents/_shared/$CodexBaseFragment.md is missing")
    )
    val base = render(text.value.trim, ToolStyle.Bare)
    if s.codexToolOutputTokens.value <= 0 then base + "\n"
    else
      val budget = s"- Start every script with `// @exec: {\"max_output_tokens\": ${s.codexToolOutputTokens}}`."
      val at = base.indexOf("\n\n# Answer")
      (if at < 0 then s"$base\n\n$budget" else base.substring(0, at) + "\n" + budget + base.substring(at)) + "\n"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def codex(d: Defs, s: GenSettings): Map[String, String] =
    val roles = d.agents.filter(_.runsOn(AgentRuntime.Codex))
    val home = Map("home/config.toml" -> codexBase(s)) ++
      (if s.codexLeanBase.value then Map(s"home/$CodexBaseFile" -> codexBaseInstructions(d, s)) else Map.empty) ++
      roles.map(a => s"home/${a.name}.config.toml" -> codexProfile(d, a)) ++
      roles.map(a => s"home/agents/${a.name}.toml" -> codexAgent(d, a)) ++
      skills(d, "home/skills", ToolStyle.Bare, keepAllowedTools = false)
    val plugin = Map(
      "plugin/.codex-plugin/plugin.json" -> pretty(
        Json
          .obj(
            "name" -> InteractivePlugin.asJson,
            "version" -> Json.fromString(s.version.value),
            "description" -> InteractiveDescription.asJson,
            "skills" -> "./skills".asJson
          )
          .deepMerge(s.mcpUrl.fold(Json.obj())(_ => Json.obj("mcpServers" -> "./.mcp.json".asJson)))
      )
    ) ++
      skills(d, "plugin/skills", ToolStyle.Bare, keepAllowedTools = false) ++
      s.mcpUrl.map(u => "plugin/.mcp.json" -> mcpJson(u.value))
    (home ++ plugin).map((k, v) => s"codex/$k" -> v)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def opencodePermissions(a: AgentDef): List[(String, String)] =
    ("*" -> "deny") :: a.tools.map(t => ToolStyle.Opencode.tool(ToolUse.Tool(t.value)) -> "allow") ++ (a.web match
      case WebAccess.WebSearch => List("webfetch" -> "allow", "websearch" -> "allow")
      case WebAccess.Fetch     => List("webfetch" -> "allow")
      case _                   => Nil)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def opencodeAgent(d: Defs, a: AgentDef): String =
    val perms = opencodePermissions(a).map((k, v) => s"  ${yamlString(k)}: $v").mkString("\n")
    s"---\ndescription: ${yamlString(a.description.value)}\nmode: primary\nsteps: ${a.maxTurns}\npermission:\n$perms\n---\n" +
      d.system(a, ToolStyle.Opencode) + "\n"

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def opencodeConfig(url: String, headless: Boolean): String =
    val server = Json
      .obj("type" -> "remote".asJson, "url" -> url.asJson, "enabled" -> Json.True)
      .deepMerge(
        if headless then Json.obj("headers" -> Json.obj("Authorization" -> s"Bearer {env:$TokenEnv}".asJson))
        else Json.obj()
      )
    pretty(
      Json
        .obj(
          "$schema" -> "https://opencode.ai/config.json".asJson,
          "autoupdate" -> Json.False,
          "share" -> "disabled".asJson,
          "mcp" -> Json.obj(McpServer -> server)
        )
        .deepMerge(if headless then Json.obj("permission" -> Json.obj("*" -> "deny".asJson)) else Json.obj())
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def opencode(d: Defs, s: GenSettings): Map[String, String] =
    val roles = d.agents.filter(_.runsOn(AgentRuntime.Opencode))
    val runs = Map(
      "xdg/opencode/opencode.json" -> pretty(Json.obj("$schema" -> "https://opencode.ai/config.json".asJson)),
      "config/opencode.json" -> opencodeConfig(s.agentMcpUrl.value, headless = true)
    ) ++
      roles.map(a => s"config/agents/${a.name}.md" -> opencodeAgent(d, a)) ++
      skills(d, "config/skills", ToolStyle.Opencode, keepAllowedTools = false)
    val interactive =
      s.mcpUrl.map(u => "interactive/opencode.json" -> opencodeConfig(u.value, headless = false)).toMap ++
        skills(d, "interactive/skills", ToolStyle.Opencode, keepAllowedTools = false)
    (runs ++ interactive).map((k, v) => s"opencode/$k" -> v)

  def tree(d: Defs, s: GenSettings, runtimes: List[AgentRuntime]): Map[String, String] =
    runtimes.distinct
      .map {
        case AgentRuntime.ClaudeCode => claudeCode(d, s)
        case AgentRuntime.Codex      => codex(d, s)
        case AgentRuntime.Opencode   => opencode(d, s)
        case _                       => Map.empty[String, String]
      }
      .foldLeft(Map.empty[String, String])(_ ++ _)

  type SourceLabel = SourceLabel.T
  object SourceLabel extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def source(m: QuotaMod): SourceLabel =
    val guard = m == QuotaMod.Guard
    SourceLabel(
      s"""// Generated by Histmersion — the ${
          if guard then "quota guard of its headless runs" else "plan-usage reporter of your own sessions"
        }.
       |// Do not edit: regenerated by the server (histmersion agent generate).
       |import type { Register } from 'claude-code'
       |
       |let blocked: string | undefined
       |
       |async function report($$: any, rateLimits: unknown) {
       |  const url = await $$.env.get('$QuotaUrlEnv')
       |  if (!url) return
       |  const token = ${if guard then s"await $$.env.get('$TokenEnv')" else "await $.env.get('HISTMERSION_API_KEY')"}
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
       |""".stripMargin + (if guard then s"""  on('tool.call', async ($$, e, next) => {
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
    )

final class AgentDefinitions(prompts: Prompts, val roster: AgentRoster):
  import AgentDefinitions.agentFile
  val tools: List[ToolName] = roster.tools

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def index: List[AgentDefinitions.ResourcePath] =
    prompts
      .bundledText(SafePath.Candidate("index.txt"))
      .toOption
      .toList
      .flatMap(_.linesIterator.map(_.trim).filter(_.nonEmpty).map(AgentDefinitions.ResourcePath(_)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
  def agentNames: List[AgentDef.Name] =
    index
      .map(_.value)
      .filter(r => !r.contains('/') && r.endsWith(".md"))
      .map(r => AgentDef.Name(r.stripSuffix(".md")))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def skillNames: List[SkillDef.Name] =
    index.map(_.value).collect { case r if r.startsWith("skills/") => SkillDef.Name(r.split('/')(1)) }.distinct
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fragmentNames: List[AgentDefinitions.FragmentName] =
    index.map(_.value).collect {
      case r if r.startsWith("_shared/") =>
        AgentDefinitions.FragmentName(r.stripPrefix("_shared/").stripSuffix(".md"))
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def agent(name: AgentDef.Name): Either[Problem, AgentDef] =
    prompts
      .load(SafePath.Candidate(s"$name.md"))
      .flatMap(text => AgentDefinitions.parse(name, AgentDefinitions.Markdown(text), tools))
      .flatMap(d =>
        Either.cond(
          roster.roles.contains(
            io.scalaland.chimney.Transformer
              .derive[AgentRole, domain.automation.AgentRole]
              .transform(d.role)
          ),
          d,
          Problem.Config.Definition(
            agentFile(name),
            Problem.Config.Flaw
              .RoleUnused(Problem.Raw.of(d.role.wire), roster.roles.toVector.map(r => Problem.Raw.of(r.wire)))
          )
        )
      )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def agent(s: StepAgent): Either[Problem, AgentDef] = agent(AgentDef.Name(s.wire.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def skill(name: SkillDef.Name): Either[Problem, SkillDef] =
    prompts
      .load(SafePath.Candidate(s"skills/$name/SKILL.md"))
      .flatMap(text => AgentDefinitions.parseSkill(name, AgentDefinitions.Markdown(text)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fragments: Either[Problems, Map[AgentDefinitions.FragmentName, AgentDefinitions.FragmentBody]] =
    all(fragmentNames)(n =>
      prompts
        .load(SafePath.Candidate(s"_shared/$n.md"))
        .map(text => n -> AgentDefinitions.FragmentBody(text))
    ).map(_.toMap)
  def skills: Either[Problems, Map[SkillDef.Name, SkillDef]] = all(skillNames)(n => skill(n).map(n -> _)).map(_.toMap)
  def agents: Either[Problems, List[AgentDef]] = all(agentNames)(agent)

  def system(d: AgentDef, style: ToolStyle): Either[Problems, String] =
    for
      fs <- fragments
      ss <- skills
      s <- AgentDefinitions.system(d, fs, ss, style)
    yield s

  private def all[A, B](as: List[A])(f: A => Either[Problem, B]): Either[Problems, List[B]] =
    as.traverseAll(f).map(_.toList)
