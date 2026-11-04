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

final case class Goal(
    slug: String,
    title: String,
    status: String,
    notes: Option[String] = None,
    tags: scala.List[String] = Nil,
    regions: scala.List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None,
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object Goal:

  /** The stages of a research goal, as the store and the wire spell them. */
  object Status:
    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    val all: scala.List[String] = scala.List(Planned, Seeding, Compiled, Reviewed, Done)

  /** What a goal is linked to. */
  enum LinkKind(val wire: String):
    case Source extends LinkKind("source")
    case Article extends LinkKind("article")
    case Query extends LinkKind("query")
    case QueueItem extends LinkKind("queue-item")

  object LinkKind:

    /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
    val SearchQuery: LinkKind = Query

    def parse(s: String): Option[LinkKind] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = LinkKind.values
      def wire(e: LinkKind): String = e.wire
      def fromWire(s: String): Option[LinkKind] = parse(s)
  given Schema[Goal] = summon[KindlingsSchema[Goal]].schema

  final case class Deleted(
      slug: String,
      deleted: Boolean
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Deleted:
    given Schema[Deleted] = summon[KindlingsSchema[Deleted]].schema

  final case class List(
      total: Int,
      goals: scala.List[Goal],
      offset: Int = 0,
      next: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def page: OffsetPage = OffsetPage(total, offset, goals.size)

  object List:
    given Schema[List] = summon[KindlingsSchema[List]].schema
    def of(total: Int, rows: scala.List[Goal], offset: Int): List =
      List(total, rows, offset, OffsetPage(total, offset, rows.size).next)

  final case class Patch(
      title: Option[String] = None,
      status: Option[String] = None,
      notes: Option[String] = None,
      tags: Option[scala.List[String]] = None,
      regions: Option[scala.List[String]] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None,
      budgetUsd: Option[Double] = None
  ) derives KindlingsSchema

  object Patch:
    given Schema[Patch] = summon[KindlingsSchema[Patch]].schema

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  object Plans:

    final case class PlannedGoal(
        slug: String,
        title: String,
        notes: Option[String] = None,
        tags: scala.List[String] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema
    object PlannedGoal:
      given Schema[PlannedGoal] = summon[KindlingsSchema[PlannedGoal]].schema

    final case class PlannedWork(
        doi: String,
        title: Option[String] = None,
        year: Option[Int] = None,
        authors: scala.List[String] = Nil,
        citekey: Option[String] = None,
        inCorpus: Boolean = false,
        isOa: Boolean = false,
        why: String = ""
    ) derives KindlingsCodecAsObject,
          KindlingsSchema
    object PlannedWork:
      given Schema[PlannedWork] = summon[KindlingsSchema[PlannedWork]].schema

    final case class Plan(
        goal: String,
        rationale: String = "",
        subGoals: scala.List[PlannedGoal] = Nil,
        queries: scala.List[String] = Nil,
        works: scala.List[PlannedWork] = Nil,
        dropped: scala.List[String] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema:
      def isEmpty: Boolean = subGoals.isEmpty && queries.isEmpty && works.isEmpty
    object Plan:
      given Schema[Plan] = summon[KindlingsSchema[Plan]].schema

    export com.histmersion.api.automation.Goal.LinkKind as LinkKind
    export com.histmersion.api.research.DraftMode
    export com.histmersion.api.automation.ScheduleKind

    final case class ArticleDraft(
        slug: String,
        mode: DraftMode,
        title: String,
        kind: String,
        text: String,
        summary: Option[String] = None,
        section: Option[String] = None,
        cited: scala.List[String] = Nil,
        notUsed: scala.List[String] = Nil,
        problems: scala.List[String] = Nil,
        filter: String = "",
        reasons: scala.List[Coverage.Reason] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema
    object ArticleDraft:
      given Schema[ArticleDraft] = summon[KindlingsSchema[ArticleDraft]].schema

    final case class GoalLink(
        kind: LinkKind,
        ref: String,
        note: Option[String] = None,
        title: Option[String] = None,
        createdAt: String = "",
        queue: Option[String] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema
    object GoalLink:
      given Schema[GoalLink] = summon[KindlingsSchema[GoalLink]].schema

    final case class LinkAdd(kind: LinkKind, ref: String, note: Option[String] = None) derives KindlingsSchema
    object LinkAdd:
      given Schema[LinkAdd] = summon[KindlingsSchema[LinkAdd]].schema

      given Codec.AsObject[LinkAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[LinkAdd])

    /** `cron`: five fields in UTC, or `@daily`… */
    final case class GoalSchedule(
        goal: String,
        kind: ScheduleKind,
        cron: String,
        enabled: Boolean,
        budgetUsd: Option[Double] = None,
        nextRunAt: Option[String] = None,
        lastRunAt: Option[String] = None,
        lastRun: Option[String] = None,
        lastNote: Option[String] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema
    object GoalSchedule:
      given Schema[GoalSchedule] = summon[KindlingsSchema[GoalSchedule]].schema

    final case class ScheduleSet(cron: String, enabled: Boolean = true, budgetUsd: Option[Double] = None)
        derives KindlingsSchema
    object ScheduleSet:
      given Schema[ScheduleSet] = summon[KindlingsSchema[ScheduleSet]].schema

      given Codec.AsObject[ScheduleSet] = StrictJson.codec(KindlingsCodecAsObject.derived[ScheduleSet])

    final case class Removed(removed: Boolean) derives KindlingsCodecAsObject, KindlingsSchema
    object Removed:
      given Schema[Removed] = summon[KindlingsSchema[Removed]].schema

    final case class GoalView(
        goal: Goal,
        children: scala.List[Goal] = Nil,
        links: scala.List[GoalLink] = Nil,
        schedules: scala.List[GoalSchedule] = Nil,
        plan: Option[Plan] = None,
        budgetUsd: Option[Double] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema
    object GoalView:
      given Schema[GoalView] = summon[KindlingsSchema[GoalView]].schema

  final case class Put(
      title: String,
      status: String = "planned",
      notes: Option[String] = None,
      tags: scala.List[String] = Nil,
      regions: scala.List[String] = Nil,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None
  ) derives KindlingsSchema

  object Put:
    given Schema[Put] = summon[KindlingsSchema[Put]].schema

    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])

  object Stages:
    val all: scala.List[(String, String)] = scala.List(
      com.histmersion.api.automation.Goal.Status.Planned -> "created; nothing works on it yet",
      com.histmersion.api.automation.Goal.Status.Seeding -> "finding and reading sources, writing its articles",
      com.histmersion.api.automation.Goal.Status.Compiled -> "its articles are written; not all have passed review yet",
      com.histmersion.api.automation.Goal.Status.Reviewed -> "every article (and sub-goal) passed the review panel",
      com.histmersion.api.automation.Goal.Status.Done -> "finished: only you set this, the autopilot never does"
    )

    def meaning(status: String): Option[String] = all.collectFirst { case (s, m) if s == status => m }
