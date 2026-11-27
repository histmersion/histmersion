package com.histmersion
package domain.automation.agents

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

/** The pure half of the definitions: parsing a definition and a skill, the placeholders, and the system prompt a
  * headless run of a definition gets (the same function for every runtime, so the generated Claude Code agent and the
  * prompt the engine sends the API cannot drift).
  */
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

  /** Audience markers that do not pair up or name no audience — a part that would leak into the wrong prompt.
    */
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

  /** `{{tool:x}}` → the runtime's name for tool `x`; `{{tool:*}}` → every Histmersion tool. */
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

  /** Every tool a text names by placeholder (`*` left out). */
  def placeholders(text: String): List[String] =
    Placeholder.findAllMatchIn(text).map(_.group(1)).filter(_ != "*").toList.distinct

  /** `---\n<yaml>\n---\n<body>` → (yaml, body); the body keeps its leading newline, as written. */
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

  /** A header field that holds a list: none, or its items; `field` names it when it holds something else. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def strings(field: String, v: Any): Either[Problem.Config.Flaw, List[String]] =
    Option(v).map(_.asMatchable) match
      case None                              => Right(Nil)
      case Some(l: java.util.List[?])        => Right(l.asScala.toList.map(String.valueOf))
      case Some(s: String) if s.trim.isEmpty => Right(Nil)
      case Some(other) => Left(Problem.Config.Flaw.NotList(Problem.Field(field), Problem.Raw(s"$other")))

  /** The file a definition, a skill or a fragment is, as the prompts name it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[agents] def agentFile(name: AgentDef.Name): Problem.Raw = Problem.Raw(s"agents/$name.md")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def skillFile(name: SkillDef.Name): Problem.Raw = Problem.Raw(s"skills/$name/SKILL.md")

  /** Parse one definition. `name` is its file name; the header's `name` must agree. */
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

  /** Parse one skill (`skills/<name>/SKILL.md`). */
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

  /** The system prompt of a headless run of `d`: its body, its include fragments in order (the writing guide under its
    * heading), then the skills it lists, inlined — a main-thread Claude Code agent never gets its `skills:` preloaded,
    * and Codex cannot open a skill without a shell, so inlining is what every runtime has in common (§2.3, §3a).
    * Placeholders rendered for `style`.
    */
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

  /** Who a headless run of `d` is: a step — and the writer when it includes the whole writing guide. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def headlessAudience(d: AgentDef): Set[Audience] =
    if d.include.exists(_.value == WritingGuide) then Set(Audience.Headless, Audience.Writer)
    else Set(Audience.Headless)

/** The definitions as the server reads them: bundled resources (`agents/index.txt` lists them), each file overridable
  * from `agents.prompts-dir` through [[Prompts]]. Read on every use — a definition can be iterated on without a
  * rebuild, and the hash recorded per attempt says which text ran.
  */
/** `roster`: the agent tools the engine's toolbox holds and the roles the contributed workflows take — a definition may
  * list only these tools and take only these roles.
  */
final class AgentDefinitions(prompts: Prompts, val roster: AgentRoster):
  import AgentDefinitions.agentFile
  val tools: List[ToolName] = roster.tools

  /** Every bundled file, relative to `agents/`. */
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

  /** The system prompt `d` runs with in a runtime that names tools `style`'s way. */
  def system(d: AgentDef, style: ToolStyle): Either[Problems, String] =
    for
      fs <- fragments
      ss <- skills
      s <- AgentDefinitions.system(d, fs, ss, style)
    yield s

  private def all[A, B](as: List[A])(f: A => Either[Problem, B]): Either[Problems, List[B]] =
    as.traverseAll(f).map(_.toList)
