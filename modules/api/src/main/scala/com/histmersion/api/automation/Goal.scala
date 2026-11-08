package com.histmersion
package api.automation
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
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.library.Doi
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Citekey

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
    updatedAt: Option[Goal.UpdatedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Goal:
  type Export = Export.T
  object Export extends RefinedType[String, Pure]
  type LinkSectionKey = LinkSectionKey.T
  object LinkSectionKey extends RefinedType[String, Pure]
  type Ref = Ref.T
  object Ref extends RefinedType[String, Pure]
  type Stage = Stage.T
  object Stage extends RefinedType[String, Pure]
  type StageMeaning = StageMeaning.T
  object StageMeaning extends RefinedType[String, Pure]
  type Uid = Uid.T
  object Uid extends RefinedType[String, Pure]

  /** The stages of a research goal, as the store and the wire spell them. */
  type Slug = Slug.T
  object Slug extends RefinedType[String, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type StatusValue = StatusValue.T
  object StatusValue extends RefinedType[String, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)
  type Notes = Notes.T
  object Notes extends RefinedType[String, Pure]
  type Tags = Tags.T
  object Tags extends RefinedType[String, Pure]
  type Regions = Regions.T
  object Regions extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]

  object Status:
    type All = All.T
    object All extends RefinedType[String, Pure]

    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    val all: scala.List[All] = scala.List(All(Planned), All(Seeding), All(Compiled), All(Reviewed), All(Done))

  /** What a goal is linked to. */
  enum LinkKind(val wire: LinkKind.Wire):
    case Source extends LinkKind(LinkKind.Wire("source"))
    case Article extends LinkKind(LinkKind.Wire("article"))
    case Query extends LinkKind(LinkKind.Wire("query"))
    case QueueItem extends LinkKind(LinkKind.Wire("queue-item"))

  object LinkKind:

    /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]


    val SearchQuery: LinkKind = Query

    def parse(s: Wire): Option[LinkKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = values
      def wire(e: LinkKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[LinkKind] = parse(Wire((s).value))


  final case class Deleted(
      slug: Slug,
      deleted: Deleted.Value
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends RefinedType[Boolean, Pure]



  final case class List(
      total: RowCount,
      goals: scala.List[Goal],
      offset: Offset = Offset(0),
      next: Option[Offset] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, RowCount(goals.size))

  object List:


    def of(total: RowCount, rows: scala.List[Goal], offset: UnreadPage.Offset): List =
      List(RowCount((total).value), rows, Offset((offset).value), OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(rows.size)).next)

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
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Patch:


    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends RefinedType[Double, Pure]

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  object Plans:

    final case class PlannedGoal(
        slug: Slug,
        title: Title,
        notes: Option[Notes] = None,
        tags: scala.List[Tags] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema


    final case class PlannedWork(
        doi: Doi,
        title: Option[com.histmersion.api.library.Title] = None,
        year: Option[Year] = None,
        authors: scala.List[AuthorName] = Nil,
        citekey: Option[Citekey] = None,
        inCorpus: PlannedWork.InCorpus = PlannedWork.InCorpus(false),
        isOa: PlannedWork.IsOa = PlannedWork.IsOa(false),
        why: PlannedWork.Why = PlannedWork.Why("")
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object PlannedWork:
      type InCorpus = InCorpus.T
      object InCorpus extends RefinedType[Boolean, Pure]
      type IsOa = IsOa.T
      object IsOa extends RefinedType[Boolean, Pure]
      type Why = Why.T
      object Why extends RefinedType[String, Pure]


    final case class Plan(
        goal: Slug,
        rationale: Plan.Rationale = Plan.Rationale(""),
        subGoals: scala.List[PlannedGoal] = Nil,
        queries: scala.List[Plan.Queries] = Nil,
        works: scala.List[PlannedWork] = Nil,
        dropped: scala.List[Plan.Dropped] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def isEmpty: Boolean = (subGoals.isEmpty && queries.isEmpty && works.isEmpty)

    object Plan:

      type Rationale = Rationale.T
      object Rationale extends RefinedType[String, Pure]
      type Queries = Queries.T
      object Queries extends RefinedType[String, Pure]
      type Dropped = Dropped.T
      object Dropped extends RefinedType[String, Pure]


    export Goal.LinkKind as LinkKind
    export com.histmersion.api.research.DraftMode
    export com.histmersion.api.automation.ScheduleKind

    final case class ArticleDraft(
        slug: com.histmersion.api.research.Article.Slug,
        mode: DraftMode,
        title: com.histmersion.api.research.Article.Title,
        kind: com.histmersion.api.research.Article.Kind.Wire,
        text: ArticleDraft.Text,
        summary: Option[com.histmersion.api.research.Article.SummaryText] = None,
        section: Option[ArticleDraft.Section] = None,
        cited: scala.List[Citekey] = Nil,
        notUsed: scala.List[Citekey] = Nil,
        problems: scala.List[ArticleDraft.Problems] = Nil,
        filter: ArticleDraft.Filter = ArticleDraft.Filter(""),
        reasons: scala.List[Coverage.Reason] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object ArticleDraft:
      type Text = Text.T
      object Text extends RefinedType[String, Pure]
      type Section = Section.T
      object Section extends RefinedType[String, Pure]
      type Problems = Problems.T
      object Problems extends RefinedType[String, Pure]
      type Filter = Filter.T
      object Filter extends RefinedType[String, Pure]


    final case class GoalLink(
        kind: LinkKind,
        ref: GoalLink.Ref,
        note: Option[GoalLink.Note] = None,
        title: Option[GoalLink.Title] = None,
        createdAt: GoalLink.CreatedAt = GoalLink.CreatedAt(""),
        queue: Option[GoalLink.Queue] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object GoalLink:
      type QueueNumber = QueueNumber.T
      object QueueNumber extends RefinedType[Int, Pure]
      type Ref = Ref.T
      object Ref extends RefinedType[String, Pure]
      type Note = Note.T
      object Note extends RefinedType[String, Pure]
      type Title = Title.T
      object Title extends RefinedType[String, Pure]
      type CreatedAt = CreatedAt.T
      object CreatedAt extends RefinedType[String, Pure]
      type Queue = Queue.T
      object Queue extends RefinedType[String, Pure]


    final case class LinkAdd(kind: LinkKind, ref: LinkAdd.Ref, note: Option[LinkAdd.Note] = None) derives KindlingsSchema, com.histmersion.api.json.Schema
    object LinkAdd:


      type Ref = Ref.T
      object Ref extends RefinedType[String, Pure]
      type Note = Note.T
      object Note extends RefinedType[String, Pure]

      given Codec.AsObject[LinkAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[LinkAdd])

    /** `cron`: five fields in UTC, or `@daily`… */
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
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object GoalSchedule:
      type Cron = Cron.T
      object Cron extends RefinedType[String, Pure]
      type Enabled = Enabled.T
      object Enabled extends RefinedType[Boolean, Pure]
      type BudgetUsd = BudgetUsd.T
      object BudgetUsd extends RefinedType[Double, Pure]
      type NextRunAt = NextRunAt.T
      object NextRunAt extends RefinedType[String, Pure]
      type LastRunAt = LastRunAt.T
      object LastRunAt extends RefinedType[String, Pure]
      type LastRun = LastRun.T
      object LastRun extends RefinedType[String, Pure]
      type LastNote = LastNote.T
      object LastNote extends RefinedType[String, Pure]


    final case class ScheduleSet(cron: ScheduleSet.Cron, enabled: ScheduleSet.Enabled = ScheduleSet.Enabled(true), budgetUsd: Option[ScheduleSet.BudgetUsd] = None)
        derives KindlingsSchema, com.histmersion.api.json.Schema
    object ScheduleSet:


      type Cron = Cron.T
      object Cron extends RefinedType[String, Pure]
      type Enabled = Enabled.T
      object Enabled extends RefinedType[Boolean, Pure]
      type BudgetUsd = BudgetUsd.T
      object BudgetUsd extends RefinedType[Double, Pure]

      given Codec.AsObject[ScheduleSet] = StrictJson.codec(KindlingsCodecAsObject.derived[ScheduleSet])

    final case class Removed(removed: Removed.Value) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

    object Removed:
      type Value = Value.T
      object Value extends RefinedType[Boolean, Pure]


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
      object BudgetUsd extends RefinedType[Double, Pure]


  final case class Put(
      title: Title,
      status: StatusValue = StatusValue("planned"),
      notes: Option[Notes] = None,
      tags: scala.List[Tags] = Nil,
      regions: scala.List[Regions] = Nil,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None,
      parentSlug: Option[Slug] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Put:


    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])

  object Stages:
    export Goal.{Stage as AllPart1, StageMeaning as AllPart2, StageMeaning as MeaningResult, Stage as MeaningStatus}

    val all: scala.List[(Stage, StageMeaning)] = scala.List(
      { val refactorTuple6 = Status.Planned -> "created; nothing works on it yet"; (Stage(refactorTuple6._1), StageMeaning(refactorTuple6._2)) },
      { val refactorTuple7 = Status.Seeding -> "finding and reading sources, writing its articles"; (Stage(refactorTuple7._1), StageMeaning(refactorTuple7._2)) },
      { val refactorTuple3 = Status.Compiled -> "its articles are written; not all have passed review yet"; (Stage(refactorTuple3._1), StageMeaning(refactorTuple3._2)) },
      { val refactorTuple9 = Status.Reviewed -> "every article (and sub-goal) passed the review panel"; (Stage(refactorTuple9._1), StageMeaning(refactorTuple9._2)) },
      { val refactorTuple10 = Status.Done -> "finished: only you set this, the autopilot never does"; (Stage(refactorTuple10._1), StageMeaning(refactorTuple10._2)) }
    )

    def meaning(status: Stage): Option[StageMeaning] = all.collectFirst { case (s, m) if s == status => StageMeaning((m).value) }
