package com.histmersion.domain

import com.histmersion.api.Articles
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.GoalPlans.{Plan, PlannedGoal, PlannedWork}
import com.histmersion.values.Doi

/** The `plan-goal` pipeline's typed answer and its pure checks (docs/research-embedded-agents.md §14): the planner
  * proposes sub-goals, search queries and candidate works (DOIs) for one goal; code validates the vocabulary (slugs,
  * DOIs, caps), looks every DOI up upstream, and only then shows the plan to a person. Nothing is created before the
  * person approves it.
  */
object PlanGoal:

  // ── the model's answer ──────────────────────────────────────────────────────────────────────────

  final case class SubGoalAnswer(slug: String, title: String, notes: Option[String] = None, tags: List[String] = Nil)
      derives KindlingsCodecAsObject
  object SubGoalAnswer:
    given Schema[SubGoalAnswer] = KindlingsSchema
      .derived[SubGoalAnswer]
      .schema
      .modify(_.slug)(_.description("A new, lower-case slug for the sub-goal (letters, digits, '-', '_')"))
      .modify(_.title)(_.description("What the sub-goal is about, in a short phrase"))
      .modify(_.notes)(_.description("What it should establish, in one or two sentences; null if obvious"))
      .modify(_.tags)(_.description("Tags from the corpus vocabulary (facets), not new ones"))

  final case class WorkAnswer(doi: String, why: String) derives KindlingsCodecAsObject
  object WorkAnswer:
    given Schema[WorkAnswer] = KindlingsSchema
      .derived[WorkAnswer]
      .schema
      .modify(_.doi)(_.description("The work's DOI (10.xxxx/…), exactly; never invent one"))
      .modify(_.why)(_.description("Why this work matters for the goal, in one sentence"))

  final case class PlanAnswer(
      rationale: String,
      subGoals: List[SubGoalAnswer] = Nil,
      queries: List[String] = Nil,
      works: List[WorkAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object PlanAnswer:
    given Schema[PlanAnswer] = KindlingsSchema
      .derived[PlanAnswer]
      .schema
      .modify(_.rationale)(_.description("How the plan covers the goal, in two or three sentences"))
      .modify(_.subGoals)(_.description("Narrower goals that split this one; empty if it needs none"))
      .modify(_.queries)(_.description("Literature search queries (plain words) that find the works the goal needs"))
      .modify(_.works)(_.description("Specific works to read or acquire, by DOI — only DOIs you are sure of"))

  // ── checked (checkpointed before the upstream lookups) ──────────────────────────────────────────

  final case class ProposedWork(doi: String, why: String) derives KindlingsCodecAsObject

  /** The planner's answer after the pure checks; its DOIs are not looked up yet. */
  final case class Proposed(
      rationale: String,
      subGoals: List[PlannedGoal],
      queries: List[String],
      works: List[ProposedWork],
      dropped: List[String]
  ) derives KindlingsCodecAsObject

  /** The caps on a plan (config `agents.plan.*`). */
  final case class Caps(subGoals: Int, queries: Int, works: Int, queryChars: Int)

  /** Check a plan answer. Problems a retry can fix (an invalid slug or DOI, an empty title, a cap exceeded, nothing
    * proposed) send it back; things a retry cannot change (a slug that is already a goal, a query the goal already has,
    * a duplicate) are dropped with a note. On the last attempt what passed is kept and the problems become notes.
    */
  def check(
      a: PlanAnswer,
      goal: String,
      goalExists: String => Boolean,
      knownQueries: Set[String],
      caps: Caps,
      last: Boolean
  ): Either[List[String], (Proposed, List[String])] =
    val problems = List.newBuilder[String]
    val dropped = List.newBuilder[String]

    val goals = a.subGoals
      .foldLeft(Vector.empty[PlannedGoal]) { (acc, g) =>
        Articles.slugOf(Option(g.slug).getOrElse("")) match
          case Left(m)                                            => problems += s"sub-goal: $m"; acc
          case Right(s) if Option(g.title).forall(_.trim.isEmpty) => problems += s"sub-goal '$s' has no title"; acc
          case Right(s) if s == goal                              => dropped += s"sub-goal '$s' is the goal itself"; acc
          case Right(s) if acc.exists(_.slug == s)                => dropped += s"sub-goal '$s' proposed twice"; acc
          case Right(s) if goalExists(s)                          => dropped += s"sub-goal '$s' is already a goal"; acc
          case Right(s)                                           =>
            acc :+ PlannedGoal(
              s,
              g.title.trim,
              g.notes.map(_.trim).filter(_.nonEmpty),
              g.tags.map(_.trim).filter(_.nonEmpty).distinct
            )
      }
      .toList

    val known = knownQueries.map(_.trim.toLowerCase)
    val queries = a.queries
      .map(q => Option(q).map(_.trim.replaceAll("\\s+", " ")).getOrElse(""))
      .foldLeft(Vector.empty[String]) { (acc, q) =>
        if q.isEmpty then { problems += "an empty query"; acc }
        else if q.length > caps.queryChars then {
          problems += s"query '${q.take(40)}…' is longer than ${caps.queryChars} characters"; acc
        } else if acc.exists(_.equalsIgnoreCase(q)) then acc
        else if known.contains(q.toLowerCase) then { dropped += s"query '$q' is already linked to the goal"; acc }
        else acc :+ q
      }
      .toList

    val works = a.works
      .foldLeft(Vector.empty[ProposedWork]) { (acc, w) =>
        Doi.normalize(Option(w.doi).getOrElse("")) match
          case None                              => problems += s"'${w.doi}' is not a DOI (10.xxxx/…)"; acc
          case Some(d) if acc.exists(_.doi == d) => acc
          case Some(d)                           => acc :+ ProposedWork(d, Option(w.why).map(_.trim).getOrElse(""))
      }
      .toList

    if goals.size > caps.subGoals then problems += s"at most ${caps.subGoals} sub-goals (you gave ${goals.size})"
    if queries.size > caps.queries then problems += s"at most ${caps.queries} queries (you gave ${queries.size})"
    if works.size > caps.works then problems += s"at most ${caps.works} works (you gave ${works.size})"
    if goals.isEmpty && queries.isEmpty && works.isEmpty && a.subGoals.isEmpty && a.queries.isEmpty && a.works.isEmpty
    then problems += "the plan proposes nothing: give sub-goals, queries or works"

    if Option(a.rationale).forall(_.trim.isEmpty) then
      problems += "the rationale is empty — say how the plan covers the goal"

    val ps = problems.result()
    if ps.nonEmpty && !last then Left(ps)
    else
      val kept = Proposed(
        Option(a.rationale).map(_.trim).getOrElse(""),
        goals.take(caps.subGoals),
        queries.take(caps.queries),
        works.take(caps.works),
        dropped.result() ++ ps
      )
      Right((kept, ps.map(p => s"plan: dropped — $p")))

  // ── the upstream check (§14: DOIs are verified before a person sees them) ───────────────────────

  /** Look each proposed DOI up (OpenAlex/Crossref through the discover path). A DOI that does not resolve, or a
    * retracted work, is dropped with why — a person never sees an unverified work.
    */
  /** A work as the lookup found it: its metadata, whether the corpus holds it (and under which citekey), whether it is
    * open access or retracted.
    */
  final case class Work(
      title: Option[String],
      year: Option[Int],
      authors: List[String],
      citekey: Option[String],
      inDb: Boolean,
      isOa: Boolean,
      isRetracted: Boolean
  )

  /** A work looked up by its DOI (the library's metadata, lent by the composition). */
  type Lookup = String => Either[String, Work]

  def verify(p: Proposed, goal: String, lookup: Lookup): Plan =
    val looked = p.works.map(w => (w, lookup(w.doi)))
    val works = looked.collect {
      case (w, Right(r)) if !r.isRetracted =>
        PlannedWork(w.doi, r.title, r.year, r.authors.take(6), r.citekey, r.inDb, r.isOa, w.why)
    }
    val dropped = looked.collect {
      case (w, Left(m))                   => s"${w.doi}: not verified upstream — $m"
      case (w, Right(r)) if r.isRetracted => s"${w.doi}: retracted upstream"
    }
    Plan(goal, p.rationale, p.subGoals, p.queries, works, p.dropped ++ dropped)

  // ── applying an approved plan ───────────────────────────────────────────────────────────────────

  /** The acquisition item a planned work outside the corpus becomes. The DOI is written as a resolver URL so
    * [[Scheduling.dois]] (and a person) find it again.
    */
  def acquireText(w: PlannedWork, goal: String): String =
    val who = w.authors.headOption.map(a => if w.authors.size > 1 then s"$a et al." else a)
    val head = List(who, w.year.map(y => s"($y)")).flatten.mkString(" ")
    val title = w.title.getOrElse(w.doi)
    val why = Option(w.why).map(_.trim).filter(_.nonEmpty).fold("")(r => s": $r")
    s"${if head.isEmpty then "" else s"$head. "}$title. https://doi.org/${w.doi} — for goal $goal$why"

  /** The log line of an applied (or rejected) plan. */
  def logLine(
      goal: String,
      plan: Plan,
      approved: Boolean,
      created: Int,
      linked: Int,
      queued: Int,
      runId: String
  ): String =
    if !approved then s"plan-goal $goal: the plan was rejected; nothing was created (agent run ${runId.take(8)})"
    else
      s"plan-goal $goal: approved — $created sub-goal(s), ${plan.queries.size} query link(s), " +
        s"$linked work(s) in the corpus linked, $queued queued for acquisition (agent run ${runId.take(8)})"
