package com.histmersion.api.automation
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.research.Article

object Managers:

  export com.histmersion.domain.automation.Manager.Kind as Kind
  export com.histmersion.domain.automation.Manager.State as State
  export com.histmersion.domain.automation.ItemStatus

  enum ItemOrigin(val wire: String):
    case Selection extends ItemOrigin("selection")
    case Person extends ItemOrigin("person")
    case Chain extends ItemOrigin("chain")
    case Hint extends ItemOrigin("hint")

    /** An item an ingest added, by a name no other type takes. */
    case FromIngest extends ItemOrigin("ingest")

    case Cron extends ItemOrigin("cron")

  object ItemOrigin:
    def parse(s: String): Option[ItemOrigin] = values.find(_.wire == s.trim.toLowerCase)

  object Feed:
    val Started = "started"
    val Settled = "settled"
    val Evaluated = "evaluated"
    val Stopped = "stopped"
    val Escalated = "escalated"
    val Held = "held"
    val Hint = "hint"
    val Paused = "paused"
    val Stall = "stall"
    val Starved = "starved"

    val Swept = "swept"

    val Ingested = "ingested"

    val Cron = "cron"

    val Migrated = "migrated"

    val Approval = "approval"

    val Autopilot = "autopilot"

  val ArticleReview = "article-review"
  val InferFilter = "infer-filter"
  val ArticleExtend = "article-extend"
  val SourceEnrich = "source-enrich"
  val ProposeArticles = "propose-articles"
  val ArticleCompile = "article-compile"

  val AcquisitionSweep = "acquisition-sweep"
  val CurrencyCheck = "currency-check"

  val PlanGoalItem = "plan-goal"

  val OaIngest = "oa-ingest"

  def itemKinds(k: Kind): List[String] = k match
    case Kind.Review => List(ArticleReview, InferFilter, ArticleExtend)
    case Kind.Enrich => List(SourceEnrich)
    case Kind.Seed   => List(ProposeArticles, ArticleCompile, PlanGoalItem)
    case Kind.Watch  => List(AcquisitionSweep, CurrencyCheck, OaIngest)

  def itemKindOf(k: Kind, word: String): Option[String] =
    val w = word.trim.toLowerCase
    val kinds = itemKinds(k)
    if w.isEmpty then kinds.headOption
    else
      val alias = w match
        case "review" | "article"        => ArticleReview
        case "filter" | "infer"          => InferFilter
        case "extend"                    => ArticleExtend
        case "enrich" | "source"         => SourceEnrich
        case "propose" | "plan" | "seed" => ProposeArticles
        case "compile"                   => ArticleCompile
        case "sweep" | "acquisition"     => AcquisitionSweep
        case "currency"                  => CurrencyCheck
        case "plan-goal" | "goal-plan"   => PlanGoalItem
        case "ingest" | "backlog"        => OaIngest
        case other                       => other
      Option(alias).filter(kinds.contains)

  val ReviewId = "review"

  val EnrichId = "enrich"

  def seedId(goal: String): String = s"seed:${goal.trim}"

  def watchId(goal: String): String = s"watch:${goal.trim}"

  enum Sweep(val wire: String, val itemKind: String):
    case Acquisition extends Sweep("acquisition", AcquisitionSweep)
    case Currency extends Sweep("currency", CurrencyCheck)

  object Sweep:
    def parse(s: String): Option[Sweep] =
      values.find(x => x.wire == s.trim.toLowerCase || x.itemKind == s.trim.toLowerCase)
    def ofItem(kind: String): Option[Sweep] = values.find(_.itemKind == kind)

    def of(c: ManagerConfig): List[Sweep] =
      c.sweeps.fold(values.toList)(ws => values.toList.filter(s => ws.exists(w => parse(w).contains(s))))

  enum EnqueueKind(val wire: String):
    case Review extends EnqueueKind("review")
    case Extend extends EnqueueKind("extend")
    case Enrich extends EnqueueKind("enrich")
    case Seed extends EnqueueKind("seed")

  object EnqueueKind:
    def parse(s: String): Option[EnqueueKind] = s.trim.toLowerCase match
      case "" | "article" | ArticleReview       => Some(Review)
      case ArticleExtend                        => Some(Extend)
      case SourceEnrich | "source"              => Some(Enrich)
      case ProposeArticles | "propose" | "plan" => Some(Seed)
      case other                                => values.find(_.wire == other)

  final case class ArticleSpec(title: String, kind: String, filter: Article.Filter, why: String = "")
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ArticleSpec:
    given Schema[ArticleSpec] = summon[KindlingsSchema[ArticleSpec]].schema

  final case class ManagerConfig(
      maxRuns: Option[Int] = None,
      maxOpen: Option[Int] = None,
      rounds: Option[Int] = None,
      maxRunsPerArticle: Option[Int] = None,
      noProgressRuns: Option[Int] = None,
      reopenLimit: Option[Int] = None,
      cooldownMinutes: Option[Int] = None,
      lowWater: Option[Int] = None,
      refillBatch: Option[Int] = None,
      maxFailures: Option[Int] = None,
      budgetUsd: Option[Double] = None,
      includeTags: Option[List[String]] = None,
      includeGoal: Option[String] = None,
      models: Option[List[Agents.ModelOverride]] = None,
      staleAfterDays: Option[Int] = None,
      inferFilter: Option[Boolean] = None,
      minSources: Option[Int] = None,
      maxArticles: Option[Int] = None,
      sweeps: Option[List[String]] = None,
      autoIngest: Option[Boolean] = None,
      autoIngestPerDay: Option[Int] = None,
      dailyUsd: Option[Double] = None,
      autoApprove: Option[Boolean] = None,
      extendMinNew: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def merge(other: ManagerConfig): ManagerConfig = ManagerConfig(
      other.maxRuns.orElse(maxRuns),
      other.maxOpen.orElse(maxOpen),
      other.rounds.orElse(rounds),
      other.maxRunsPerArticle.orElse(maxRunsPerArticle),
      other.noProgressRuns.orElse(noProgressRuns),
      other.reopenLimit.orElse(reopenLimit),
      other.cooldownMinutes.orElse(cooldownMinutes),
      other.lowWater.orElse(lowWater),
      other.refillBatch.orElse(refillBatch),
      other.maxFailures.orElse(maxFailures),
      other.budgetUsd.orElse(budgetUsd),
      other.includeTags.orElse(includeTags),
      other.includeGoal.orElse(includeGoal),
      other.models.orElse(models),
      other.staleAfterDays.orElse(staleAfterDays),
      other.inferFilter.orElse(inferFilter),
      other.minSources.orElse(minSources),
      other.maxArticles.orElse(maxArticles),
      other.sweeps.orElse(sweeps),
      other.autoIngest.orElse(autoIngest),
      other.autoIngestPerDay.orElse(autoIngestPerDay),
      other.dailyUsd.orElse(dailyUsd),
      other.autoApprove.orElse(autoApprove),
      other.extendMinNew.orElse(extendMinNew)
    )
  object ManagerConfig:
    given Schema[ManagerConfig] = summon[KindlingsSchema[ManagerConfig]].schema

    def parse(kvs: List[String]): Either[String, ManagerConfig] =
      kvs.foldLeft[Either[String, ManagerConfig]](Right(ManagerConfig())) { (acc, kv) =>
        acc.flatMap { c =>
          kv.split("=", 2) match
            case Array(k0, v0) =>
              val (k, v) = (k0.trim, v0.trim)
              def int = v.toIntOption.filter(_ >= 0).toRight(s"$k: '$v' is not a whole number")
              k match
                case "maxRuns" | "max-runs"                       => int.map(n => c.copy(maxRuns = Some(n)))
                case "maxOpen" | "max-open"                       => int.map(n => c.copy(maxOpen = Some(n)))
                case "rounds"                                     => int.map(n => c.copy(rounds = Some(n)))
                case "maxRunsPerArticle" | "max-runs-per-article" => int.map(n => c.copy(maxRunsPerArticle = Some(n)))
                case "noProgressRuns" | "no-progress-runs"        => int.map(n => c.copy(noProgressRuns = Some(n)))
                case "reopenLimit" | "reopen-limit"               => int.map(n => c.copy(reopenLimit = Some(n)))
                case "cooldownMinutes" | "cooldown-minutes"       => int.map(n => c.copy(cooldownMinutes = Some(n)))
                case "lowWater" | "low-water"                     => int.map(n => c.copy(lowWater = Some(n)))
                case "refillBatch" | "refill-batch"               => int.map(n => c.copy(refillBatch = Some(n)))
                case "maxFailures" | "max-failures"               => int.map(n => c.copy(maxFailures = Some(n)))
                case "staleAfterDays" | "stale-after-days"        => int.map(n => c.copy(staleAfterDays = Some(n)))
                case "minSources" | "min-sources"                 => int.map(n => c.copy(minSources = Some(n)))
                case "maxArticles" | "max-articles"               => int.map(n => c.copy(maxArticles = Some(n)))
                case "autoIngestPerDay" | "auto-ingest-per-day"   => int.map(n => c.copy(autoIngestPerDay = Some(n)))
                case "inferFilter" | "infer-filter"               => bool(k, v).map(b => c.copy(inferFilter = Some(b)))
                case "autoIngest" | "auto-ingest"                 => bool(k, v).map(b => c.copy(autoIngest = Some(b)))
                case "autoApprove" | "auto-approve"               => bool(k, v).map(b => c.copy(autoApprove = Some(b)))
                case "extendMinNew" | "extend-min-new"            => int.map(n => c.copy(extendMinNew = Some(n)))
                case "sweeps"                                     =>
                  val ws = v.split(",").toList.map(_.trim).filter(_.nonEmpty)
                  ws.find(w => Sweep.parse(w).isEmpty) match
                    case Some(w) =>
                      Left(s"$k: '$w' is not a sweep (one of: ${Sweep.values.map(_.wire).mkString(", ")})")
                    case None => Right(c.copy(sweeps = Some(ws.flatMap(Sweep.parse).distinct.map(_.wire))))
                case "dailyUsd" | "daily-usd" =>
                  v.toDoubleOption
                    .filter(_ >= 0)
                    .toRight(s"$k: '$v' is not a number of USD (0 = no cap)")
                    .map(d => c.copy(dailyUsd = Some(d)))
                case "budgetUsd" | "budget-usd" =>
                  v.toDoubleOption
                    .filter(_ > 0)
                    .toRight(s"$k: '$v' is not a positive number")
                    .map(d => c.copy(budgetUsd = Some(d)))
                case "includeTags" | "include-tags" | "tags" =>
                  Right(c.copy(includeTags = Some(v.split(",").toList.map(_.trim).filter(_.nonEmpty))))
                case "includeGoal" | "include-goal" | "goal" =>
                  Right(c.copy(includeGoal = Option(v).filter(_.nonEmpty)))
                case "models" =>
                  v.split(";")
                    .toList
                    .map(_.trim)
                    .filter(_.nonEmpty)
                    .map(Agents.ModelOverride.parse)
                    .partitionMap(identity) match
                    case (Nil, ms) => Right(c.copy(models = Some(ms)))
                    case (errs, _) => Left(errs.mkString("; "))
                case other => Left(s"unknown manager setting '$other'")
            case _ => Left(s"'$kv' is not key=value")
        }
      }

    private def bool(k: String, v: String): Either[String, Boolean] = v.toLowerCase match
      case "true" | "on" | "yes"  => Right(true)
      case "false" | "off" | "no" => Right(false)
      case _                      => Left(s"$k: '$v' is not true or false")

  final case class ItemCounts(
      pending: Int = 0,
      held: Int = 0,
      active: Int = 0,
      done: Int = 0,
      stopped: Int = 0,
      failed: Int = 0,
      cancelled: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def open: Int = pending + held + active
  object ItemCounts:
    given Schema[ItemCounts] = summon[KindlingsSchema[ItemCounts]].schema

    def of(rows: List[(String, Int)]): ItemCounts =
      @tailrec def loop(
          rest: List[(String, Int)],
          pending: Int,
          held: Int,
          active: Int,
          done: Int,
          stopped: Int,
          failed: Int,
          cancelled: Int
      ): ItemCounts =
        rest match
          case Nil            => ItemCounts(pending, held, active, done, stopped, failed, cancelled)
          case (s, n) :: tail =>
            ItemStatus.parse(s) match
              case Some(ItemStatus.Pending)   => loop(tail, pending + n, held, active, done, stopped, failed, cancelled)
              case Some(ItemStatus.Held)      => loop(tail, pending, held + n, active, done, stopped, failed, cancelled)
              case Some(ItemStatus.Active)    => loop(tail, pending, held, active + n, done, stopped, failed, cancelled)
              case Some(ItemStatus.Done)      => loop(tail, pending, held, active, done + n, stopped, failed, cancelled)
              case Some(ItemStatus.Stopped)   => loop(tail, pending, held, active, done, stopped + n, failed, cancelled)
              case Some(ItemStatus.Failed)    => loop(tail, pending, held, active, done, stopped, failed + n, cancelled)
              case Some(ItemStatus.Cancelled) =>
                loop(tail, pending, held, active, done, stopped, failed, cancelled + n)
              case None => loop(tail, pending, held, active, done, stopped, failed, cancelled)
      loop(rows, 0, 0, 0, 0, 0, 0, 0)

  final case class ManagerEvent(
      id: Long,
      managerId: String,
      itemId: Option[Long] = None,
      runId: Option[String] = None,
      at: String = "",
      kind: String = "",
      text: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerEvent:
    given Schema[ManagerEvent] = summon[KindlingsSchema[ManagerEvent]].schema

  final case class ManagerInfo(
      id: String,
      kind: String,
      goal: Option[String] = None,
      state: String = State.Active.wire,
      pauseRuns: Boolean = false,
      config: ManagerConfig = ManagerConfig(),
      effective: ManagerConfig = ManagerConfig(),
      statusNote: Option[String] = None,
      cron: Option[String] = None,
      nextTickAt: Option[String] = None,
      createdAt: String = "",
      updatedAt: String = "",
      counts: ItemCounts = ItemCounts(),
      inFlight: Int = 0,
      lastEvent: Option[ManagerEvent] = None,
      spentTodayUsd: Double = 0,
      tokensToday: Long = 0,
      backlog: Int = 0,
      backlogDays: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerInfo:
    given Schema[ManagerInfo] = summon[KindlingsSchema[ManagerInfo]].schema

  final case class Hold(
      until: Option[String] = None,
      untilSource: Option[String] = None,
      reason: String = "",
      hint: Option[Long] = None,
      untilApproval: Option[Long] = None,
      focus: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Hold:
    given Schema[Hold] = summon[KindlingsSchema[Hold]].schema

  final case class RunMark(
      run: String,
      before: Option[Int] = None,
      after: Int = 0,
      maxReopened: Int = 0,
      escalated: Int = 0,
      churn: Boolean = false,
      pairsBefore: Option[Int] = None,
      pairsAfter: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RunMark:
    given Schema[RunMark] = summon[KindlingsSchema[RunMark]].schema

  final case class ItemState(
      history: List[RunMark] = Nil,
      before: Option[Int] = None,
      reopened: Int = 0,
      pairsBefore: Option[Int] = None,
      spec: Option[ArticleSpec] = None,
      filterHits: Option[Int] = None,
      oa: Option[OaWork] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ItemState:
    given Schema[ItemState] = summon[KindlingsSchema[ItemState]].schema

  final case class OaWork(
      doi: String,
      title: String = "",
      year: Option[Int] = None,
      authors: List[String] = Nil,
      venue: Option[String] = None,
      goals: List[String] = Nil,
      why: String = "",
      foundBy: Option[String] = None,
      acquireText: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object OaWork:
    given Schema[OaWork] = summon[KindlingsSchema[OaWork]].schema

  final case class ManagerItem(
      id: Long,
      managerId: String,
      kind: String,
      target: String,
      status: String,
      priority: Int = 0,
      attempts: Int = 0,
      failures: Int = 0,
      runId: Option[String] = None,
      evaluatedRun: Option[String] = None,
      notBefore: Option[String] = None,
      hold: Option[Hold] = None,
      state: ItemState = ItemState(),
      outcome: Option[String] = None,
      origin: String = ItemOrigin.Selection.wire,
      createdAt: String = "",
      updatedAt: String = "",
      run: Option[Agents.RunInfo] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerItem:
    given Schema[ManagerItem] = summon[KindlingsSchema[ManagerItem]].schema

  final case class HintEffect(
      action: String,
      targets: List[String] = Nil,
      tag: Option[String] = None,
      topic: Option[String] = None,
      goal: Option[String] = None,
      until: Option[String] = None,
      untilSource: Option[String] = None,
      priority: Option[Int] = None,
      config: Option[ManagerConfig] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object HintEffect:
    given Schema[HintEffect] = summon[KindlingsSchema[HintEffect]].schema
    val Prioritise = "prioritise"
    val HoldAction = "hold"
    val Skip = "skip"
    val Config = "config"

    val Actions = List(Prioritise, HoldAction, Skip)

    val Proposable = Actions :+ Config

  final case class HintProposal(
      managerId: String,
      hint: Long,
      text: String,
      effect: HintEffect,
      by: String = "",
      why: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object HintProposal:
    given Schema[HintProposal] = summon[KindlingsSchema[HintProposal]].schema

  final case class ManagerHint(
      id: Long,
      managerId: String,
      itemId: Option[Long] = None,
      text: String = "",
      effect: Option[HintEffect] = None,
      author: String = "",
      standing: Boolean = true,
      createdAt: String = "",
      retiredAt: Option[String] = None,
      uses: Int = 0,
      proposal: Option[Long] = None,
      proposed: Option[HintEffect] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerHint:
    given Schema[ManagerHint] = summon[KindlingsSchema[ManagerHint]].schema

  final case class ManagerDetail(
      manager: ManagerInfo,
      current: List[ManagerItem] = Nil,
      hints: List[ManagerHint] = Nil,
      events: List[ManagerEvent] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerDetail:
    given Schema[ManagerDetail] = summon[KindlingsSchema[ManagerDetail]].schema

  final case class ItemPage(items: List[ManagerItem], total: Int, offset: Int, limit: Int)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ItemPage:
    given Schema[ItemPage] = summon[KindlingsSchema[ItemPage]].schema

  final case class EventPage(events: List[ManagerEvent], before: Option[Long] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object EventPage:
    given Schema[EventPage] = summon[KindlingsSchema[EventPage]].schema

  final case class ManagerCreate(
      kind: String,
      goal: Option[String] = None,
      config: ManagerConfig = ManagerConfig(),
      paused: Option[Boolean] = None,
      cron: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerCreate:
    given Schema[ManagerCreate] = summon[KindlingsSchema[ManagerCreate]].schema

  final case class ManagerUpdate(config: Option[ManagerConfig] = None, cron: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerUpdate:
    given Schema[ManagerUpdate] = summon[KindlingsSchema[ManagerUpdate]].schema

  final case class EnqueueRequest(kind: String, target: String, priority: Option[Int] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object EnqueueRequest:
    given Schema[EnqueueRequest] = summon[KindlingsSchema[EnqueueRequest]].schema

  final case class EnqueueResult(managerId: String, items: List[ManagerItem] = Nil, skipped: List[String] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object EnqueueResult:
    given Schema[EnqueueResult] = summon[KindlingsSchema[EnqueueResult]].schema

  final case class ItemPatch(
      action: String,
      priority: Option[Int] = None,
      until: Option[String] = None,
      untilSource: Option[String] = None,
      reason: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ItemPatch:
    given Schema[ItemPatch] = summon[KindlingsSchema[ItemPatch]].schema
    val Actions = List("hold", "release", "priority", "skip", "reopen")

  final case class HintAdd(
      text: String,
      item: Option[String] = None,
      once: Option[Boolean] = None,
      effect: Option[HintEffect] = None,
      author: Option[String] = None,
      propose: Option[Boolean] = None,
      suggest: Option[Boolean] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object HintAdd:
    given Schema[HintAdd] = summon[KindlingsSchema[HintAdd]].schema

  val Actions: List[String] = List("pause", "resume", "stop")
