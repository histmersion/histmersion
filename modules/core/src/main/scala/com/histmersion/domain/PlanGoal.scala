package com.histmersion.domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Goal.Plans.{Plan, PlannedGoal, PlannedWork}
import com.histmersion.domain.library.Doi
import com.histmersion.domain.research.Slug

/** The `plan-goal` pipeline's typed answer and its pure checks (docs/research-embedded-agents.md §14): the planner
  * proposes sub-goals, search queries and candidate works (DOIs) for one goal; code validates the vocabulary (slugs,
  * DOIs, caps), looks every DOI up upstream, and only then shows the plan to a person. Nothing is created before the
  * person approves it.
  */
object PlanGoal:
  type AcquireText = AcquireText.T
  object AcquireText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Approved = Approved.T
  object Approved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedCount = CreatedCount.T
  object CreatedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastRound = LastRound.T
  object LastRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedCount = LinkedCount.T
  object LinkedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLine = LogLine.T
  object LogLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueuedCount = QueuedCount.T
  object QueuedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── the model's answer ──────────────────────────────────────────────────────────────────────────


  final case class SubGoalAnswer(slug: SubGoalAnswer.Slug, title: SubGoalAnswer.Title, notes: Option[SubGoalAnswer.Notes] = None, tags: List[SubGoalAnswer.Tags] = Nil)
      derives KindlingsCodecAsObject
  object SubGoalAnswer:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[SubGoalAnswer] = KindlingsSchema
      .derived[SubGoalAnswer]
      .schema
      .modify(_.slug)(_.description("A new, lower-case slug for the sub-goal (letters, digits, '-', '_')"))
      .modify(_.title)(_.description("What the sub-goal is about, in a short phrase"))
      .modify(_.notes)(_.description("What it should establish, in one or two sentences; null if obvious"))
      .modify(_.tags)(_.description("Tags from the corpus vocabulary (facets), not new ones"))

  final case class WorkAnswer(doi: WorkAnswer.Doi, why: WorkAnswer.Why) derives KindlingsCodecAsObject
  object WorkAnswer:
    type Doi = Doi.T
    object Doi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[WorkAnswer] = KindlingsSchema
      .derived[WorkAnswer]
      .schema
      .modify(_.doi)(_.description("The work's DOI (10.xxxx/…), exactly; never invent one"))
      .modify(_.why)(_.description("Why this work matters for the goal, in one sentence"))

  final case class PlanAnswer(
      rationale: PlanAnswer.Rationale,
      subGoals: List[SubGoalAnswer] = Nil,
      queries: List[PlanAnswer.Queries] = Nil,
      works: List[WorkAnswer] = Nil
  ) derives KindlingsCodecAsObject
  object PlanAnswer:
    type Rationale = Rationale.T
    object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queries = Queries.T
    object Queries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[PlanAnswer] = KindlingsSchema
      .derived[PlanAnswer]
      .schema
      .modify(_.rationale)(_.description("How the plan covers the goal, in two or three sentences"))
      .modify(_.subGoals)(_.description("Narrower goals that split this one; empty if it needs none"))
      .modify(_.queries)(_.description("Literature search queries (plain words) that find the works the goal needs"))
      .modify(_.works)(_.description("Specific works to read or acquire, by DOI — only DOIs you are sure of"))

  // ── checked (checkpointed before the upstream lookups) ──────────────────────────────────────────

  final case class ProposedWork(doi: com.histmersion.domain.library.Doi, why: ProposedWork.Why) derives KindlingsCodecAsObject

  object ProposedWork:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The planner's answer after the pure checks; its DOIs are not looked up yet. */
  final case class Proposed(
      rationale: Proposed.Rationale,
      subGoals: List[PlannedGoal],
      queries: List[Proposed.Queries],
      works: List[ProposedWork],
      dropped: List[Proposed.Dropped]
  ) derives KindlingsCodecAsObject

  object Proposed:
    type Rationale = Rationale.T
    object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queries = Queries.T
    object Queries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dropped = Dropped.T
    object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The caps on a plan (config `agents.plan.*`). */
  final case class Caps(subGoals: Caps.SubGoals, queries: Caps.Queries, works: Caps.Works, queryChars: Caps.QueryChars)

  object Caps:
    type SubGoals = SubGoals.T
    object SubGoals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queries = Queries.T
    object Queries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Works = Works.T
    object Works extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QueryChars = QueryChars.T
    object QueryChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Check a plan answer. Problems a retry can fix (an invalid slug or DOI, an empty title, a cap exceeded, nothing
    * proposed) send it back; things a retry cannot change (a slug that is already a goal, a query the goal already has,
    * a duplicate) are dropped with a note. On the last attempt what passed is kept and the problems become notes.
    */
  def check(
      a: PlanAnswer,
      goal: com.histmersion.domain.automation.Goal.Slug,
      goalExists: com.histmersion.domain.automation.Goal.Slug => Boolean,
      knownQueries: Set[com.histmersion.domain.PlanGoal.Query],
      caps: Caps,
      last: com.histmersion.domain.PlanGoal.LastRound
  ): Either[List[com.histmersion.domain.PlanGoal.Problem], (Proposed, List[com.histmersion.domain.PlanGoal.Problem])] =
    val problems = List.newBuilder[String]
    val dropped = List.newBuilder[String]

    val goals = a.subGoals
      .foldLeft(Vector.empty[PlannedGoal]) { (acc, g) =>
        Slug
          .parse(Option(g.slug).fold("")(_.value))
          .flatMap(s => com.histmersion.domain.automation.Goal.Slug.either(s.value).left.map(Slug.Problem(_))) match
          case Left(m)                                                  => problems += s"sub-goal: $m"; acc
          case Right(s) if Option(g.title).forall(_.value.trim.isEmpty) => problems += s"sub-goal '$s' has no title"; acc
          case Right(s) if s == goal                                    => dropped += s"sub-goal '$s' is the goal itself"; acc
          case Right(s) if acc.exists(_.slug.value == s.value)          => dropped += s"sub-goal '$s' proposed twice"; acc
          case Right(s) if goalExists(s)                                => dropped += s"sub-goal '$s' is already a goal"; acc
          case Right(s)                                                 =>
            acc :+ PlannedGoal(
              com.histmersion.api.automation.Goal.Slug(s.value),
              com.histmersion.api.automation.Goal.Title(g.title.value.trim),
              g.notes.map(_.value.trim).filter(_.nonEmpty).map(com.histmersion.api.automation.Goal.Notes(_)),
              g.tags.map(_.value.trim).filter(_.nonEmpty).distinct.map(com.histmersion.api.automation.Goal.Tags(_))
            )
      }
      .toList

    val known = knownQueries.map(_.value.trim.toLowerCase)
    val queries = a.queries
      .map(q => Option(q).fold("")(_.value.trim.replaceAll("\\s+", " ")))
      .foldLeft(Vector.empty[String]) { (acc, q) =>
        if q.isEmpty then { problems += "an empty query"; acc }
        else if q.length > caps.queryChars.value then {
          problems += s"query '${q.take(40)}…' is longer than ${caps.queryChars} characters"; acc
        } else if acc.exists(_.equalsIgnoreCase(q)) then acc
        else if known.contains(q.toLowerCase) then { dropped += s"query '$q' is already linked to the goal"; acc }
        else acc :+ q
      }
      .toList

    val works = a.works
      .foldLeft(Vector.empty[ProposedWork]) { (acc, w) =>
        Doi.normalize(Option(w.doi).fold("")(_.value)) match
          case None                              => problems += s"'${w.doi}' is not a DOI (10.xxxx/…)"; acc
          case Some(d) if acc.exists(_.doi == d) => acc
          case Some(d)                           => acc :+ ProposedWork(d, com.histmersion.domain.PlanGoal.ProposedWork.Why(Option(w.why).fold("")(_.value.trim)))
      }
      .toList

    if goals.size > caps.subGoals.value then problems += s"at most ${caps.subGoals} sub-goals (you gave ${goals.size})"
    if queries.size > caps.queries.value then problems += s"at most ${caps.queries} queries (you gave ${queries.size})"
    if works.size > caps.works.value then problems += s"at most ${caps.works} works (you gave ${works.size})"
    if goals.isEmpty && queries.isEmpty && works.isEmpty && a.subGoals.isEmpty && a.queries.isEmpty && a.works.isEmpty
    then problems += "the plan proposes nothing: give sub-goals, queries or works"

    if Option(a.rationale).forall(_.value.trim.isEmpty) then
      problems += "the rationale is empty — say how the plan covers the goal"

    val ps = problems.result()
    if ps.nonEmpty && !last.value then Left(ps.map(Problem(_)))
    else
      val kept = Proposed(
        com.histmersion.domain.PlanGoal.Proposed.Rationale(Option(a.rationale).fold("")(_.value.trim)),
        goals.take(caps.subGoals.value),
        queries.take(caps.queries.value).map(Proposed.Queries(_)),
        works.take(caps.works.value),
        (dropped.result() ++ ps).map(Proposed.Dropped(_))
      )
      Right((kept, ps.map(p => Problem(s"plan: dropped — $p"))))

  // ── the upstream check (§14: DOIs are verified before a person sees them) ───────────────────────

  /** Look each proposed DOI up (OpenAlex/Crossref through the discover path). A DOI that does not resolve, or a
    * retracted work, is dropped with why — a person never sees an unverified work.
    */
  /** A work as the lookup found it: its metadata, whether the corpus holds it (and under which citekey), whether it is
    * open access or retracted.
    */
  final case class Work(
      title: Option[Work.Title],
      year: Option[com.histmersion.Year],
      authors: List[Work.Authors],
      citekey: Option[com.histmersion.domain.library.Citekey],
      inDb: Work.InDb,
      isOa: Work.IsOa,
      isRetracted: Work.IsRetracted
  )

  object Work:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InDb = InDb.T
    object InDb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsOa = IsOa.T
    object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsRetracted = IsRetracted.T
    object IsRetracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A work looked up by its DOI (the library's metadata, lent by the composition). */
  type Lookup = Doi => Either[Problem, Work]

  def verify(p: Proposed, goal: com.histmersion.domain.automation.Goal.Slug, lookup: Lookup): Plan =
    val looked = p.works.map(w => (w, lookup(w.doi)))
    val works = looked.collect {
      case (w, Right(r)) if !r.isRetracted.value =>
        PlannedWork(
          com.histmersion.api.library.Doi(w.doi.value),
          r.title.map(t => com.histmersion.api.library.Title(t.value)),
          r.year.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
          r.authors.take(6).map(a => com.histmersion.api.library.AuthorName(a.value)),
          r.citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
          PlannedWork.InCorpus(r.inDb.value),
          PlannedWork.IsOa(r.isOa.value),
          PlannedWork.Why(w.why.value)
        )
    }
    val dropped = looked.collect {
      case (w, Left(m))                         => Plan.Dropped(s"${w.doi}: not verified upstream — $m")
      case (w, Right(r)) if r.isRetracted.value => Plan.Dropped(s"${w.doi}: retracted upstream")
    }
    Plan(
      com.histmersion.api.automation.Goal.Slug(goal.value),
      Plan.Rationale(p.rationale.value),
      p.subGoals,
      p.queries.map(q => Plan.Queries(q.value)),
      works,
      p.dropped.map(d => Plan.Dropped(d.value)) ++ dropped
    )

  // ── applying an approved plan ───────────────────────────────────────────────────────────────────

  /** The acquisition item a planned work outside the corpus becomes. The DOI is written as a resolver URL so
    * [[Scheduling.dois]] (and a person) find it again.
    */
  def acquireText(w: PlannedWork, goal: com.histmersion.domain.automation.Goal.Slug): com.histmersion.domain.PlanGoal.AcquireText =
    val who = w.authors.headOption.map(a => if w.authors.size > 1 then s"$a et al." else a.value)
    val head = List(who, w.year.map(y => s"($y)")).flatten.mkString(" ")
    val title = w.title.fold(w.doi.value)(_.value)
    val why = Option(w.why).map(_.value.trim).filter(_.nonEmpty).fold("")(r => s": $r")
    com.histmersion.domain.PlanGoal.AcquireText(s"${if head.isEmpty then "" else s"$head. "}$title. https://doi.org/${w.doi} — for goal $goal$why")

  /** The log line of an applied (or rejected) plan. */
  def logLine(
      goal: com.histmersion.domain.automation.Goal.Slug,
      plan: Plan,
      approved: com.histmersion.domain.PlanGoal.Approved,
      created: com.histmersion.domain.PlanGoal.CreatedCount,
      linked: com.histmersion.domain.PlanGoal.LinkedCount,
      queued: com.histmersion.domain.PlanGoal.QueuedCount,
      runId: com.histmersion.domain.automation.Run.Id
  ): com.histmersion.domain.PlanGoal.LogLine =
    if !approved.value then com.histmersion.domain.PlanGoal.LogLine(s"plan-goal $goal: the plan was rejected; nothing was created (agent run ${runId.value.take(8)})")
    else
      com.histmersion.domain.PlanGoal.LogLine(
        s"plan-goal $goal: approved — $created sub-goal(s), ${plan.queries.size} query link(s), " +
          s"$linked work(s) in the corpus linked, $queued queued for acquisition (agent run ${runId.value.take(8)})"
      )
