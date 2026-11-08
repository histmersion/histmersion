package com.histmersion
package domain.automation.agents

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

/** The pure half of the definitions: parsing a definition and a skill, the placeholders, and the system prompt a
  * headless run of a definition gets (the same function for every runtime, so the generated Claude Code agent and the
  * prompt the engine sends the API cannot drift).
  */
object AgentDefinitions:
  type FragmentBody = FragmentBody.T
  object FragmentBody extends Newtype[String, Pure]
  type FragmentName = FragmentName.T
  object FragmentName extends Newtype[String, Pure]
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]
  type ResourcePath = ResourcePath.T
  object ResourcePath extends Newtype[String, Pure]

  /** The MCP server's name in every runtime's configuration. */


  val McpServer = "histmersion"

  /** The include fragment that is the writing guide, and the heading it is sent under. */
  val WritingGuide = "writing-guide"
  private val GuideHeading = "# The writing guide (docs/writing-guide.md)"

  /** The include that is the writing guide WITHOUT its writer-only parts (the phases' procedure, the self-check): every
    * rule an article is held to, for the roles that check or fix one.
    */
  val WritingRules = "writing-rules"
  private val RulesHeading = "# The writing guide (docs/writing-guide.md): the rules an article is held to"

  private val Marked = """(?s)<!-- only:([a-z]+) -->\n?(.*?)<!-- /only:\1 -->\n?""".r
  private val Marker = """<!-- (/?)only:([a-z]+) -->""".r

  /** `text` as `keep` reads it: a part marked for an audience not in `keep` is removed, a kept one loses its markers.
    * Removing a part never leaves more than one blank line behind.
    */
  def forAudience(text: AgentDef.Body, keep: Set[Audience]): AgentDef.Body =
    if !text.value.contains("<!-- only:") then text
    else
      val out = Marked.replaceAllIn(
        text.value,
        m => java.util.regex.Matcher.quoteReplacement(if keep.exists(_.wire.value == m.group(1)) then m.group(2) else "")
      )
      AgentDef.Body(out.replaceAll("\n{3,}", "\n\n"))

  /** Audience markers that do not pair up or name no audience — a part that would leak into the wrong prompt.
    */
  def markerProblems(text: AgentDef.Body): List[String] =
    val ms = Marker.findAllMatchIn(text.value).map(m => (m.group(1) == "/", m.group(2))).toList
    val unknown =
      ms.map(_._2).distinct.filterNot(a => Audience.values.exists(_.wire.value == a)).map(a => s"names no audience: only:$a")
    @tailrec def pair(rest: List[(Boolean, String)], open: Option[String], errs: List[String]): List[String] =
      (open, rest) match
        case (_, Nil)                               => errs ++ open.map(o => s"only:$o is never closed").toList
        case (None, (false, a) :: tail)             => pair(tail, Some(a), errs)
        case (Some(o), (true, a) :: tail) if o == a => pair(tail, None, errs)
        case (Some(o), (false, a) :: tail)          => pair(tail, Some(a), errs :+ s"only:$a opens inside only:$o")
        case (o, (true, a) :: tail)                 => pair(tail, o, errs :+ s"/only:$a closes nothing open")
    unknown ++ pair(ms, None, Nil)

  private val Placeholder = """\{\{tool:([a-z_]+|\*)\}\}""".r

  /** `{{tool:x}}` → the runtime's name for tool `x`; `{{tool:*}}` → every Histmersion tool. */
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

  /** Every tool a text names by placeholder (`*` left out). */
  def placeholders(text: String): List[String] =
    Placeholder.findAllMatchIn(text).map(_.group(1)).filter(_ != "*").toList.distinct

  /** `---\n<yaml>\n---\n<body>` → (yaml, body); the body keeps its leading newline, as written. */
  def split(md: Markdown): Either[String, (String, String)] =
    val t = md.value.replace("\r\n", "\n")
    if !t.startsWith("---\n") then Left("no YAML header (--- … ---)")
    else
      t.indexOf("\n---\n", 3) match
        case -1 => Left("the YAML header is not closed")
        case i  => Right((t.substring(4, i), t.substring(i + 5)))

  private def yaml(text: String): Either[String, Map[String, Any]] =
    try
      Option(Yaml(SafeConstructor(LoaderOptions())).load[Any](text)).map(_.asMatchable) match
        case Some(m: java.util.Map[?, ?]) => Right(m.asScala.toMap.map((k, v) => k.toString -> v))
        case _                            => Left("the YAML header is not a mapping")
    catch
      case e: Exception =>
        Left(s"the YAML header does not parse: ${e.getMessage.linesIterator.nextOption().getOrElse("")}")

  private def strings(v: Any): Either[String, List[String]] = v.asMatchable match
    case null                        => Right(Nil)
    case l: java.util.List[?]        => Right(l.asScala.toList.map(String.valueOf))
    case s: String if s.trim.isEmpty => Right(Nil)
    case other                       => Left(s"expected a list, not '$other'")

  /** Parse one definition. `name` is its file name; the header's `name` must agree. */
  def parse(name: AgentDef.Name, md: Markdown, known: List[ToolName]): Either[String, AgentDef] =
    def fail(m: String) = Left(s"agents/$name.md: $m")
    split(md).flatMap((h, body) => yaml(h).map(_ -> body)).left.map(m => s"agents/$name.md: $m").flatMap { (m, body) =>
      def str(k: String) = m.get(k).map(String.valueOf).map(_.trim).filter(_.nonEmpty)
      val tools = m.get("tools").map(_.asMatchable) match
        case Some(t: java.util.Map[?, ?]) => t.asScala.toMap.map((k, v) => k.toString -> v)
        case _                            => Map.empty[String, Any]
      val interactive = m.get("interactive").map(_.asMatchable) match
        case Some(i: java.util.Map[?, ?]) =>
          val im = i.asScala.toMap.map((k, v) => k.toString -> v)
          strings(im.getOrElse("tools", null))
            .map(ts => Some(InteractiveSpec(InteractiveSpec.Model(String.valueOf(im.getOrElse("model", ""))), ts.map(InteractiveSpec.Tools(_)))))
        case _ => Right(None)
      for
        n <- str("name")
          .toRight("no name")
          .flatMap(n => Either.cond(n == name.value, n, s"name '$n' is not the file's"))
          .left
          .flatMap(fail)
        desc <- str("description").toRight(s"agents/$name.md: no description")
        role <- str("model-role")
          .flatMap(r => Role.parse(AgentRole.Wire(r)))
          .toRight(s"agents/$name.md: model-role is not one of ${Role.values.map(_.wire.value).mkString(", ")}")
        sk <- strings(m.getOrElse("skills", null)).left.flatMap(e => fail(s"skills: $e"))
        inc <- strings(m.getOrElse("include", null)).left.flatMap(e => fail(s"include: $e"))
        hm <- strings(tools.getOrElse("histmersion", null)).left.flatMap(e => fail(s"tools.histmersion: $e"))
        ts <- hm.map(t => known.find(_.value == t).toRight(t)).partitionMap(identity) match
          case (Nil, ok) => Right(ok)
          case (bad, _)  =>
            fail(
              s"tools.histmersion names tools an agent cannot be offered: ${bad.mkString(", ")} " +
                s"(one of ${known.map(_.value).mkString(", ")})"
            )
        web <- WebAccess.values
          .find(w => tools.get("web").map(String.valueOf).getOrElse("none") == w.wire.value)
          .toRight(s"agents/$name.md: tools.web is none, discover, search or fetch")
        wr <- Writes.values
          .find(w => str("writes").getOrElse("none") == w.wire.value)
          .toRight(s"agents/$name.md: writes is none, propose or direct")
        turns <- str("max-turns")
          .map(_.toIntOption.filter(_ > 0).toRight(s"agents/$name.md: max-turns is a positive number"))
          .getOrElse(Right(6))
        rts <- strings(m.getOrElse("runtimes", null)).left.flatMap(e => fail(s"runtimes: $e")).flatMap { rs =>
          rs.map(r => AgentRuntime.parse(AgentRuntime.Wire(r)).toRight(r)).partitionMap(identity) match
            case (Nil, ok) if ok.nonEmpty => Right(ok.toSet)
            case (Nil, _)                 => Right(AgentRuntime.values.toSet - AgentRuntime.Interactive)
            case (bad, _)                 => fail(s"unknown runtime(s) ${bad.mkString(", ")}")
        }
        it <- interactive.left.flatMap(e => fail(s"interactive.tools: $e"))
        _ <- Either.cond(
          !rts.contains(AgentRuntime.Interactive) || it.isDefined,
          (),
          s"agents/$name.md: an interactive agent needs `interactive: {model, tools}`"
        )
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

  /** Parse one skill (`skills/<name>/SKILL.md`). */
  def parseSkill(name: SkillDef.Name, md: Markdown): Either[String, SkillDef] =
    split(md).left.map(m => s"skills/$name/SKILL.md: $m").flatMap { (h, body) =>
      yaml(h).left.map(m => s"skills/$name/SKILL.md: $m").flatMap { m =>
        Either.cond(
          m.get("name").map(String.valueOf).contains(name.value),
          SkillDef(name, h.split('\n').toList.map(SkillDef.Header(_)), SkillDef.Body(body)),
          s"skills/$name/SKILL.md: its name is not '$name'"
        )
      }
    }

  /** The system prompt of a headless run of `d`: its body, its include fragments in order (the writing guide under its
    * heading), then the skills it lists, inlined — a main-thread Claude Code agent never gets its `skills:` preloaded,
    * and Codex cannot open a skill without a shell, so inlining is what every runtime has in common (§2.3, §3a).
    * Placeholders rendered for `style`.
    */
  def system(
      d: AgentDef,
      fragments: Map[FragmentName, FragmentBody],
      skills: Map[SkillDef.Name, SkillDef],
      style: ToolStyle
  ): Either[String, String] =
    val keep = headlessAudience(d)
    val parts = d.include.map { i =>
      val name = if i.value == WritingRules then WritingGuide else i.value
      fragments
        .get(FragmentName(name))
        .toRight(s"agents/${d.name}.md includes '$i', which is not in agents/_shared/")
        .map(f => forAudience(AgentDef.Body(f.value), keep).value.trim)
        .map(f =>
          if i.value == WritingGuide then s"$GuideHeading\n\n$f"
          else if i.value == WritingRules then s"$RulesHeading\n\n$f"
          else f
        )
    } ++ d.skills.map { s =>
      skills
        .get(SkillDef.Name(s.value))
        .toRight(s"agents/${d.name}.md lists skill '$s', which is not in skills/")
        .map(sk => s"# Skill: ${sk.name}\n\n${forAudience(AgentDef.Body(sk.body.value), keep).value.trim}")
    }
    parts.collect { case Left(m) => m } match
      case Nil =>
        Right(render((forAudience(d.body, keep).value.trim :: parts.collect { case Right(p) => p }).mkString("\n\n"), style))
      case errs => Left(errs.mkString("; "))

  /** Who a headless run of `d` is: a step — and the writer when it includes the whole writing guide. */
  def headlessAudience(d: AgentDef): Set[Audience] =
    if d.include.exists(_.value == WritingGuide) then Set(Audience.Headless, Audience.Writer) else Set(Audience.Headless)

/** The definitions as the server reads them: bundled resources (`agents/index.txt` lists them), each file overridable
  * from `agents.prompts-dir` through [[Prompts]]. Read on every use — a definition can be iterated on without a
  * rebuild, and the hash recorded per attempt says which text ran.
  */
/** `roster`: the agent tools the engine's toolbox holds and the roles the contributed workflows take — a definition may
  * list only these tools and take only these roles.
  */
final class AgentDefinitions(prompts: Prompts, val roster: AgentRoster):
  val tools: List[ToolName] = roster.tools

  /** Every bundled file, relative to `agents/`. */
  def index: List[AgentDefinitions.ResourcePath] =
    prompts
      .bundledText(SafePath.Candidate("index.txt"))
      .toOption
      .toList
      .flatMap(_.linesIterator.map(_.trim).filter(_.nonEmpty).map(AgentDefinitions.ResourcePath(_)))

  def agentNames: List[AgentDef.Name] =
    index
      .map(_.value)
      .filter(r => !r.contains('/') && r.endsWith(".md"))
      .map(r => AgentDef.Name(r.stripSuffix(".md")))
  def skillNames: List[SkillDef.Name] =
    index.map(_.value).collect { case r if r.startsWith("skills/") => SkillDef.Name(r.split('/')(1)) }.distinct
  def fragmentNames: List[AgentDefinitions.FragmentName] =
    index.map(_.value).collect {
      case r if r.startsWith("_shared/") =>
        AgentDefinitions.FragmentName(r.stripPrefix("_shared/").stripSuffix(".md"))
    }

  def agent(name: AgentDef.Name): Either[String, AgentDef] =
    prompts
      .load(SafePath.Candidate(s"$name.md"))
      .left
      .map(_.value)
      .flatMap(text => AgentDefinitions.parse(name, AgentDefinitions.Markdown(text), tools))
      .flatMap(d =>
        Either.cond(
          roster.roles.contains(io.scalaland.chimney.Transformer.derive[AgentRole, com.histmersion.domain.automation.AgentRole].transform(d.role)),
          d,
          s"agents/$name.md: role ${d.role.wire} is no contributed workflow's " +
            s"(one of: ${roster.roles.map(_.wire).mkString(", ")})"
        )
      )
  def agent(s: StepAgent): Either[String, AgentDef] = agent(AgentDef.Name(s.wire.value))
  def skill(name: SkillDef.Name): Either[String, SkillDef] =
    prompts
      .load(SafePath.Candidate(s"skills/$name/SKILL.md"))
      .left
      .map(_.value)
      .flatMap(text => AgentDefinitions.parseSkill(name, AgentDefinitions.Markdown(text)))

  def fragments: Either[String, Map[AgentDefinitions.FragmentName, AgentDefinitions.FragmentBody]] =
    all(fragmentNames)(n =>
      prompts
        .load(SafePath.Candidate(s"_shared/$n.md"))
        .left
        .map(_.value)
        .map(text => n -> AgentDefinitions.FragmentBody(text))
    ).map(_.toMap)
  def skills: Either[String, Map[SkillDef.Name, SkillDef]] = all(skillNames)(n => skill(n).map(n -> _)).map(_.toMap)
  def agents: Either[String, List[AgentDef]] = all(agentNames)(agent)

  /** The system prompt `d` runs with in a runtime that names tools `style`'s way. */
  def system(d: AgentDef, style: ToolStyle): Either[String, String] =
    for
      fs <- fragments
      ss <- skills
      s <- AgentDefinitions.system(d, fs, ss, style)
    yield s

  private def all[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    as.map(f).partitionMap(identity) match
      case (Nil, ok) => Right(ok)
      case (errs, _) => Left(errs.mkString("; "))
