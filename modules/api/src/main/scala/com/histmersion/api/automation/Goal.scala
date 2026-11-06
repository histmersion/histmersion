package com.histmersion.api.automation
import com.histmersion.api.WireEnumValues
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.OffsetPage
import com.histmersion.api.StrictJson
import com.histmersion.api.research.Coverage
import io.circe.Codec
import io.circe.{Codec, Decoder, Encoder}
import com.histmersion.api.json.NewtypeCodecs.given

final case class Goal(
    slug: Goal.Slug,
    title: Goal.Title,
    status: Goal.StatusValue,
    notes: Option[Goal.Notes] = None,
    tags: scala.List[Goal.Tags] = Nil,
    regions: scala.List[Goal.Regions] = Nil,
    periodStart: Option[com.histmersion.api.catalogue.Year] = None,
    periodEnd: Option[com.histmersion.api.catalogue.Year] = None,
    parentSlug: Option[com.histmersion.api.automation.Goal.Slug] = None,
    updatedAt: Option[Goal.UpdatedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Goal:

  /** The stages of a research goal, as the store and the wire spell them. */
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusValue = StatusValue.T
  object StatusValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Status:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    val all: scala.List[com.histmersion.api.automation.Goal.Status.All] = scala.List(_root_.com.histmersion.api.automation.Goal.Status.All(Planned), _root_.com.histmersion.api.automation.Goal.Status.All(Seeding), _root_.com.histmersion.api.automation.Goal.Status.All(Compiled), _root_.com.histmersion.api.automation.Goal.Status.All(Reviewed), _root_.com.histmersion.api.automation.Goal.Status.All(Done))

  /** What a goal is linked to. */
  enum LinkKind(val wire: LinkKind.Wire):
    case Source extends LinkKind(_root_.com.histmersion.api.automation.Goal.LinkKind.Wire("source"))
    case Article extends LinkKind(_root_.com.histmersion.api.automation.Goal.LinkKind.Wire("article"))
    case Query extends LinkKind(_root_.com.histmersion.api.automation.Goal.LinkKind.Wire("query"))
    case QueueItem extends LinkKind(_root_.com.histmersion.api.automation.Goal.LinkKind.Wire("queue-item"))

  object LinkKind:

    /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val SearchQuery: LinkKind = Query

    def parse(s: com.histmersion.api.automation.Goal.LinkKind.ParseS): Option[LinkKind] = values.find(_.wire == _root_.com.histmersion.api.automation.Goal.LinkKind.Wire((s).value.trim.toLowerCase))
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = LinkKind.values
      def wire(e: LinkKind): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((e.wire).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[LinkKind] = parse(_root_.com.histmersion.api.automation.Goal.LinkKind.ParseS((s).value))


  final case class Deleted(
      slug: com.histmersion.api.automation.Goal.Slug,
      deleted: Deleted.Value
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class List(
      total: com.histmersion.api.RowCount,
      goals: scala.List[Goal],
      offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
      next: Option[com.histmersion.api.Offset] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(goals.size))

  object List:

    type OfTotal = OfTotal.T
    object OfTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfOffset = OfOffset.T
    object OfOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(total: com.histmersion.api.automation.Goal.List.OfTotal, rows: scala.List[Goal], offset: com.histmersion.api.automation.Goal.List.OfOffset): List =
      List(com.histmersion.api.RowCount((total).value), rows, com.histmersion.api.Offset((offset).value), OffsetPage(com.histmersion.api.RowCount(total), com.histmersion.api.Offset(offset), com.histmersion.api.RowCount(rows.size)).next)

  final case class Patch(
      title: Option[com.histmersion.api.automation.Goal.Title] = None,
      status: Option[com.histmersion.api.automation.Goal.StatusValue] = None,
      notes: Option[com.histmersion.api.automation.Goal.Notes] = None,
      tags: Option[scala.List[com.histmersion.api.automation.Goal.Tags]] = None,
      regions: Option[scala.List[com.histmersion.api.automation.Goal.Regions]] = None,
      periodStart: Option[com.histmersion.api.catalogue.Year] = None,
      periodEnd: Option[com.histmersion.api.catalogue.Year] = None,
      parentSlug: Option[com.histmersion.api.automation.Goal.Slug] = None,
      budgetUsd: Option[Patch.BudgetUsd] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Patch:


    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  object Plans:

    final case class PlannedGoal(
        slug: com.histmersion.api.automation.Goal.Slug,
        title: com.histmersion.api.automation.Goal.Title,
        notes: Option[com.histmersion.api.automation.Goal.Notes] = None,
        tags: scala.List[com.histmersion.api.automation.Goal.Tags] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema


    final case class PlannedWork(
        doi: com.histmersion.api.library.Doi,
        title: Option[com.histmersion.api.library.Title] = None,
        year: Option[com.histmersion.api.catalogue.Year] = None,
        authors: scala.List[com.histmersion.api.library.AuthorName] = Nil,
        citekey: Option[com.histmersion.api.library.Citekey] = None,
        inCorpus: PlannedWork.InCorpus = _root_.com.histmersion.api.automation.Goal.Plans.PlannedWork.InCorpus(false),
        isOa: PlannedWork.IsOa = _root_.com.histmersion.api.automation.Goal.Plans.PlannedWork.IsOa(false),
        why: PlannedWork.Why = _root_.com.histmersion.api.automation.Goal.Plans.PlannedWork.Why("")
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object PlannedWork:
      type InCorpus = InCorpus.T
      object InCorpus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type IsOa = IsOa.T
      object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class Plan(
        goal: com.histmersion.api.automation.Goal.Slug,
        rationale: Plan.Rationale = _root_.com.histmersion.api.automation.Goal.Plans.Plan.Rationale(""),
        subGoals: scala.List[PlannedGoal] = Nil,
        queries: scala.List[Plan.Queries] = Nil,
        works: scala.List[PlannedWork] = Nil,
        dropped: scala.List[Plan.Dropped] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def isEmpty: com.histmersion.api.automation.Goal.Plans.Plan.IsEmptyResult = _root_.com.histmersion.api.automation.Goal.Plans.Plan.IsEmptyResult(subGoals.isEmpty && queries.isEmpty && works.isEmpty)

    object Plan:
      type IsEmptyResult = IsEmptyResult.T
      object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Rationale = Rationale.T
      object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Queries = Queries.T
      object Queries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Dropped = Dropped.T
      object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    export com.histmersion.api.automation.Goal.LinkKind as LinkKind
    export com.histmersion.api.research.DraftMode
    export com.histmersion.api.automation.ScheduleKind

    final case class ArticleDraft(
        slug: com.histmersion.api.research.Article.Slug,
        mode: DraftMode,
        title: com.histmersion.api.research.Article.Title,
        kind: com.histmersion.api.research.Article.KindValue,
        text: ArticleDraft.Text,
        summary: Option[com.histmersion.api.research.Article.SummaryText] = None,
        section: Option[ArticleDraft.Section] = None,
        cited: scala.List[com.histmersion.api.library.Citekey] = Nil,
        notUsed: scala.List[com.histmersion.api.library.Citekey] = Nil,
        problems: scala.List[ArticleDraft.Problems] = Nil,
        filter: ArticleDraft.Filter = _root_.com.histmersion.api.automation.Goal.Plans.ArticleDraft.Filter(""),
        reasons: scala.List[Coverage.Reason] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object ArticleDraft:
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Section = Section.T
      object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Problems = Problems.T
      object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Filter = Filter.T
      object Filter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class GoalLink(
        kind: LinkKind,
        ref: GoalLink.Ref,
        note: Option[GoalLink.Note] = None,
        title: Option[GoalLink.Title] = None,
        createdAt: GoalLink.CreatedAt = _root_.com.histmersion.api.automation.Goal.Plans.GoalLink.CreatedAt(""),
        queue: Option[GoalLink.Queue] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object GoalLink:
      type Ref = Ref.T
      object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Title = Title.T
      object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type CreatedAt = CreatedAt.T
      object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Queue = Queue.T
      object Queue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class LinkAdd(kind: LinkKind, ref: LinkAdd.Ref, note: Option[LinkAdd.Note] = None) derives KindlingsSchema, com.histmersion.api.json.Schema
    object LinkAdd:


      type Ref = Ref.T
      object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      given Codec.AsObject[LinkAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[LinkAdd])

    /** `cron`: five fields in UTC, or `@daily`… */
    final case class GoalSchedule(
        goal: com.histmersion.api.automation.Goal.Slug,
        kind: ScheduleKind,
        cron: GoalSchedule.Cron,
        enabled: GoalSchedule.Enabled,
        budgetUsd: Option[GoalSchedule.BudgetUsd] = None,
        nextRunAt: Option[GoalSchedule.NextRunAt] = None,
        lastRunAt: Option[GoalSchedule.LastRunAt] = None,
        lastRun: Option[GoalSchedule.LastRun] = None,
        lastNote: Option[GoalSchedule.LastNote] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object GoalSchedule:
      type Cron = Cron.T
      object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Enabled = Enabled.T
      object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type BudgetUsd = BudgetUsd.T
      object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type NextRunAt = NextRunAt.T
      object NextRunAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type LastRunAt = LastRunAt.T
      object LastRunAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type LastRun = LastRun.T
      object LastRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type LastNote = LastNote.T
      object LastNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class ScheduleSet(cron: ScheduleSet.Cron, enabled: ScheduleSet.Enabled = _root_.com.histmersion.api.automation.Goal.Plans.ScheduleSet.Enabled(true), budgetUsd: Option[ScheduleSet.BudgetUsd] = None)
        derives KindlingsSchema, com.histmersion.api.json.Schema
    object ScheduleSet:


      type Cron = Cron.T
      object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Enabled = Enabled.T
      object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type BudgetUsd = BudgetUsd.T
      object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

      given Codec.AsObject[ScheduleSet] = StrictJson.codec(KindlingsCodecAsObject.derived[ScheduleSet])

    final case class Removed(removed: Removed.Value) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

    object Removed:
      type Value = Value.T
      object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class GoalView(
        goal: Goal,
        children: scala.List[Goal] = Nil,
        links: scala.List[GoalLink] = Nil,
        schedules: scala.List[GoalSchedule] = Nil,
        plan: Option[Plan] = None,
        budgetUsd: Option[GoalView.BudgetUsd] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object GoalView:
      type BudgetUsd = BudgetUsd.T
      object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Put(
      title: com.histmersion.api.automation.Goal.Title,
      status: com.histmersion.api.automation.Goal.StatusValue = _root_.com.histmersion.api.automation.Goal.StatusValue("planned"),
      notes: Option[com.histmersion.api.automation.Goal.Notes] = None,
      tags: scala.List[com.histmersion.api.automation.Goal.Tags] = Nil,
      regions: scala.List[com.histmersion.api.automation.Goal.Regions] = Nil,
      periodStart: Option[com.histmersion.api.catalogue.Year] = None,
      periodEnd: Option[com.histmersion.api.catalogue.Year] = None,
      parentSlug: Option[com.histmersion.api.automation.Goal.Slug] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Put:


    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])

  object Stages:
    type AllPart1 = AllPart1.T
    object AllPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllPart2 = AllPart2.T
    object AllPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MeaningResult = MeaningResult.T
    object MeaningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MeaningStatus = MeaningStatus.T
    object MeaningStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val all: scala.List[(com.histmersion.api.automation.Goal.Stages.AllPart1, com.histmersion.api.automation.Goal.Stages.AllPart2)] = scala.List(
      { val refactorTuple6 = com.histmersion.api.automation.Goal.Status.Planned -> "created; nothing works on it yet"; (_root_.com.histmersion.api.automation.Goal.Stages.AllPart1(refactorTuple6._1), _root_.com.histmersion.api.automation.Goal.Stages.AllPart2(refactorTuple6._2)) },
      { val refactorTuple7 = com.histmersion.api.automation.Goal.Status.Seeding -> "finding and reading sources, writing its articles"; (_root_.com.histmersion.api.automation.Goal.Stages.AllPart1(refactorTuple7._1), _root_.com.histmersion.api.automation.Goal.Stages.AllPart2(refactorTuple7._2)) },
      { val refactorTuple3 = com.histmersion.api.automation.Goal.Status.Compiled -> "its articles are written; not all have passed review yet"; (_root_.com.histmersion.api.automation.Goal.Stages.AllPart1(refactorTuple3._1), _root_.com.histmersion.api.automation.Goal.Stages.AllPart2(refactorTuple3._2)) },
      { val refactorTuple9 = com.histmersion.api.automation.Goal.Status.Reviewed -> "every article (and sub-goal) passed the review panel"; (_root_.com.histmersion.api.automation.Goal.Stages.AllPart1(refactorTuple9._1), _root_.com.histmersion.api.automation.Goal.Stages.AllPart2(refactorTuple9._2)) },
      { val refactorTuple10 = com.histmersion.api.automation.Goal.Status.Done -> "finished: only you set this, the autopilot never does"; (_root_.com.histmersion.api.automation.Goal.Stages.AllPart1(refactorTuple10._1), _root_.com.histmersion.api.automation.Goal.Stages.AllPart2(refactorTuple10._2)) }
    )

    def meaning(status: com.histmersion.api.automation.Goal.Stages.MeaningStatus): Option[com.histmersion.api.automation.Goal.Stages.MeaningResult] = all.collectFirst { case (s, m) if s == status => m }
