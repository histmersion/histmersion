package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

object GoalPlans:

  final case class PlannedGoal(slug: String, title: String, notes: Option[String] = None, tags: List[String] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object PlannedGoal:
    given Schema[PlannedGoal] = summon[KindlingsSchema[PlannedGoal]].schema

  final case class PlannedWork(
      doi: String,
      title: Option[String] = None,
      year: Option[Int] = None,
      authors: List[String] = Nil,
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
      subGoals: List[PlannedGoal] = Nil,
      queries: List[String] = Nil,
      works: List[PlannedWork] = Nil,
      dropped: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isEmpty: Boolean = subGoals.isEmpty && queries.isEmpty && works.isEmpty
  object Plan:
    given Schema[Plan] = summon[KindlingsSchema[Plan]].schema

  export com.histmersion.values.{DraftMode, GoalLinkKind as LinkKind, ScheduleKind}

  final case class ArticleDraft(
      slug: String,
      mode: DraftMode,
      title: String,
      kind: String,
      text: String,
      summary: Option[String] = None,
      section: Option[String] = None,
      cited: List[String] = Nil,
      notUsed: List[String] = Nil,
      problems: List[String] = Nil,
      filter: String = "",
      reasons: List[CoverageReason] = Nil
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
      children: List[Goal] = Nil,
      links: List[GoalLink] = Nil,
      schedules: List[GoalSchedule] = Nil,
      plan: Option[Plan] = None,
      budgetUsd: Option[Double] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object GoalView:
    given Schema[GoalView] = summon[KindlingsSchema[GoalView]].schema
