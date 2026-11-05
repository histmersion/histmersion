package com.histmersion.api.automation
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.research.Article

object Managers:

  type ItemKindsResult = ItemKindsResult.T
  object ItemKindsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemKindOfResult = ItemKindOfResult.T
  object ItemKindOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemKindOfWord = ItemKindOfWord.T
  object ItemKindOfWord extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedIdResult = SeedIdResult.T
  object SeedIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedIdGoal = SeedIdGoal.T
  object SeedIdGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchIdResult = WatchIdResult.T
  object WatchIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchIdGoal = WatchIdGoal.T
  object WatchIdGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Actions = Actions.T
  object Actions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  export com.histmersion.api.automation.Manager.Kind as Kind
  export com.histmersion.api.automation.Manager.State as State
  export com.histmersion.api.automation.ItemStatus

  enum ItemOrigin(val wire: String):
    case Selection extends ItemOrigin("selection")
    case Person extends ItemOrigin("person")
    case Chain extends ItemOrigin("chain")
    case Hint extends ItemOrigin("hint")

    /** An item an ingest added, by a name no other type takes. */
    case FromIngest extends ItemOrigin("ingest")

    case Cron extends ItemOrigin("cron")

  object ItemOrigin:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Managers.ItemOrigin.ParseS): Option[ItemOrigin] = values.find(_.wire == s.trim.toLowerCase)

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

  def itemKinds(k: Kind): List[com.histmersion.api.automation.Managers.ItemKindsResult] = k match
    case Kind.Review => List(ArticleReview, InferFilter, ArticleExtend)
    case Kind.Enrich => List(SourceEnrich)
    case Kind.Seed   => List(ProposeArticles, ArticleCompile, PlanGoalItem)
    case Kind.Watch  => List(AcquisitionSweep, CurrencyCheck, OaIngest)

  def itemKindOf(k: Kind, word: com.histmersion.api.automation.Managers.ItemKindOfWord): Option[com.histmersion.api.automation.Managers.ItemKindOfResult] =
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

  def seedId(goal: com.histmersion.api.automation.Managers.SeedIdGoal): com.histmersion.api.automation.Managers.SeedIdResult = s"seed:${goal.trim}"

  def watchId(goal: com.histmersion.api.automation.Managers.WatchIdGoal): com.histmersion.api.automation.Managers.WatchIdResult = s"watch:${goal.trim}"

  enum Sweep(val wire: String, val itemKind: String):
    case Acquisition extends Sweep("acquisition", AcquisitionSweep)
    case Currency extends Sweep("currency", CurrencyCheck)

  object Sweep:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfItemKind = OfItemKind.T
    object OfItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Managers.Sweep.ParseS): Option[Sweep] =
      values.find(x => x.wire == s.trim.toLowerCase || x.itemKind == s.trim.toLowerCase)
    def ofItem(kind: com.histmersion.api.automation.Managers.Sweep.OfItemKind): Option[Sweep] = values.find(_.itemKind == kind)

    def of(c: ManagerConfig): List[Sweep] =
      c.sweeps.fold(values.toList)(ws => values.toList.filter(s => ws.exists(w => parse(w).contains(s))))

  enum EnqueueKind(val wire: String):
    case Review extends EnqueueKind("review")
    case Extend extends EnqueueKind("extend")
    case Enrich extends EnqueueKind("enrich")
    case Seed extends EnqueueKind("seed")

  object EnqueueKind:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Managers.EnqueueKind.ParseS): Option[EnqueueKind] = s.trim.toLowerCase match
      case "" | "article" | ArticleReview       => Some(Review)
      case ArticleExtend                        => Some(Extend)
      case SourceEnrich | "source"              => Some(Enrich)
      case ProposeArticles | "propose" | "plan" => Some(Seed)
      case other                                => values.find(_.wire == other)

  final case class ArticleSpec(title: com.histmersion.api.research.Article.Title, kind: com.histmersion.api.research.Article.KindValue, filter: Article.Filter, why: ArticleSpec.Why = "")
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ArticleSpec:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerConfig(
      maxRuns: Option[ManagerConfig.MaxRuns] = None,
      maxOpen: Option[ManagerConfig.MaxOpen] = None,
      rounds: Option[ManagerConfig.Rounds] = None,
      maxRunsPerArticle: Option[ManagerConfig.MaxRunsPerArticle] = None,
      noProgressRuns: Option[ManagerConfig.NoProgressRuns] = None,
      reopenLimit: Option[ManagerConfig.ReopenLimit] = None,
      cooldownMinutes: Option[ManagerConfig.CooldownMinutes] = None,
      lowWater: Option[ManagerConfig.LowWater] = None,
      refillBatch: Option[ManagerConfig.RefillBatch] = None,
      maxFailures: Option[ManagerConfig.MaxFailures] = None,
      budgetUsd: Option[ManagerConfig.BudgetUsd] = None,
      includeTags: Option[List[ManagerConfig.IncludeTags]] = None,
      includeGoal: Option[ManagerConfig.IncludeGoal] = None,
      models: Option[List[Agents.ModelOverride]] = None,
      staleAfterDays: Option[ManagerConfig.StaleAfterDays] = None,
      inferFilter: Option[ManagerConfig.InferFilter] = None,
      minSources: Option[ManagerConfig.MinSources] = None,
      maxArticles: Option[ManagerConfig.MaxArticles] = None,
      sweeps: Option[List[ManagerConfig.Sweeps]] = None,
      autoIngest: Option[ManagerConfig.AutoIngest] = None,
      autoIngestPerDay: Option[ManagerConfig.AutoIngestPerDay] = None,
      dailyUsd: Option[ManagerConfig.DailyUsd] = None,
      autoApprove: Option[ManagerConfig.AutoApprove] = None,
      extendMinNew: Option[ManagerConfig.ExtendMinNew] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
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


    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseKvs = ParseKvs.T
    object ParseKvs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BoolResultPart1 = BoolResultPart1.T
    object BoolResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BoolResultPart2 = BoolResultPart2.T
    object BoolResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BoolK = BoolK.T
    object BoolK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BoolV = BoolV.T
    object BoolV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type MaxRuns = MaxRuns.T
    object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxOpen = MaxOpen.T
    object MaxOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rounds = Rounds.T
    object Rounds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxRunsPerArticle = MaxRunsPerArticle.T
    object MaxRunsPerArticle extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoProgressRuns = NoProgressRuns.T
    object NoProgressRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReopenLimit = ReopenLimit.T
    object ReopenLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CooldownMinutes = CooldownMinutes.T
    object CooldownMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LowWater = LowWater.T
    object LowWater extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefillBatch = RefillBatch.T
    object RefillBatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxFailures = MaxFailures.T
    object MaxFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IncludeTags = IncludeTags.T
    object IncludeTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IncludeGoal = IncludeGoal.T
    object IncludeGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StaleAfterDays = StaleAfterDays.T
    object StaleAfterDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InferFilter = InferFilter.T
    object InferFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinSources = MinSources.T
    object MinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxArticles = MaxArticles.T
    object MaxArticles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sweeps = Sweeps.T
    object Sweeps extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AutoIngest = AutoIngest.T
    object AutoIngest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AutoIngestPerDay = AutoIngestPerDay.T
    object AutoIngestPerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DailyUsd = DailyUsd.T
    object DailyUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AutoApprove = AutoApprove.T
    object AutoApprove extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExtendMinNew = ExtendMinNew.T
    object ExtendMinNew extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(kvs: List[com.histmersion.api.automation.Managers.ManagerConfig.ParseKvs]): Either[com.histmersion.api.automation.Managers.ManagerConfig.ParseResultPart1, ManagerConfig] =
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

    private def bool(k: com.histmersion.api.automation.Managers.ManagerConfig.BoolK, v: com.histmersion.api.automation.Managers.ManagerConfig.BoolV): Either[com.histmersion.api.automation.Managers.ManagerConfig.BoolResultPart1, com.histmersion.api.automation.Managers.ManagerConfig.BoolResultPart2] = v.toLowerCase match
      case "true" | "on" | "yes"  => Right(true)
      case "false" | "off" | "no" => Right(false)
      case _                      => Left(s"$k: '$v' is not true or false")

  final case class ItemCounts(
      pending: ItemCounts.Pending = 0,
      held: ItemCounts.Held = 0,
      active: ItemCounts.Active = 0,
      done: ItemCounts.Done = 0,
      stopped: ItemCounts.Stopped = 0,
      failed: ItemCounts.Failed = 0,
      cancelled: ItemCounts.Cancelled = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def open: com.histmersion.api.automation.Managers.ItemCounts.OpenResult = pending + held + active
  object ItemCounts:


    type OpenResult = OpenResult.T
    object OpenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfRowsPart1 = OfRowsPart1.T
    object OfRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfRowsPart2 = OfRowsPart2.T
    object OfRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Pending = Pending.T
    object Pending extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Held = Held.T
    object Held extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Active = Active.T
    object Active extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Done = Done.T
    object Done extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Stopped = Stopped.T
    object Stopped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failed = Failed.T
    object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cancelled = Cancelled.T
    object Cancelled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(rows: List[(com.histmersion.api.automation.Managers.ItemCounts.OfRowsPart1, com.histmersion.api.automation.Managers.ItemCounts.OfRowsPart2)]): ItemCounts =
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
      id: ManagerEvent.Id,
      managerId: com.histmersion.api.automation.Managers.ManagerInfo.Id,
      itemId: Option[ManagerEvent.ItemId] = None,
      runId: Option[com.histmersion.api.automation.Agents.RunInfo.Id] = None,
      at: ManagerEvent.At = "",
      kind: ManagerEvent.Kind = "",
      text: ManagerEvent.Text = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerEvent:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemId = ItemId.T
    object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerInfo(
      id: ManagerInfo.Id,
      kind: ManagerInfo.Kind,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      state: ManagerInfo.State = State.Active.wire,
      pauseRuns: ManagerInfo.PauseRuns = false,
      config: ManagerConfig = ManagerConfig(),
      effective: ManagerConfig = ManagerConfig(),
      statusNote: Option[ManagerInfo.StatusNote] = None,
      cron: Option[ManagerInfo.Cron] = None,
      nextTickAt: Option[ManagerInfo.NextTickAt] = None,
      createdAt: ManagerInfo.CreatedAt = "",
      updatedAt: ManagerInfo.UpdatedAt = "",
      counts: ItemCounts = ItemCounts(),
      inFlight: ManagerInfo.InFlight = 0,
      lastEvent: Option[ManagerEvent] = None,
      spentTodayUsd: ManagerInfo.SpentTodayUsd = 0,
      tokensToday: ManagerInfo.TokensToday = 0,
      backlog: ManagerInfo.Backlog = 0,
      backlogDays: Option[ManagerInfo.BacklogDays] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerInfo:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PauseRuns = PauseRuns.T
    object PauseRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusNote = StatusNote.T
    object StatusNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cron = Cron.T
    object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NextTickAt = NextTickAt.T
    object NextTickAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InFlight = InFlight.T
    object InFlight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpentTodayUsd = SpentTodayUsd.T
    object SpentTodayUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TokensToday = TokensToday.T
    object TokensToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Backlog = Backlog.T
    object Backlog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogDays = BacklogDays.T
    object BacklogDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Hold(
      until: Option[Hold.Until] = None,
      untilSource: Option[Hold.UntilSource] = None,
      reason: Hold.Reason = "",
      hint: Option[Hold.Hint] = None,
      untilApproval: Option[Hold.UntilApproval] = None,
      focus: Option[Hold.Focus] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Hold:
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UntilSource = UntilSource.T
    object UntilSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Hint = Hint.T
    object Hint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UntilApproval = UntilApproval.T
    object UntilApproval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Focus = Focus.T
    object Focus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RunMark(
      run: com.histmersion.api.automation.Agents.RunInfo.Id,
      before: Option[RunMark.Before] = None,
      after: RunMark.After = 0,
      maxReopened: RunMark.MaxReopened = 0,
      escalated: RunMark.Escalated = 0,
      churn: RunMark.Churn = false,
      pairsBefore: Option[RunMark.PairsBefore] = None,
      pairsAfter: Option[RunMark.PairsAfter] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RunMark:
    type Before = Before.T
    object Before extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type After = After.T
    object After extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxReopened = MaxReopened.T
    object MaxReopened extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Escalated = Escalated.T
    object Escalated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Churn = Churn.T
    object Churn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PairsBefore = PairsBefore.T
    object PairsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PairsAfter = PairsAfter.T
    object PairsAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ItemState(
      history: List[RunMark] = Nil,
      before: Option[ItemState.Before] = None,
      reopened: ItemState.Reopened = 0,
      pairsBefore: Option[ItemState.PairsBefore] = None,
      spec: Option[ArticleSpec] = None,
      filterHits: Option[ItemState.FilterHits] = None,
      oa: Option[OaWork] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ItemState:
    type Before = Before.T
    object Before extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reopened = Reopened.T
    object Reopened extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PairsBefore = PairsBefore.T
    object PairsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilterHits = FilterHits.T
    object FilterHits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class OaWork(
      doi: com.histmersion.api.library.Doi,
      title: com.histmersion.api.library.Title = "",
      year: Option[com.histmersion.api.catalogue.Year] = None,
      authors: List[com.histmersion.api.library.AuthorName] = Nil,
      venue: Option[com.histmersion.api.library.Venue] = None,
      goals: List[com.histmersion.api.automation.Goal.Slug] = Nil,
      why: OaWork.Why = "",
      foundBy: Option[OaWork.FoundBy] = None,
      acquireText: Option[OaWork.AcquireText] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object OaWork:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FoundBy = FoundBy.T
    object FoundBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AcquireText = AcquireText.T
    object AcquireText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerItem(
      id: ManagerItem.Id,
      managerId: com.histmersion.api.automation.Managers.ManagerInfo.Id,
      kind: ManagerItem.Kind,
      target: ManagerItem.Target,
      status: ManagerItem.Status,
      priority: ManagerItem.Priority = 0,
      attempts: ManagerItem.Attempts = 0,
      failures: ManagerItem.Failures = 0,
      runId: Option[com.histmersion.api.automation.Agents.RunInfo.Id] = None,
      evaluatedRun: Option[com.histmersion.api.automation.Agents.RunInfo.Id] = None,
      notBefore: Option[ManagerItem.NotBefore] = None,
      hold: Option[Hold] = None,
      state: ItemState = ItemState(),
      outcome: Option[ManagerItem.Outcome] = None,
      origin: ManagerItem.Origin = ItemOrigin.Selection.wire,
      createdAt: ManagerItem.CreatedAt = "",
      updatedAt: ManagerItem.UpdatedAt = "",
      run: Option[Agents.RunInfo] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerItem:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempts = Attempts.T
    object Attempts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failures = Failures.T
    object Failures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotBefore = NotBefore.T
    object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Outcome = Outcome.T
    object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Origin = Origin.T
    object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class HintEffect(
      action: HintEffect.Action,
      targets: List[HintEffect.Targets] = Nil,
      tag: Option[HintEffect.Tag] = None,
      topic: Option[HintEffect.Topic] = None,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      until: Option[HintEffect.Until] = None,
      untilSource: Option[HintEffect.UntilSource] = None,
      priority: Option[HintEffect.Priority] = None,
      config: Option[ManagerConfig] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object HintEffect:

    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Targets = Targets.T
    object Targets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tag = Tag.T
    object Tag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topic = Topic.T
    object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UntilSource = UntilSource.T
    object UntilSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Prioritise = "prioritise"
    val HoldAction = "hold"
    val Skip = "skip"
    val Config = "config"

    val Actions = List(Prioritise, HoldAction, Skip)

    val Proposable = Actions :+ Config

  final case class HintProposal(
      managerId: com.histmersion.api.automation.Managers.ManagerInfo.Id,
      hint: HintProposal.Hint,
      text: HintProposal.Text,
      effect: HintEffect,
      by: HintProposal.By = "",
      why: HintProposal.Why = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object HintProposal:
    type Hint = Hint.T
    object Hint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerHint(
      id: ManagerHint.Id,
      managerId: com.histmersion.api.automation.Managers.ManagerInfo.Id,
      itemId: Option[ManagerHint.ItemId] = None,
      text: ManagerHint.Text = "",
      effect: Option[HintEffect] = None,
      author: ManagerHint.Author = "",
      standing: ManagerHint.Standing = true,
      createdAt: ManagerHint.CreatedAt = "",
      retiredAt: Option[ManagerHint.RetiredAt] = None,
      uses: ManagerHint.Uses = 0,
      proposal: Option[ManagerHint.Proposal] = None,
      proposed: Option[HintEffect] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerHint:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemId = ItemId.T
    object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Author = Author.T
    object Author extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Standing = Standing.T
    object Standing extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetiredAt = RetiredAt.T
    object RetiredAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Uses = Uses.T
    object Uses extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Proposal = Proposal.T
    object Proposal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerDetail(
      manager: ManagerInfo,
      current: List[ManagerItem] = Nil,
      hints: List[ManagerHint] = Nil,
      events: List[ManagerEvent] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class ItemPage(items: List[ManagerItem], total: ItemPage.Total, offset: ItemPage.Offset, limit: ItemPage.Limit)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ItemPage:
    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class EventPage(events: List[ManagerEvent], before: Option[EventPage.Before] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object EventPage:
    type Before = Before.T
    object Before extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerCreate(
      kind: ManagerCreate.Kind,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      config: ManagerConfig = ManagerConfig(),
      paused: Option[ManagerCreate.Paused] = None,
      cron: Option[ManagerCreate.Cron] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerCreate:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Paused = Paused.T
    object Paused extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cron = Cron.T
    object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerUpdate(config: Option[ManagerConfig] = None, cron: Option[ManagerUpdate.Cron] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerUpdate:
    type Cron = Cron.T
    object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class EnqueueRequest(kind: EnqueueRequest.Kind, target: EnqueueRequest.Target, priority: Option[EnqueueRequest.Priority] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object EnqueueRequest:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class EnqueueResult(managerId: com.histmersion.api.automation.Managers.ManagerInfo.Id, items: List[ManagerItem] = Nil, skipped: List[EnqueueResult.Skipped] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object EnqueueResult:
    type Skipped = Skipped.T
    object Skipped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ItemPatch(
      action: ItemPatch.Action,
      priority: Option[ItemPatch.Priority] = None,
      until: Option[ItemPatch.Until] = None,
      untilSource: Option[ItemPatch.UntilSource] = None,
      reason: Option[ItemPatch.Reason] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object ItemPatch:

    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UntilSource = UntilSource.T
    object UntilSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Actions = List("hold", "release", "priority", "skip", "reopen")

  final case class HintAdd(
      text: HintAdd.Text,
      item: Option[HintAdd.Item] = None,
      once: Option[HintAdd.Once] = None,
      effect: Option[HintEffect] = None,
      author: Option[HintAdd.Author] = None,
      propose: Option[HintAdd.Propose] = None,
      suggest: Option[HintAdd.Suggest] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object HintAdd:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Once = Once.T
    object Once extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Author = Author.T
    object Author extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Propose = Propose.T
    object Propose extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Suggest = Suggest.T
    object Suggest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val Actions: List[com.histmersion.api.automation.Managers.Actions] = List("pause", "resume", "stop")
