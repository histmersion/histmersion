package com.histmersion
package api.automation

import com.histmersion.api.WireEnumValues
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.OffsetPage
import com.histmersion.api.StrictJson
import com.histmersion.api.research.Coverage
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.library.Doi
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Creation
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class Goal(
    slug: Goal.Slug,
    title: Goal.Title,
    status: Goal.StatusValue,
    notes: Option[Goal.Notes] = None,
    tags: scala.List[Goal.Tags] = Nil,
    regions: scala.List[Goal.Regions] = Nil,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None,
    parentSlug: Option[Goal.Slug] = None,
    updatedAt: Option[Goal.UpdatedAt] = None,
    creator: Option[Creation] = None,
    createdAt: Option[Creation.At] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:

  /** The period the goal is about (`from 1300 BCE`, `1300–1100 BCE`), `none` when it has neither end. */
  def period(none: String): String = Year.openPeriod(periodStart, periodEnd, none)

object Goal:
  /** Why a plan names a work: the planner's words, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  type Export = Export.T
  object Export extends Newtype[String, Pure]
  type LinkSectionKey = LinkSectionKey.T
  object LinkSectionKey extends Newtype[String, Pure]
  type Ref = Ref.T
  object Ref extends Newtype[String, Pure]
  type Stage = Stage.T
  object Stage extends Newtype[String, Pure]
  type StageMeaning = StageMeaning.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object StageMeaning extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Uid = Uid.T
  object Uid extends Newtype[String, Pure]

  /** The stages of a research goal, as the store and the wire spell them. */
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Title = Title.T
  object Title extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type StatusValue = StatusValue.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object StatusValue extends Newtype[String, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]

  object Status:
    type All = All.T
    object All extends Newtype[String, Pure]

    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: scala.List[All] = scala.List(All(Planned), All(Seeding), All(Compiled), All(Reviewed), All(Done))

  /** What a goal is linked to. */
  enum LinkKind(val wire: LinkKind.Wire) derives CanEqual:
    case Source extends LinkKind(LinkKind.Wire("source"))
    case Article extends LinkKind(LinkKind.Wire("article"))
    case Query extends LinkKind(LinkKind.Wire("query"))
    case QueueItem extends LinkKind(LinkKind.Wire("queue-item"))

  object LinkKind:

    /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val SearchQuery: LinkKind = Query

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[LinkKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: LinkKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[LinkKind] = parse(Wire((s).value))

  final case class Deleted(
      slug: Slug,
      deleted: Deleted.Value
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Deleted:
    enum Value derives CanEqual:
      case Deleted, Kept
    object Value extends TwoStates[Value]:
      extension (value: Value) def deleted: Boolean = value == Deleted

  final case class List(
      total: RowCount,
      goals: scala.List[Goal],
      offset: Offset = Offset(0),
      next: Option[Offset] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def page: OffsetPage = OffsetPage(total, offset, RowCount(goals.size))

  object List:

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def of(total: RowCount, rows: scala.List[Goal], offset: UnreadPage.Offset): List =
      List(
        total,
        rows,
        Offset((offset).value),
        OffsetPage(total, Offset((offset).value), RowCount(rows.size)).next
      )

  final case class Patch(
      title: Option[Title] = None,
      status: Option[StatusValue] = None,
      notes: Option[Notes] = None,
      tags: Option[scala.List[Tags]] = None,
      regions: Option[scala.List[Regions]] = None,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None,
      parentSlug: Option[Slug] = None,
      budgetUsd: Option[Patch.BudgetUsd] = None
  ) derives StrictJson,
        KindlingsSchema,
        api.json.Schema

  object Patch:

    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends Newtype[Double, Pure]

  /** `POST /api/goals/areas`: a new research area as one server operation. The goal at `slug` is created, never
    * replaced (a slug in use, or an old slug of another goal, is refused with 409); then, as asked, its plan-goal run
    * (`plan`), its seeding manager (`seed`) and its watcher with these settings (`watch`). All of it or none of it.
    */
  final case class AreaCreate(
      slug: Slug,
      goal: Put,
      plan: AreaCreate.Plan = AreaCreate.Plan.NoPlan,
      seed: AreaCreate.Seed = AreaCreate.Seed.NoSeed,
      watch: Option[Managers.ManagerConfig] = None,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema

  object AreaCreate:
    enum Plan derives CanEqual:
      case Plan, NoPlan
    object Plan extends TwoStates[Plan]:
      extension (plan: Plan) def plan: Boolean = plan == Plan
    enum Seed derives CanEqual:
      case Seed, NoSeed
    object Seed extends TwoStates[Seed]:
      extension (seed: Seed) def seed: Boolean = seed == Seed

  /** What [[AreaCreate]] made: the goal, its plan-goal run (handed to the engine), its seeding manager and its watcher.
    */
  final case class AreaCreated(
      goal: Goal,
      run: Option[Agents.RunInfo] = None,
      seed: Option[Managers.ManagerInfo] = None,
      watch: Option[Managers.ManagerInfo] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Plans:

    final case class PlannedGoal(
        slug: Slug,
        title: Title,
        notes: Option[Notes] = None,
        tags: scala.List[Tags] = Nil
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    final case class PlannedWork(
        doi: Doi,
        title: Option[api.library.Title] = None,
        year: Option[Year] = None,
        authors: scala.List[AuthorName] = Nil,
        citekey: Option[Citekey] = None,
        inCorpus: PlannedWork.InCorpus = PlannedWork.InCorpus.Missing,
        isOa: PlannedWork.IsOa = PlannedWork.IsOa.Closed,
        why: Rationale = Rationale("")
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object PlannedWork:
      enum InCorpus derives CanEqual:
        case InCorpus, Missing
      object InCorpus extends TwoStates[InCorpus]:
        extension (inCorpus: InCorpus) def inCorpus: Boolean = inCorpus == InCorpus
      enum IsOa derives CanEqual:
        case OpenAccess, Closed
      object IsOa extends TwoStates[IsOa]:
        extension (isOa: IsOa) def isOa: Boolean = isOa == OpenAccess

    final case class Plan(
        goal: Slug,
        rationale: Plan.Rationale = Plan.Rationale(""),
        subGoals: scala.List[PlannedGoal] = Nil,
        queries: scala.List[Plan.Queries] = Nil,
        works: scala.List[PlannedWork] = Nil,
        dropped: scala.List[Plan.Dropped] = Nil
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema:
      def isEmpty: Boolean = (subGoals.isEmpty && queries.isEmpty && works.isEmpty)

    object Plan:

      type Rationale = Rationale.T
      object Rationale extends Newtype[String, Pure]
      type Queries = Queries.T
      object Queries extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)
      type Dropped = Dropped.T
      object Dropped extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)

    export Goal.LinkKind as LinkKind
    export com.histmersion.api.research.DraftMode
    export com.histmersion.api.automation.ScheduleKind

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class ArticleDraft(
        slug: api.research.Article.Slug,
        mode: DraftMode,
        title: api.research.Article.Title,
        kind: api.research.Article.Kind.Wire,
        text: ArticleDraft.Text,
        summary: Option[api.research.Article.SummaryText] = None,
        section: Option[ArticleDraft.Section] = None,
        cited: scala.List[Citekey] = Nil,
        notUsed: scala.List[Citekey] = Nil,
        problems: scala.List[ArticleDraft.Problems] = Nil,
        filter: ArticleDraft.Filter = ArticleDraft.Filter(""),
        reasons: scala.List[Coverage.Reason] = Nil
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object ArticleDraft:
      type Text = Text.T
      object Text extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)
      type Section = Section.T
      object Section extends Newtype[String, Pure]
      type Problems = Problems.T
      object Problems extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)
      type Filter = Filter.T
      object Filter extends Newtype[String, Pure]

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class GoalLink(
        kind: LinkKind,
        ref: GoalLink.Ref,
        note: Option[GoalLink.Note] = None,
        title: Option[GoalLink.Title] = None,
        createdAt: GoalLink.CreatedAt = GoalLink.CreatedAt(""),
        queue: Option[GoalLink.Queue] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object GoalLink:
      type QueueNumber = QueueNumber.T
      @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
      object QueueNumber extends Newtype[Int, Pure]
      type Ref = Ref.T
      object Ref extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)
      type Note = Note.T
      object Note extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)
      type Title = Title.T
      object Title extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)
      type CreatedAt = CreatedAt.T
      object CreatedAt extends Newtype[String, Pure]
      type Queue = Queue.T
      object Queue extends Newtype[String, Pure]

    final case class LinkAdd(
        kind: LinkKind,
        ref: LinkAdd.Ref,
        note: Option[LinkAdd.Note] = None,
        createdBy: Option[Creation.By] = None,
        via: Option[Creation.Via] = None
    ) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema
    object LinkAdd:

      type Ref = Ref.T
      object Ref extends Newtype[String, Pure]
      type Note = Note.T
      object Note extends Newtype[String, Pure]

    /** `cron`: five fields in UTC, or `@daily`… */
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class GoalSchedule(
        goal: Slug,
        kind: ScheduleKind,
        cron: GoalSchedule.Cron,
        enabled: GoalSchedule.Enabled,
        budgetUsd: Option[GoalSchedule.BudgetUsd] = None,
        nextRunAt: Option[GoalSchedule.NextRunAt] = None,
        lastRunAt: Option[GoalSchedule.LastRunAt] = None,
        lastRun: Option[GoalSchedule.LastRun] = None,
        lastNote: Option[GoalSchedule.LastNote] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object GoalSchedule:
      type Cron = Cron.T
      object Cron extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)
      enum Enabled derives CanEqual:
        case Enabled, Disabled
      object Enabled extends TwoStates[Enabled]:
        extension (enabled: Enabled) def enabled: Boolean = enabled == Enabled
      export Managers.ManagerConfig.BudgetUsd
      type NextRunAt = NextRunAt.T
      object NextRunAt extends Newtype[String, Pure]
      type LastRunAt = LastRunAt.T
      @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
      object LastRunAt extends Newtype[String, Pure]
      type LastRun = LastRun.T
      object LastRun extends Newtype[String, Pure]
      type LastNote = LastNote.T
      object LastNote extends Newtype[String, Pure]:
        given Display[T] = Display(_.value)

    final case class ScheduleSet(
        cron: ScheduleSet.Cron,
        enabled: ScheduleSet.Enabled = ScheduleSet.Enabled.Enabled,
        budgetUsd: Option[ScheduleSet.BudgetUsd] = None,
        createdBy: Option[Creation.By] = None,
        via: Option[Creation.Via] = None
    ) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema
    object ScheduleSet:

      type Cron = Cron.T
      object Cron extends Newtype[String, Pure]
      enum Enabled derives CanEqual:
        case Enabled, Disabled
      object Enabled extends TwoStates[Enabled]:
        extension (enabled: Enabled) def enabled: Boolean = enabled == Enabled
      export Managers.ManagerConfig.BudgetUsd

    final case class Removed(removed: Removed.Value) derives JsonCodec, KindlingsSchema, api.json.Schema

    object Removed:
      enum Value derives CanEqual:
        case Removed, Kept
      object Value extends TwoStates[Value]:
        extension (value: Value) def removed: Boolean = value == Removed

    @tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
    final case class GoalView(
        goal: Goal,
        children: scala.List[Goal] = Nil,
        links: scala.List[GoalLink] = Nil,
        schedules: scala.List[GoalSchedule] = Nil,
        plan: Option[Plan] = None,
        budgetUsd: Option[GoalView.BudgetUsd] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object GoalView:
      type BudgetUsd = BudgetUsd.T
      object BudgetUsd extends Newtype[Double, Pure]

  final case class Put(
      title: Title,
      status: StatusValue = StatusValue("planned"),
      notes: Option[Notes] = None,
      tags: scala.List[Tags] = Nil,
      regions: scala.List[Regions] = Nil,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None,
      parentSlug: Option[Slug] = None,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema

  object Stages:
    export Goal.{Stage as MeaningStatus, StageMeaning as MeaningResult}

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: scala.List[(Stage, StageMeaning)] = scala.List(
      {
        val refactorTuple6 = Status.Planned -> "created; nothing works on it yet";
        (Stage(refactorTuple6._1), StageMeaning(refactorTuple6._2))
      }, {
        val refactorTuple7 = Status.Seeding -> "finding and reading sources, writing its articles";
        (Stage(refactorTuple7._1), StageMeaning(refactorTuple7._2))
      }, {
        val refactorTuple3 = Status.Compiled -> "its articles are written; not all have passed review yet";
        (Stage(refactorTuple3._1), StageMeaning(refactorTuple3._2))
      }, {
        val refactorTuple9 = Status.Reviewed -> "every article (and sub-goal) passed the review panel";
        (Stage(refactorTuple9._1), StageMeaning(refactorTuple9._2))
      }, {
        val refactorTuple10 = Status.Done -> "finished: only you set this, the autopilot never does";
        (Stage(refactorTuple10._1), StageMeaning(refactorTuple10._2))
      }
    )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def meaning(status: Stage): Option[StageMeaning] = all.collectFirst {
      case (s, m) if s == status => m
    }
