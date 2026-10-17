package com.histmersion.server.agents

import com.histmersion.core.Agents.Role
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** Where an agent definition is run, which decides how it is rendered (docs/research-agent-runtimes.md
  * §2): the three CLI runtimes, the direct API (sttp-ai, the engine's own loop), and the person's own
  * interactive Claude Code session (the plugin the research workspace installs). */
enum AgentRuntime(val wire: String):
  case ClaudeCode  extends AgentRuntime("claude-code")
  case Codex       extends AgentRuntime("codex")
  case Opencode    extends AgentRuntime("opencode")
  case Api         extends AgentRuntime("api")
  case Interactive extends AgentRuntime("interactive")

  /** How a `{{tool:x}}` placeholder reads in this runtime. */
  def style: ToolStyle = this match
    case ClaudeCode | Interactive => ToolStyle.Claude
    case Opencode                 => ToolStyle.Opencode
    case Codex | Api              => ToolStyle.Bare

object AgentRuntime:
  def parse(s: String): Option[AgentRuntime] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** The runtimes the generator writes a tree for (the API reads the definitions in process). */
  val Generated: List[AgentRuntime] = List(ClaudeCode, Codex, Opencode)

/** The name a runtime gives one of Histmersion's MCP tools: Claude Code `mcp__histmersion__get_source`,
  * opencode `histmersion_get_source`, Codex and the direct API the bare `get_source`. */
enum ToolStyle:
  case Claude, Opencode, Bare

  def tool(name: String): String = this match
    case Claude   => s"mcp__${AgentDefinitions.McpServer}__$name"
    case Opencode => s"${AgentDefinitions.McpServer}_$name"
    case Bare     => name

/** Which of the runtime's own web tools a role may use: none, Histmersion's own `discover` (OpenAlex,
  * Crossref, Wikipedia references — still MCP only), or the runtime's web search / fetch. */
enum WebAccess(val wire: String):
  case None     extends WebAccess("none")
  case Discover extends WebAccess("discover")
  case Search   extends WebAccess("search")
  case Fetch    extends WebAccess("fetch")

/** What a role may change: nothing; a typed answer code writes (`propose` — every embedded step); or
  * directly (an interactive role). */
enum Writes(val wire: String):
  case None    extends Writes("none")
  case Propose extends Writes("propose")
  case Direct  extends Writes("direct")

/** How the agent shows in the person's own Claude Code (the interactive plugin): its model and its
  * built-in tools, exactly as the hand-written definitions had them. */
final case class InteractiveSpec(model: String, tools: List[String])

/** One canonical agent definition, `agents/<name>.md`: a runtime-neutral manifest in the YAML header
  * and the prompt body, which speaks of tools by `{{tool:<name>}}` placeholder. */
final case class AgentDef(
    name: String,
    description: String,
    role: Role,
    skills: List[String],
    include: List[String],
    tools: List[ToolName],
    web: WebAccess,
    writes: Writes,
    maxTurns: Int,
    answer: Option[String],
    runtimes: Set[AgentRuntime],
    interactive: Option[InteractiveSpec],
    body: String):
  def runsOn(r: AgentRuntime): Boolean = runtimes.contains(r)

/** One canonical skill, `skills/<name>/SKILL.md`: its YAML header as written (kept line by line, so
  * the rendered file stays the author's) and its body. */
final case class SkillDef(name: String, header: List[String], body: String):
  def description: String =
    header.find(_.startsWith("description:")).map(_.stripPrefix("description:").trim).getOrElse("")

/** Who a marked part of a definition is for (`<!-- only:<audience> -->` … `<!-- /only:<audience> -->`):
  * the person's own session, which runs the `histmersion` CLI in Bash and writes its answer as the JSON
  * block of its Output section; a headless step, which calls MCP tools and answers through the
  * response schema; and the writer of an article — the parts of the writing guide (its phases, its
  * self-check) that a reviewer or a fixer does not act on. Every turn of a step re-sends its prompt,
  * so what a role does not act on is left out of it (docs/research-agent-runtimes.md §4). */
enum Audience(val wire: String):
  case Interactive extends Audience("interactive")
  case Headless    extends Audience("headless")
  case Writer      extends Audience("writer")

/** The steps of the engine's pipelines, each run as one agent definition. A test holds every one of
  * them to a definition that parses. */
enum StepAgent(val wire: String):
  case Probe            extends StepAgent("probe")
  case EnrichClassify   extends StepAgent("enrich-classify")
  case EnrichFindings   extends StepAgent("enrich-findings")
  case EnrichNotes      extends StepAgent("enrich-notes")
  case RulesReviewer    extends StepAgent("article-rules-reviewer")
  case ColdReader       extends StepAgent("article-cold-reader")
  case Skeptic          extends StepAgent("article-skeptic")
  case Fixer            extends StepAgent("article-fixer")
  case GoalPlanner      extends StepAgent("goal-planner")
  case CompileExtractor extends StepAgent("compile-extractor")
  case CompileWriter    extends StepAgent("compile-writer")
  case AcquisitionRanker extends StepAgent("acquisition-ranker")
  case FilterInferrer   extends StepAgent("filter-inferrer")
  case ArticlePlanner   extends StepAgent("article-planner")
  case HintReader       extends StepAgent("hint-reader")

/** The pure half of the definitions: parsing a definition and a skill, the placeholders, and the
  * system prompt a headless run of a definition gets (the same function for every runtime, so the
  * generated Claude Code agent and the prompt the engine sends the API cannot drift). */
object AgentDefinitions:

  /** The MCP server's name in every runtime's configuration. */
  val McpServer = "histmersion"

  /** The include fragment that is the writing guide, and the heading it is sent under. */
  val WritingGuide = "writing-guide"
  private val GuideHeading = "# The writing guide (docs/writing-guide.md)"

  /** The include that is the writing guide WITHOUT its writer-only parts (the phases' procedure, the
    * self-check): every rule an article is held to, for the roles that check or fix one. */
  val WritingRules = "writing-rules"
  private val RulesHeading = "# The writing guide (docs/writing-guide.md): the rules an article is held to"

  private val Marked = """(?s)<!-- only:([a-z]+) -->\n?(.*?)<!-- /only:\1 -->\n?""".r
  private val Marker = """<!-- (/?)only:([a-z]+) -->""".r

  /** `text` as `keep` reads it: a part marked for an audience not in `keep` is removed, a kept one
    * loses its markers. Removing a part never leaves more than one blank line behind. */
  def forAudience(text: String, keep: Set[Audience]): String =
    if !text.contains("<!-- only:") then text
    else
      val out = Marked.replaceAllIn(text, m => java.util.regex.Matcher.quoteReplacement(
        if keep.exists(_.wire == m.group(1)) then m.group(2) else ""))
      out.replaceAll("\n{3,}", "\n\n")

  /** Audience markers that do not pair up or name no audience — a part that would leak into the wrong
    * prompt. */
  def markerProblems(text: String): List[String] =
    val ms = Marker.findAllMatchIn(text).map(m => (m.group(1) == "/", m.group(2))).toList
    val unknown = ms.map(_._2).distinct.filterNot(a => Audience.values.exists(_.wire == a)).map(a => s"names no audience: only:$a")
    val (open, errs) = ms.foldLeft((Option.empty[String], List.empty[String])) {
      case ((None, e), (false, a))                 => (Some(a), e)
      case ((Some(o), e), (true, a)) if o == a     => (None, e)
      case ((Some(o), e), (false, a))              => (Some(a), e :+ s"only:$a opens inside only:$o")
      case ((o, e), (true, a))                     => (o, e :+ s"/only:$a closes nothing open")
    }
    unknown ++ errs ++ open.map(o => s"only:$o is never closed").toList

  private val Placeholder = """\{\{tool:([a-z_]+|\*)\}\}""".r

  /** `{{tool:x}}` → the runtime's name for tool `x`; `{{tool:*}}` → every Histmersion tool. */
  def render(text: String, style: ToolStyle): String =
    Placeholder.replaceAllIn(text, m => java.util.regex.Matcher.quoteReplacement(m.group(1) match
      case "*" => style match
        case ToolStyle.Bare => "the histmersion tools"
        case s              => s.tool("*")
      case t => style.tool(t)))

  /** Every tool a text names by placeholder (`*` left out). */
  def placeholders(text: String): List[String] =
    Placeholder.findAllMatchIn(text).map(_.group(1)).filter(_ != "*").toList.distinct

  /** `---\n<yaml>\n---\n<body>` → (yaml, body); the body keeps its leading newline, as written. */
  def split(md: String): Either[String, (String, String)] =
    val t = md.replace("\r\n", "\n")
    if !t.startsWith("---\n") then Left("no YAML header (--- … ---)")
    else t.indexOf("\n---\n", 3) match
      case -1 => Left("the YAML header is not closed")
      case i  => Right((t.substring(4, i), t.substring(i + 5)))

  private def yaml(text: String): Either[String, Map[String, Any]] =
    try
      Option(Yaml(SafeConstructor(LoaderOptions())).load[Any](text)).map(_.asMatchable) match
        case Some(m: java.util.Map[?, ?]) => Right(m.asScala.toMap.map((k, v) => k.toString -> v))
        case _                            => Left("the YAML header is not a mapping")
    catch case e: Exception => Left(s"the YAML header does not parse: ${e.getMessage.linesIterator.nextOption().getOrElse("")}")

  private def strings(v: Any): Either[String, List[String]] = v.asMatchable match
    case null                  => Right(Nil)
    case l: java.util.List[?]  => Right(l.asScala.toList.map(String.valueOf))
    case s: String if s.trim.isEmpty => Right(Nil)
    case other                 => Left(s"expected a list, not '$other'")

  /** Parse one definition. `name` is its file name; the header's `name` must agree. */
  def parse(name: String, md: String): Either[String, AgentDef] =
    def fail(m: String) = Left(s"agents/$name.md: $m")
    split(md).flatMap((h, body) => yaml(h).map(_ -> body)).left.map(m => s"agents/$name.md: $m").flatMap { (m, body) =>
      def str(k: String) = m.get(k).map(String.valueOf).map(_.trim).filter(_.nonEmpty)
      val tools = m.get("tools").map(_.asMatchable) match
        case Some(t: java.util.Map[?, ?]) => t.asScala.toMap.map((k, v) => k.toString -> v)
        case _                            => Map.empty[String, Any]
      val interactive = m.get("interactive").map(_.asMatchable) match
        case Some(i: java.util.Map[?, ?]) =>
          val im = i.asScala.toMap.map((k, v) => k.toString -> v)
          strings(im.getOrElse("tools", null)).map(ts => Some(InteractiveSpec(String.valueOf(im.getOrElse("model", "")), ts)))
        case _ => Right(None)
      for
        n     <- str("name").toRight("no name").flatMap(n => Either.cond(n == name, n, s"name '$n' is not the file's"))
                   .left.flatMap(fail)
        desc  <- str("description").toRight(s"agents/$name.md: no description")
        role  <- str("model-role").flatMap(Role.parse).toRight(s"agents/$name.md: model-role is not one of ${Role.values.map(_.wire).mkString(", ")}")
        sk    <- strings(m.getOrElse("skills", null)).left.flatMap(e => fail(s"skills: $e"))
        inc   <- strings(m.getOrElse("include", null)).left.flatMap(e => fail(s"include: $e"))
        hm    <- strings(tools.getOrElse("histmersion", null)).left.flatMap(e => fail(s"tools.histmersion: $e"))
        ts    <- hm.map(t => ToolName.values.find(_.wire == t).toRight(t)).partitionMap(identity) match
                   case (Nil, ok) => Right(ok)
                   case (bad, _)  => fail(s"tools.histmersion names tools an agent cannot be offered: ${bad.mkString(", ")} " +
                                       s"(one of ${ToolName.values.map(_.wire).mkString(", ")})")
        web   <- WebAccess.values.find(w => tools.get("web").map(String.valueOf).getOrElse("none") == w.wire)
                   .toRight(s"agents/$name.md: tools.web is none, discover, search or fetch")
        wr    <- Writes.values.find(w => str("writes").getOrElse("none") == w.wire)
                   .toRight(s"agents/$name.md: writes is none, propose or direct")
        turns <- str("max-turns").map(_.toIntOption.filter(_ > 0).toRight(s"agents/$name.md: max-turns is a positive number"))
                   .getOrElse(Right(6))
        rts   <- strings(m.getOrElse("runtimes", null)).left.flatMap(e => fail(s"runtimes: $e")).flatMap { rs =>
                   rs.map(r => AgentRuntime.parse(r).toRight(r)).partitionMap(identity) match
                     case (Nil, ok) if ok.nonEmpty => Right(ok.toSet)
                     case (Nil, _)  => Right(AgentRuntime.values.toSet - AgentRuntime.Interactive)
                     case (bad, _)  => fail(s"unknown runtime(s) ${bad.mkString(", ")}")
                 }
        it    <- interactive.left.flatMap(e => fail(s"interactive.tools: $e"))
        _     <- Either.cond(!rts.contains(AgentRuntime.Interactive) || it.isDefined, (),
                   s"agents/$name.md: an interactive agent needs `interactive: {model, tools}`")
      yield AgentDef(n, desc, role, sk, inc, ts, web, wr, turns, str("answer"), rts, it, body)
    }

  /** Parse one skill (`skills/<name>/SKILL.md`). */
  def parseSkill(name: String, md: String): Either[String, SkillDef] =
    split(md).left.map(m => s"skills/$name/SKILL.md: $m").flatMap { (h, body) =>
      yaml(h).left.map(m => s"skills/$name/SKILL.md: $m").flatMap { m =>
        Either.cond(m.get("name").map(String.valueOf).contains(name), SkillDef(name, h.split('\n').toList, body),
          s"skills/$name/SKILL.md: its name is not '$name'")
      }
    }

  /** The system prompt of a headless run of `d`: its body, its include fragments in order (the
    * writing guide under its heading), then the skills it lists, inlined — a main-thread Claude Code
    * agent never gets its `skills:` preloaded, and Codex cannot open a skill without a shell, so
    * inlining is what every runtime has in common (§2.3, §3a). Placeholders rendered for `style`. */
  def system(d: AgentDef, fragments: Map[String, String], skills: Map[String, SkillDef], style: ToolStyle): Either[String, String] =
    val keep = headlessAudience(d)
    val parts = d.include.map { i =>
      val name = if i == WritingRules then WritingGuide else i
      fragments.get(name).toRight(s"agents/${d.name}.md includes '$i', which is not in agents/_shared/")
        .map(f => forAudience(f, keep).trim)
        .map(f => if i == WritingGuide then s"$GuideHeading\n\n$f" else if i == WritingRules then s"$RulesHeading\n\n$f" else f)
    } ++ d.skills.map { s =>
      skills.get(s).toRight(s"agents/${d.name}.md lists skill '$s', which is not in skills/")
        .map(sk => s"# Skill: ${sk.name}\n\n${forAudience(sk.body, keep).trim}")
    }
    parts.collect { case Left(m) => m } match
      case Nil  => Right(render((forAudience(d.body, keep).trim :: parts.collect { case Right(p) => p }).mkString("\n\n"), style))
      case errs => Left(errs.mkString("; "))

  /** Who a headless run of `d` is: a step — and the writer when it includes the whole writing guide. */
  def headlessAudience(d: AgentDef): Set[Audience] =
    if d.include.contains(WritingGuide) then Set(Audience.Headless, Audience.Writer) else Set(Audience.Headless)

/** The definitions as the server reads them: bundled resources (`agents/index.txt` lists them), each
  * file overridable from `agents.prompts-dir` through [[Prompts]]. Read on every use — a definition
  * can be iterated on without a rebuild, and the hash recorded per attempt says which text ran. */
final class AgentDefinitions(prompts: Prompts):

  /** Every bundled file, relative to `agents/`. */
  def index: List[String] =
    Option(getClass.getClassLoader.getResourceAsStream("agents/index.txt"))
      .map(in => try String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) finally in.close())
      .toList.flatMap(_.linesIterator.map(_.trim).filter(_.nonEmpty))

  def agentNames: List[String] = index.filter(r => !r.contains('/') && r.endsWith(".md")).map(_.stripSuffix(".md"))
  def skillNames: List[String] = index.collect { case r if r.startsWith("skills/") => r.split('/')(1) }.distinct
  def fragmentNames: List[String] = index.collect { case r if r.startsWith("_shared/") => r.stripPrefix("_shared/").stripSuffix(".md") }

  def agent(name: String): Either[String, AgentDef] = prompts.load(s"$name.md").flatMap(AgentDefinitions.parse(name, _))
  def agent(s: StepAgent): Either[String, AgentDef] = agent(s.wire)
  def skill(name: String): Either[String, SkillDef] = prompts.load(s"skills/$name/SKILL.md").flatMap(AgentDefinitions.parseSkill(name, _))

  def fragments: Either[String, Map[String, String]] = all(fragmentNames)(n => prompts.load(s"_shared/$n.md").map(n -> _)).map(_.toMap)
  def skills: Either[String, Map[String, SkillDef]] = all(skillNames)(n => skill(n).map(n -> _)).map(_.toMap)
  def agents: Either[String, List[AgentDef]] = all(agentNames)(agent)

  /** The system prompt `d` runs with in a runtime that names tools `style`'s way. */
  def system(d: AgentDef, style: ToolStyle): Either[String, String] =
    for
      fs <- fragments
      ss <- skills
      s  <- AgentDefinitions.system(d, fs, ss, style)
    yield s

  private def all[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    as.map(f).partitionMap(identity) match
      case (Nil, ok) => Right(ok)
      case (errs, _) => Left(errs.mkString("; "))
