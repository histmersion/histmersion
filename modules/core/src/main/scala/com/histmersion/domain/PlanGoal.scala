package com.histmersion
package domain

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Content, Count, Field, Raw, Where}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Goal.Plans.{Plan, PlannedGoal, PlannedWork}
import com.histmersion.api.automation.Goal.Rationale
import com.histmersion.domain.library.Doi
import com.histmersion.domain.research.Slug
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Run

/** The `plan-goal` pipeline's typed answer and its pure checks (docs/research-embedded-agents.md §14): the planner
  * proposes sub-goals, search queries and candidate works (DOIs) for one goal; code validates the vocabulary (slugs,
  * DOIs, caps), looks every DOI up upstream, and only then shows the plan to a person. Nothing is created before the
  * person approves it.
  */
object PlanGoal:
  type AcquireText = AcquireText.T
  object AcquireText extends Newtype[String, Pure]
  type Approved = Approved.T
  object Approved extends Newtype[Boolean, Pure]
  type CreatedCount = CreatedCount.T
  object CreatedCount extends Newtype[Int, Pure]
  type LastRound = LastRound.T
  object LastRound extends Newtype[Boolean, Pure]
  type LinkedCount = LinkedCount.T
  object LinkedCount extends Newtype[Int, Pure]
  type LogLine = LogLine.T
  object LogLine extends Newtype[String, Pure]
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type QueuedCount = QueuedCount.T
  object QueuedCount extends Newtype[Int, Pure]

  // ── the model's answer ──────────────────────────────────────────────────────────────────────────

  final case class SubGoalAnswer(
      slug: SubGoalAnswer.Slug,
      title: SubGoalAnswer.Title,
      notes: Option[SubGoalAnswer.Notes] = None,
      tags: List[SubGoalAnswer.Tags] = Nil
  ) derives JsonCodec
  object SubGoalAnswer:
    type Slug = Slug.T
    object Slug extends Newtype[String, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]

    given Schema[SubGoalAnswer] = KindlingsSchema
      .derived[SubGoalAnswer]
      .schema
      .modify(_.slug)(_.description("A new, lower-case slug for the sub-goal (letters, digits, '-', '_')"))
      .modify(_.title)(_.description("What the sub-goal is about, in a short phrase"))
      .modify(_.notes)(_.description("What it should establish, in one or two sentences; null if obvious"))
      .modify(_.tags)(_.description("Tags from the corpus vocabulary (facets), not new ones"))

  final case class WorkAnswer(doi: WorkAnswer.Doi, why: Rationale) derives JsonCodec
  object WorkAnswer:
    type Doi = Doi.T
    object Doi extends Newtype[String, Pure]

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
  ) derives JsonCodec
  object PlanAnswer:
    type Rationale = Rationale.T
    object Rationale extends Newtype[String, Pure]
    type Queries = Queries.T
    object Queries extends Newtype[String, Pure]

    given Schema[PlanAnswer] = KindlingsSchema
      .derived[PlanAnswer]
      .schema
      .modify(_.rationale)(_.description("How the plan covers the goal, in two or three sentences"))
      .modify(_.subGoals)(_.description("Narrower goals that split this one; empty if it needs none"))
      .modify(_.queries)(_.description("Literature search queries (plain words) that find the works the goal needs"))
      .modify(_.works)(_.description("Specific works to read or acquire, by DOI — only DOIs you are sure of"))

  // ── checked (checkpointed before the upstream lookups) ──────────────────────────────────────────

  final case class ProposedWork(doi: Doi, why: Rationale) derives JsonCodec

  /** The planner's answer after the pure checks; its DOIs are not looked up yet. */
  final case class Proposed(
      rationale: Proposed.Rationale,
      subGoals: List[PlannedGoal],
      queries: List[Proposed.Queries],
      works: List[ProposedWork],
      dropped: List[Proposed.Dropped]
  ) derives JsonCodec

  object Proposed:
    type Rationale = Rationale.T
    object Rationale extends Newtype[String, Pure]
    type Queries = Queries.T
    object Queries extends Newtype[String, Pure]
    type Dropped = Dropped.T
    object Dropped extends Newtype[String, Pure]

  /** The caps on a plan (config `agents.plan.*`). */
  final case class Caps(subGoals: Caps.SubGoals, queries: Caps.Queries, works: Caps.Works, queryChars: Caps.QueryChars)

  object Caps:
    type SubGoals = SubGoals.T
    object SubGoals extends Newtype[Int, Pure]
    type Queries = Queries.T
    object Queries extends Newtype[Int, Pure]
    type Works = Works.T
    object Works extends Newtype[Int, Pure]
    type QueryChars = QueryChars.T
    object QueryChars extends Newtype[Int, Pure]

  /** Check a plan answer. Problems a retry can fix (an invalid slug or DOI, an empty title, a cap exceeded, nothing
    * proposed) send it back; things a retry cannot change (a slug that is already a goal, a query the goal already has,
    * a duplicate) are dropped with a note. On the last attempt what passed is kept and the problems become notes.
    */
  def check(
      a: PlanAnswer,
      goal: Goal.Slug,
      goalExists: Goal.Slug => Boolean,
      knownQueries: Set[Query],
      caps: Caps,
      last: LastRound
  ): Either[List[Problem], (Proposed, List[Problem])] =
    val problems = List.newBuilder[Problem]
    val dropped = List.newBuilder[String]

    val goals = a.subGoals
      .foldLeft(Vector.empty[PlannedGoal]) { (acc, g) =>
        Slug
          .parse(Option(g.slug).fold("")(_.value))
          .flatMap(s => Goal.Slug.parse(s.value)) match
          case Left(m) => problems ++= m.toVector.map(_.within(Field("sub-goal"))); acc
          case Right(s) if Option(g.title).forall(_.value.trim.isEmpty) =>
            problems += Content.Answer(Content.Requirement.SubGoalTitle(Raw.of(s))); acc
          case Right(s) if s == goal                           => dropped += s"sub-goal '$s' is the goal itself"; acc
          case Right(s) if acc.exists(_.slug.value == s.value) => dropped += s"sub-goal '$s' proposed twice"; acc
          case Right(s) if goalExists(s)                       => dropped += s"sub-goal '$s' is already a goal"; acc
          case Right(s)                                        =>
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
        if q.isEmpty then { problems += Content.Answer(Content.Requirement.QueryGiven); acc }
        else if q.length > caps.queryChars.value then {
          problems += Content.Answer(Content.Requirement.QueryWithin(Raw(q), Count.of(caps.queryChars))); acc
        } else if acc.exists(_.equalsIgnoreCase(q)) then acc
        else if known.contains(q.toLowerCase) then { dropped += s"query '$q' is already linked to the goal"; acc }
        else acc :+ q
      }
      .toList

    val works = a.works
      .foldLeft(Vector.empty[ProposedWork]) { (acc, w) =>
        Doi.normalize(Option(w.doi).fold("")(_.value)) match
          case None => problems += Content.Answer(Content.Requirement.DoiGiven(Raw.of(w.doi))); acc
          case Some(d) if acc.exists(_.doi == d) => acc
          case Some(d)                           =>
            acc :+ ProposedWork(d, Rationale(Option(w.why).fold("")(r => s"$r".trim)))
      }
      .toList

    if goals.size > caps.subGoals.value then
      problems += Content.Answer(
        Content.Requirement.TooManySubGoals(Count.of(caps.subGoals), Count(goals.size.toLong))
      )
    if queries.size > caps.queries.value then
      problems += Content.Answer(
        Content.Requirement.TooManyQueries(Count.of(caps.queries), Count(queries.size.toLong))
      )
    if works.size > caps.works.value then
      problems += Content.Answer(Content.Requirement.TooManyWorks(Count.of(caps.works), Count(works.size.toLong)))
    if goals.isEmpty && queries.isEmpty && works.isEmpty && a.subGoals.isEmpty && a.queries.isEmpty && a.works.isEmpty
    then problems += Content.Answer(Content.Requirement.PlanGiven)

    if Option(a.rationale).forall(_.value.trim.isEmpty) then
      problems += Content.Answer(Content.Requirement.GoalPlanRationale)

    val ps = problems.result()
    if ps.nonEmpty && !last.value then Left(ps)
    else
      val kept = Proposed(
        Proposed.Rationale(Option(a.rationale).fold("")(_.value.trim)),
        goals.take(caps.subGoals.value),
        queries.take(caps.queries.value).map(Proposed.Queries(_)),
        works.take(caps.works.value),
        (dropped.result() ++ ps.map(_.sentence)).map(Proposed.Dropped(_))
      )
      Right((kept, ps.map(_.inside(Where("plan: dropped")))))

  // ── the upstream check (§14: DOIs are verified before a person sees them) ───────────────────────

  /** Look each proposed DOI up (OpenAlex/Crossref through the discover path). A DOI that does not resolve, or a
    * retracted work, is dropped with why — a person never sees an unverified work.
    */
  /** A work as the lookup found it: its metadata, whether the corpus holds it (and under which citekey), whether it is
    * open access or retracted.
    */
  final case class Work(
      title: Option[Work.Title],
      year: Option[Year],
      authors: List[Work.Authors],
      citekey: Option[Citekey],
      inDb: Work.InDb,
      isOa: Work.IsOa,
      isRetracted: Work.IsRetracted
  )

  object Work:
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Authors = Authors.T
    object Authors extends Newtype[String, Pure]
    type InDb = InDb.T
    object InDb extends Newtype[Boolean, Pure]
    type IsOa = IsOa.T
    object IsOa extends Newtype[Boolean, Pure]
    type IsRetracted = IsRetracted.T
    object IsRetracted extends Newtype[Boolean, Pure]

  /** A work looked up by its DOI (the library's metadata, lent by the composition). */
  type Lookup = Doi => Either[Problem, Work]

  def verify(p: Proposed, goal: Goal.Slug, lookup: Lookup): Plan =
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
          w.why
        )
    }
    val dropped = looked.collect {
      case (w, Left(m))                         => Plan.Dropped(s"${w.doi}: not verified upstream — ${m.sentence}")
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
  def acquireText(w: PlannedWork, goal: Goal.Slug): AcquireText =
    val who = w.authors.headOption.map(a => if w.authors.size > 1 then s"$a et al." else a.value)
    val head = List(who, w.year.map(y => s"($y)")).flatten.mkString(" ")
    val title = w.title.fold(w.doi.value)(_.value)
    val why = Option(w.why).map(r => s"$r".trim).filter(_.nonEmpty).fold("")(r => s": $r")
    AcquireText(s"${if head.isEmpty then "" else s"$head. "}$title. https://doi.org/${w.doi} — for goal $goal$why")

  /** The log line of an applied (or rejected) plan. */
  def logLine(
      goal: Goal.Slug,
      plan: Plan,
      approved: Approved,
      created: CreatedCount,
      linked: LinkedCount,
      queued: QueuedCount,
      runId: Run.Id
  ): LogLine =
    if !approved.value then
      LogLine(s"plan-goal $goal: the plan was rejected; nothing was created (agent run ${runId.value.take(8)})")
    else
      LogLine(
        s"plan-goal $goal: approved — $created sub-goal(s), ${plan.queries.size} query link(s), " +
          s"$linked work(s) in the corpus linked, $queued queued for acquisition (agent run ${runId.value.take(8)})"
      )
