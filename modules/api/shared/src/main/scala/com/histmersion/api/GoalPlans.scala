package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** What a person approves before a P3 pipeline creates anything (docs/research-embedded-agents.md
  * §14): a goal's PLAN (sub-goals, search queries, candidate works — each DOI already looked up
  * upstream by code) and an article DRAFT (a new article, or a section to append to one, with the
  * code self-check's remaining problems). They travel inside an approval ([[Agents.ApprovalInfo]]),
  * are stored as the approval's payload, and the run that proposed them applies exactly this value
  * when it resumes. Wire types only; the logic is in `core/jvm` and the server. */
object GoalPlans:

  /** A sub-goal a plan proposes (created as a child of the planned goal when approved). */
  final case class PlannedGoal(slug: String, title: String, notes: Option[String] = None, tags: List[String] = Nil)
      derives KindlingsCodecAsObject
  object PlannedGoal:
    given Schema[PlannedGoal] = KindlingsSchema.derived[PlannedGoal].schema

  /** A work a plan proposes to read, as code found it upstream: `inCorpus` works are linked to the
    * goal, the others become acquisition items. `why` is the planner's reason. */
  final case class PlannedWork(doi: String, title: Option[String] = None, year: Option[Int] = None,
      authors: List[String] = Nil, citekey: Option[String] = None, inCorpus: Boolean = false, isOa: Boolean = false,
      why: String = "") derives KindlingsCodecAsObject
  object PlannedWork:
    given Schema[PlannedWork] = KindlingsSchema.derived[PlannedWork].schema

  /** A verified plan. `dropped` says what code removed from the planner's answer, and why (a DOI
    * that does not resolve, a sub-goal slug already taken…), so a person sees the whole story. */
  final case class Plan(goal: String, rationale: String = "", subGoals: List[PlannedGoal] = Nil,
      queries: List[String] = Nil, works: List[PlannedWork] = Nil, dropped: List[String] = Nil)
      derives KindlingsCodecAsObject:
    def isEmpty: Boolean = subGoals.isEmpty && queries.isEmpty && works.isEmpty
  object Plan:
    given Schema[Plan] = KindlingsSchema.derived[Plan].schema

  /** Whether a compile run writes a NEW article or APPENDS a section to an existing one. */
  enum DraftMode(val wire: String):
    case Create extends DraftMode("create")
    case Extend extends DraftMode("extend")

  object DraftMode:
    def parse(s: String): Option[DraftMode] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[DraftMode]:
      def all: Array[DraftMode] = DraftMode.values
      def wire(e: DraftMode): String = e.wire
      def fromWire(s: String): Option[DraftMode] = parse(s)
    given Encoder[DraftMode] = WireEnum.encoder
    given Decoder[DraftMode] = WireEnum.decoder
    given Schema[DraftMode]  = WireEnum.schema

  /** An article a compile run drafted, as a person approves it. `text` is the whole body (create) or
    * the section to append (extend, under the heading `section` when given, else at the end).
    * `problems` are what the code self-check still found after the writer's last attempt; `cited`
    * the citekeys the text cites, `notUsed` the filter's sources it leaves out, with why (for the
    * person; `reasons` is what is stored, and the page lists them under its generated Not used). */
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
      /** The reasons the write stores for the filter's sources the text leaves out (#958). */
      reasons: List[CoverageReason] = Nil
  ) derives KindlingsCodecAsObject
  object ArticleDraft:
    given Schema[ArticleDraft] = KindlingsSchema.derived[ArticleDraft].schema

  // ── goal links and schedules ───────────────────────────────────────────────────────────────────

  /** What a goal is linked to. */
  enum LinkKind(val wire: String):
    case Source    extends LinkKind("source")
    case Article   extends LinkKind("article")
    case Query     extends LinkKind("query")
    case QueueItem extends LinkKind("queue-item")

  object LinkKind:
    def parse(s: String): Option[LinkKind] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = LinkKind.values
      def wire(e: LinkKind): String = e.wire
      def fromWire(s: String): Option[LinkKind] = parse(s)
    given Encoder[LinkKind] = WireEnum.encoder
    given Decoder[LinkKind] = WireEnum.decoder
    given Schema[LinkKind]  = WireEnum.schema

  /** One link of a goal. `title` is the source's or article's title (or the item's text) when the
    * target still exists; `None` marks a dangling link. */
  final case class GoalLink(kind: LinkKind, ref: String, note: Option[String] = None, title: Option[String] = None,
      createdAt: String = "") derives KindlingsCodecAsObject
  object GoalLink:
    given Schema[GoalLink] = KindlingsSchema.derived[GoalLink].schema

  /** `POST /api/goals/{slug}/links`. */
  final case class LinkAdd(kind: LinkKind, ref: String, note: Option[String] = None)
  object LinkAdd:
    given Schema[LinkAdd] = KindlingsSchema.derived[LinkAdd].schema
    /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
    given Codec.AsObject[LinkAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[LinkAdd])

  /** What a goal's schedule starts: a currency check of its sources, or an acquisition sweep of its
    * queries. */
  enum ScheduleKind(val wire: String, val pipeline: Agents.Pipeline):
    case Currency    extends ScheduleKind("currency", Agents.Pipeline.CurrencyCheck)
    case Acquisition extends ScheduleKind("acquisition", Agents.Pipeline.AcquisitionSweep)

  object ScheduleKind:
    def parse(s: String): Option[ScheduleKind] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[ScheduleKind]:
      def all: Array[ScheduleKind] = ScheduleKind.values
      def wire(e: ScheduleKind): String = e.wire
      def fromWire(s: String): Option[ScheduleKind] = parse(s)
    given Encoder[ScheduleKind] = WireEnum.encoder
    given Decoder[ScheduleKind] = WireEnum.decoder
    given Schema[ScheduleKind]  = WireEnum.schema

  /** One schedule of a goal. `cron` is five fields in UTC (or `@daily`…); `lastNote` says what the
    * scheduler last did (started run X, or skipped: why). */
  final case class GoalSchedule(goal: String, kind: ScheduleKind, cron: String, enabled: Boolean,
      budgetUsd: Option[Double] = None, nextRunAt: Option[String] = None, lastRunAt: Option[String] = None,
      lastRun: Option[String] = None, lastNote: Option[String] = None) derives KindlingsCodecAsObject
  object GoalSchedule:
    given Schema[GoalSchedule] = KindlingsSchema.derived[GoalSchedule].schema

  /** `PUT /api/goals/{slug}/schedules/{kind}` — create or change a schedule. */
  final case class ScheduleSet(cron: String, enabled: Boolean = true, budgetUsd: Option[Double] = None)
  object ScheduleSet:
    given Schema[ScheduleSet] = KindlingsSchema.derived[ScheduleSet].schema
    /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
    given Codec.AsObject[ScheduleSet] = StrictJson.codec(KindlingsCodecAsObject.derived[ScheduleSet])

  /** `DELETE /api/goals/{slug}/schedules/{kind}` and `…/links`. */
  final case class Removed(removed: Boolean) derives KindlingsCodecAsObject
  object Removed:
    given Schema[Removed] = KindlingsSchema.derived[Removed].schema

  /** `GET /api/goals/{slug}/view` — a goal with what hangs off it. */
  final case class GoalView(goal: Goal, children: List[Goal] = Nil, links: List[GoalLink] = Nil,
      schedules: List[GoalSchedule] = Nil, plan: Option[Plan] = None, budgetUsd: Option[Double] = None)
      derives KindlingsCodecAsObject
  object GoalView:
    given Schema[GoalView] = KindlingsSchema.derived[GoalView].schema
