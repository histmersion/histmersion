package com.histmersion
package api.automation

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}
import com.histmersion.util.problem.Text.sentence

import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Source

object Managers:
  /** Why a manager proposes, holds or changes an item: the narration of a decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  object Usd:
    type Amount = Amount.T
    object Amount extends Newtype[Double, Pure]
  object Scheduler:
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]

  type Actions = ActionsValue
  type ActionsValue = ActionsValue.T
  object ActionsValue extends Newtype[String, Pure]

  export Manager.Kind as Kind
  export Manager.State as State
  export com.histmersion.api.automation.ItemStatus

  enum ItemOrigin(val wire: ItemOrigin.Wire) derives CanEqual:
    case Selection extends ItemOrigin(ItemOrigin.Wire("selection"))
    case Person extends ItemOrigin(ItemOrigin.Wire("person"))
    case Chain extends ItemOrigin(ItemOrigin.Wire("chain"))
    case Hint extends ItemOrigin(ItemOrigin.Wire("hint"))

    /** An item an ingest added, by a name no other type takes. */
    case FromIngest extends ItemOrigin(ItemOrigin.Wire("ingest"))

    case Cron extends ItemOrigin(ItemOrigin.Wire("cron"))

  object ItemOrigin:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[ItemOrigin] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

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

  def itemKinds(k: Kind): List[ManagerItem.Kind] = k match
    case Kind.Review =>
      List(ManagerItem.Kind(ArticleReview), ManagerItem.Kind(InferFilter), ManagerItem.Kind(ArticleExtend))
    case Kind.Enrich => List(ManagerItem.Kind(SourceEnrich))
    case Kind.Seed   =>
      List(ManagerItem.Kind(ProposeArticles), ManagerItem.Kind(ArticleCompile), ManagerItem.Kind(PlanGoalItem))
    case Kind.Watch =>
      List(ManagerItem.Kind(AcquisitionSweep), ManagerItem.Kind(CurrencyCheck), ManagerItem.Kind(OaIngest))

  def itemKindOf(k: Kind, word: com.histmersion.api.ItemKind.Word): Option[ManagerItem.Kind] =
    val w = (word).value.trim.toLowerCase
    val kinds = itemKinds(k)
    if w.isEmpty then (kinds.headOption).map(refactorValue => ManagerItem.Kind((refactorValue).value))
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
      (Option(alias).filter(kinds.contains)).map(refactorValue2 => ManagerItem.Kind(refactorValue2))

  val ReviewId = "review"

  val EnrichId = "enrich"

  def seedId(goal: Goal.Ref): ManagerInfo.Id = ManagerInfo.Id(s"seed:${(goal).value.trim}")

  def watchId(goal: Goal.Ref): ManagerInfo.Id = ManagerInfo.Id(s"watch:${(goal).value.trim}")

  enum Sweep(val wire: Sweep.Wire, val itemKind: ManagerItem.Kind) derives CanEqual:
    case Acquisition extends Sweep(Sweep.Wire("acquisition"), ManagerItem.Kind(AcquisitionSweep))
    case Currency extends Sweep(Sweep.Wire("currency"), ManagerItem.Kind(CurrencyCheck))

  object Sweep:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Sweep] =
      values.find(x =>
        x.wire == Wire((s).value.trim.toLowerCase) || x.itemKind == ManagerItem.Kind((s).value.trim.toLowerCase)
      )
    def ofItem(kind: ManagerItem.Kind): Option[Sweep] = values.find(_.itemKind == kind)

    def of(c: ManagerConfig): List[Sweep] =
      c.sweeps.fold(values.toList)(ws => values.toList.filter(s => ws.exists(w => parse(w).contains(s))))

  enum EnqueueKind(val wire: EnqueueKind.Wire) derives CanEqual:
    case Review extends EnqueueKind(EnqueueKind.Wire("review"))
    case Extend extends EnqueueKind(EnqueueKind.Wire("extend"))
    case Enrich extends EnqueueKind(EnqueueKind.Wire("enrich"))
    case Seed extends EnqueueKind(EnqueueKind.Wire("seed"))

  object EnqueueKind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[EnqueueKind] = (s).value.trim.toLowerCase match
      case "" | "article" | ArticleReview       => Some(Review)
      case ArticleExtend                        => Some(Extend)
      case SourceEnrich | "source"              => Some(Enrich)
      case ProposeArticles | "propose" | "plan" => Some(Seed)
      case other                                => values.find(_.wire == Wire(other))

  final case class ArticleSpec(
      title: Article.Title,
      kind: Article.Kind.Wire,
      filter: Article.Filter,
      why: Rationale = Rationale("")
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

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
      sweeps: Option[List[Sweep.Wire]] = None,
      autoIngest: Option[ManagerConfig.AutoIngest] = None,
      autoIngestPerDay: Option[ManagerConfig.AutoIngestPerDay] = None,
      dailyUsd: Option[ManagerConfig.DailyUsd] = None,
      autoApprove: Option[ManagerConfig.AutoApprove] = None,
      extendMinNew: Option[ManagerConfig.ExtendMinNew] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
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
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]

    type MaxRuns = MaxRuns.T
    object MaxRuns extends Newtype[Int, Pure]
    type MaxOpen = MaxOpen.T
    object MaxOpen extends Newtype[Int, Pure]
    type Rounds = Rounds.T
    object Rounds extends Newtype[Int, Pure]
    type MaxRunsPerArticle = MaxRunsPerArticle.T
    object MaxRunsPerArticle extends Newtype[Int, Pure]
    type NoProgressRuns = NoProgressRuns.T
    object NoProgressRuns extends Newtype[Int, Pure]
    type ReopenLimit = ReopenLimit.T
    object ReopenLimit extends Newtype[Int, Pure]
    type CooldownMinutes = CooldownMinutes.T
    object CooldownMinutes extends Newtype[Int, Pure]
    type LowWater = LowWater.T
    object LowWater extends Newtype[Int, Pure]
    type RefillBatch = RefillBatch.T
    object RefillBatch extends Newtype[Int, Pure]
    type MaxFailures = MaxFailures.T
    object MaxFailures extends Newtype[Int, Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends Newtype[Double, Pure]
    type IncludeTags = IncludeTags.T
    object IncludeTags extends Newtype[String, Pure]
    type IncludeGoal = IncludeGoal.T
    object IncludeGoal extends Newtype[String, Pure]
    type StaleAfterDays = StaleAfterDays.T
    object StaleAfterDays extends Newtype[Int, Pure]
    type InferFilter = InferFilter.T
    object InferFilter extends Newtype[Boolean, Pure]
    type MinSources = MinSources.T
    object MinSources extends Newtype[Int, Pure]
    type MaxArticles = MaxArticles.T
    object MaxArticles extends Newtype[Int, Pure]
    type AutoIngest = AutoIngest.T
    object AutoIngest extends Newtype[Boolean, Pure]
    type AutoIngestPerDay = AutoIngestPerDay.T
    object AutoIngestPerDay extends Newtype[Int, Pure]
    type DailyUsd = DailyUsd.T
    object DailyUsd extends Newtype[Double, Pure]
    type AutoApprove = AutoApprove.T
    object AutoApprove extends Newtype[Boolean, Pure]
    type ExtendMinNew = ExtendMinNew.T
    object ExtendMinNew extends Newtype[Int, Pure]

    def parse(kvs: List[Spec]): Either[Problems, ManagerConfig] =
      kvs.foldLeft[Either[Problems, ManagerConfig]](Right(ManagerConfig())) { (acc, kv) =>
        acc.flatMap { c =>
          (kv).value.split("=", 2) match
            case Array(k0, v0) =>
              val (k, v) = (k0.trim, v0.trim)
              val at = Vector(Field(s"$k"))
              def int = v.toIntOption
                .filter(_ >= 0)
                .toRight(Invalid.OutOfRange(TypeName.named[Int], Raw(s"$v"), Invalid.Bounds(Some(Raw("0")), None), at))
              k match
                case "maxRuns" | "max-runs"                       => int.map(n => c.copy(maxRuns = Some(MaxRuns(n))))
                case "maxOpen" | "max-open"                       => int.map(n => c.copy(maxOpen = Some(MaxOpen(n))))
                case "rounds"                                     => int.map(n => c.copy(rounds = Some(Rounds(n))))
                case "maxRunsPerArticle" | "max-runs-per-article" =>
                  int.map(n => c.copy(maxRunsPerArticle = Some(MaxRunsPerArticle(n))))
                case "noProgressRuns" | "no-progress-runs" =>
                  int.map(n => c.copy(noProgressRuns = Some(NoProgressRuns(n))))
                case "reopenLimit" | "reopen-limit"         => int.map(n => c.copy(reopenLimit = Some(ReopenLimit(n))))
                case "cooldownMinutes" | "cooldown-minutes" =>
                  int.map(n => c.copy(cooldownMinutes = Some(CooldownMinutes(n))))
                case "lowWater" | "low-water"              => int.map(n => c.copy(lowWater = Some(LowWater(n))))
                case "refillBatch" | "refill-batch"        => int.map(n => c.copy(refillBatch = Some(RefillBatch(n))))
                case "maxFailures" | "max-failures"        => int.map(n => c.copy(maxFailures = Some(MaxFailures(n))))
                case "staleAfterDays" | "stale-after-days" =>
                  int.map(n => c.copy(staleAfterDays = Some(StaleAfterDays(n))))
                case "minSources" | "min-sources"   => int.map(n => c.copy(minSources = Some(MinSources(n))))
                case "maxArticles" | "max-articles" => int.map(n => c.copy(maxArticles = Some(MaxArticles(n))))
                case "autoIngestPerDay" | "auto-ingest-per-day" =>
                  int.map(n => c.copy(autoIngestPerDay = Some(AutoIngestPerDay(n))))
                case "inferFilter" | "infer-filter" => (
                  bool(k, (v)).map(b => c.copy(inferFilter = Some(InferFilter((b)))))
                )
                case "autoIngest" | "auto-ingest" => (bool(k, (v)).map(b => c.copy(autoIngest = Some(AutoIngest((b))))))
                case "autoApprove" | "auto-approve" => (
                  bool(k, (v)).map(b => c.copy(autoApprove = Some(AutoApprove((b)))))
                )
                case "extendMinNew" | "extend-min-new" => int.map(n => c.copy(extendMinNew = Some(ExtendMinNew(n))))
                case "sweeps"                          =>
                  val ws = v.split(",").toList.map(_.trim).filter(_.nonEmpty)
                  ws.find(w => Sweep.parse(Sweep.Wire(w)).isEmpty) match
                    case Some(w) =>
                      Left(
                        Invalid.Unknown(
                          TypeName.named[Sweep],
                          Raw(s"$w"),
                          Sweep.values.toVector.map(x => Raw(s"${x.wire}")),
                          at
                        )
                      )
                    case None =>
                      Right(
                        c.copy(sweeps =
                          Some(
                            ws.flatMap {
                              val refactorFunction = Sweep.parse;
                              (refactorValue4) => refactorFunction(Sweep.Wire(refactorValue4))
                            }.distinct
                              .map(_.wire)
                          )
                        )
                      )
                case "dailyUsd" | "daily-usd" =>
                  v.toDoubleOption
                    .filter(_ >= 0)
                    .toRight(
                      Invalid.OutOfRange(TypeName.of(DailyUsd), Raw(s"$v"), Invalid.Bounds(Some(Raw("0")), None), at)
                    )
                    .map(d => c.copy(dailyUsd = Some(DailyUsd(d))))
                case "budgetUsd" | "budget-usd" =>
                  v.toDoubleOption
                    .filter(_ > 0)
                    .toRight(
                      Invalid.OutOfRange(TypeName.of(BudgetUsd), Raw(s"$v"), Invalid.Bounds(Some(Raw("0")), None), at)
                    )
                    .map(d => c.copy(budgetUsd = Some(BudgetUsd(d))))
                case "includeTags" | "include-tags" | "tags" =>
                  Right(
                    c.copy(includeTags =
                      Some(
                        (v.split(",")
                          .toList
                          .map(_.trim)
                          .filter(_.nonEmpty))
                          .map(refactorValue3 => IncludeTags(refactorValue3))
                      )
                    )
                  )
                case "includeGoal" | "include-goal" | "goal" =>
                  Right(
                    c.copy(includeGoal =
                      (Option(v).filter(_.nonEmpty)).map(refactorValue8 => IncludeGoal(refactorValue8))
                    )
                  )
                case "models" =>
                  v.split(";")
                    .toList
                    .map(_.trim)
                    .filter(_.nonEmpty)
                    .map {
                      val refactorFunction2 = Agents.ModelOverride.parse;
                      (refactorValue5) => refactorFunction2(Agents.ModelOverride.Wire(refactorValue5))
                    }
                    .partitionMap(identity) match
                    case (Nil, ms) => Right(c.copy(models = Some(ms)))
                    case (errs, _) => Left(Problems.from(errs.toVector).getOrElse(errs.head).at(Field(s"$k")))
                case other => Left(Invalid.Unknown.here(TypeName.named[ManagerConfig], Raw(s"$other"), Vector.empty))
            case _ => Left(Invalid.Unreadable.here(TypeName.of(Spec), Raw(s"$kv"), Vector(Raw("max-runs=2"))))
        }
      }

    private def bool(k: String, v: String): Either[Problem, Boolean] = (v).toLowerCase match
      case "true" | "on" | "yes"  => Right((true))
      case "false" | "off" | "no" => Right((false))
      case _                      =>
        Left(
          Invalid.Unknown(TypeName.named[Boolean], Raw(s"$v"), Vector(Raw("true"), Raw("false")), Vector(Field(s"$k")))
        )

  final case class ItemCounts(
      pending: ItemCounts.Pending = ItemCounts.Pending(0),
      held: ItemCounts.Held = ItemCounts.Held(0),
      active: ItemCounts.Active = ItemCounts.Active(0),
      done: ItemCounts.Done = ItemCounts.Done(0),
      stopped: ItemCounts.Stopped = ItemCounts.Stopped(0),
      failed: ItemCounts.Failed = ItemCounts.Failed(0),
      cancelled: ItemCounts.Cancelled = ItemCounts.Cancelled(0)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def open: ItemCounts.Open = ItemCounts.Open((pending).value + (held).value + (active).value)
  object ItemCounts:
    type Count = Count.T
    object Count extends Newtype[Int, Pure]
    type Open = Open.T
    object Open extends Newtype[Int, Pure]

    type Pending = Pending.T
    object Pending extends Newtype[Int, Pure]
    type Held = Held.T
    object Held extends Newtype[Int, Pure]
    type Active = Active.T
    object Active extends Newtype[Int, Pure]
    type Done = Done.T
    object Done extends Newtype[Int, Pure]
    type Stopped = Stopped.T
    object Stopped extends Newtype[Int, Pure]
    type Failed = Failed.T
    object Failed extends Newtype[Int, Pure]
    type Cancelled = Cancelled.T
    object Cancelled extends Newtype[Int, Pure]

    def of(rows: List[(com.histmersion.api.automation.ItemStatus.Wire, Count)]): ItemCounts =
      @tailrec def loop(
          rest: List[(com.histmersion.api.automation.ItemStatus.Wire, Count)],
          pending: Pending,
          held: Held,
          active: Active,
          done: Done,
          stopped: Stopped,
          failed: Failed,
          cancelled: Cancelled
      ): ItemCounts =
        rest match
          case Nil            => ItemCounts(pending, held, active, done, stopped, failed, cancelled)
          case (s, n) :: tail =>
            ItemStatus.parse(s) match
              case Some(ItemStatus.Pending) =>
                loop(tail, Pending((pending).value + (n).value), held, active, done, stopped, failed, cancelled)
              case Some(ItemStatus.Held) =>
                loop(tail, pending, Held((held).value + (n).value), active, done, stopped, failed, cancelled)
              case Some(ItemStatus.Active) =>
                loop(tail, pending, held, Active((active).value + (n).value), done, stopped, failed, cancelled)
              case Some(ItemStatus.Done) =>
                loop(tail, pending, held, active, Done((done).value + (n).value), stopped, failed, cancelled)
              case Some(ItemStatus.Stopped) =>
                loop(tail, pending, held, active, done, Stopped((stopped).value + (n).value), failed, cancelled)
              case Some(ItemStatus.Failed) =>
                loop(tail, pending, held, active, done, stopped, Failed((failed).value + (n).value), cancelled)
              case Some(ItemStatus.Cancelled) =>
                loop(tail, pending, held, active, done, stopped, failed, Cancelled((cancelled).value + (n).value))
              case None => loop(tail, pending, held, active, done, stopped, failed, cancelled)
      loop(rows, Pending(0), Held(0), Active(0), Done(0), Stopped(0), Failed(0), Cancelled(0))

  final case class ManagerEvent(
      id: ManagerEvent.Id,
      managerId: ManagerInfo.Id,
      itemId: Option[ManagerEvent.ItemId] = None,
      runId: Option[Agents.RunInfo.Id] = None,
      at: ManagerEvent.At = ManagerEvent.At(""),
      kind: ManagerEvent.Kind = ManagerEvent.Kind(""),
      text: ManagerEvent.Text = ManagerEvent.Text("")
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ManagerEvent:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    type ItemId = ItemId.T
    object ItemId extends Newtype[Long, Pure]
    type At = At.T
    object At extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Text = Text.T
    object Text extends Newtype[String, Pure]

  final case class ManagerInfo(
      id: ManagerInfo.Id,
      kind: ManagerInfo.Kind,
      goal: Option[Goal.Slug] = None,
      state: ManagerInfo.State = Manager.State.Active.wire,
      pauseRuns: ManagerInfo.PauseRuns = ManagerInfo.PauseRuns(false),
      config: ManagerConfig = ManagerConfig(),
      effective: ManagerConfig = ManagerConfig(),
      statusNote: Option[Manager.Status] = None,
      cron: Option[ManagerInfo.Cron] = None,
      nextTickAt: Option[ManagerInfo.NextTickAt] = None,
      createdAt: ManagerInfo.CreatedAt = ManagerInfo.CreatedAt(""),
      updatedAt: ManagerInfo.UpdatedAt = ManagerInfo.UpdatedAt(""),
      counts: ItemCounts = ItemCounts(),
      inFlight: ManagerInfo.InFlight = ManagerInfo.InFlight(0),
      lastEvent: Option[ManagerEvent] = None,
      spentTodayUsd: ManagerInfo.SpentTodayUsd = ManagerInfo.SpentTodayUsd(0.0),
      tokensToday: ManagerInfo.TokensToday = ManagerInfo.TokensToday((0).toLong),
      backlog: ManagerInfo.Backlog = ManagerInfo.Backlog(0),
      backlogDays: Option[ManagerInfo.BacklogDays] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ManagerInfo:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Kind = Manager.Kind.Wire
    val Kind: Manager.Kind.Wire.type = Manager.Kind.Wire
    type State = Manager.State.Wire
    val State: Manager.State.Wire.type = Manager.State.Wire
    type PauseRuns = PauseRuns.T
    object PauseRuns extends Newtype[Boolean, Pure]
    type Cron = Cron.T
    object Cron extends Newtype[String, Pure]
    type NextTickAt = NextTickAt.T
    object NextTickAt extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type InFlight = InFlight.T
    object InFlight extends Newtype[Int, Pure]
    type SpentTodayUsd = SpentTodayUsd.T
    object SpentTodayUsd extends Newtype[Double, Pure]
    type TokensToday = TokensToday.T
    object TokensToday extends Newtype[Long, Pure]
    type Backlog = Backlog.T
    object Backlog extends Newtype[Int, Pure]
    type BacklogDays = BacklogDays.T
    object BacklogDays extends Newtype[Int, Pure]

  final case class Hold(
      until: Option[Hold.Until] = None,
      untilSource: Option[Hold.UntilSource] = None,
      reason: Rationale = Rationale(""),
      hint: Option[Hold.Hint] = None,
      untilApproval: Option[Hold.UntilApproval] = None,
      focus: Option[Hold.Focus] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Hold:
    type Until = Until.T
    object Until extends Newtype[String, Pure]
    type UntilSource = UntilSource.T
    object UntilSource extends Newtype[String, Pure]
    type Hint = Hint.T
    object Hint extends Newtype[Long, Pure]
    type UntilApproval = UntilApproval.T
    object UntilApproval extends Newtype[Long, Pure]
    type Focus = Focus.T
    object Focus extends Newtype[String, Pure]

  final case class RunMark(
      run: Agents.RunInfo.Id,
      before: Option[RunMark.Before] = None,
      after: RunMark.After = RunMark.After(0),
      maxReopened: RunMark.MaxReopened = RunMark.MaxReopened(0),
      escalated: RunMark.Escalated = RunMark.Escalated(0),
      churn: RunMark.Churn = RunMark.Churn(false),
      pairsBefore: Option[RunMark.PairsBefore] = None,
      pairsAfter: Option[RunMark.PairsAfter] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object RunMark:
    type Before = Before.T
    object Before extends Newtype[Int, Pure]
    type After = After.T
    object After extends Newtype[Int, Pure]
    type MaxReopened = MaxReopened.T
    object MaxReopened extends Newtype[Int, Pure]
    type Escalated = Escalated.T
    object Escalated extends Newtype[Int, Pure]
    type Churn = Churn.T
    object Churn extends Newtype[Boolean, Pure]
    type PairsBefore = PairsBefore.T
    object PairsBefore extends Newtype[Int, Pure]
    type PairsAfter = PairsAfter.T
    object PairsAfter extends Newtype[Int, Pure]

  final case class ItemState(
      history: List[RunMark] = Nil,
      before: Option[ItemState.Before] = None,
      reopened: ItemState.Reopened = ItemState.Reopened(0),
      pairsBefore: Option[ItemState.PairsBefore] = None,
      spec: Option[ArticleSpec] = None,
      filterHits: Option[ItemState.FilterHits] = None,
      oa: Option[OaWork] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ItemState:
    type Before = Before.T
    object Before extends Newtype[Int, Pure]
    type Reopened = Reopened.T
    object Reopened extends Newtype[Int, Pure]
    type PairsBefore = PairsBefore.T
    object PairsBefore extends Newtype[Int, Pure]
    type FilterHits = FilterHits.T
    object FilterHits extends Newtype[Int, Pure]

  final case class OaWork(
      doi: Doi,
      title: Title = Title(""),
      year: Option[Year] = None,
      authors: List[AuthorName] = Nil,
      venue: Option[Source.Venue] = None,
      goals: List[Goal.Slug] = Nil,
      why: Rationale = Rationale(""),
      foundBy: Option[OaWork.FoundBy] = None,
      acquireText: Option[OaWork.AcquireText] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object OaWork:
    type FoundBy = FoundBy.T
    object FoundBy extends Newtype[String, Pure]
    type AcquireText = AcquireText.T
    object AcquireText extends Newtype[String, Pure]

  final case class ManagerItem(
      id: ManagerItem.Id,
      managerId: ManagerInfo.Id,
      kind: ManagerItem.Kind,
      target: ManagerItem.Target,
      status: ManagerItem.Status,
      priority: ManagerItem.Priority = ManagerItem.Priority(0),
      attempts: ManagerItem.Attempts = ManagerItem.Attempts(0),
      failures: ManagerItem.Failures = ManagerItem.Failures(0),
      runId: Option[Agents.RunInfo.Id] = None,
      evaluatedRun: Option[Agents.RunInfo.Id] = None,
      notBefore: Option[ManagerItem.NotBefore] = None,
      hold: Option[Hold] = None,
      state: ItemState = ItemState(),
      outcome: Option[ManagerItem.Outcome] = None,
      origin: ManagerItem.Origin = ItemOrigin.Selection.wire,
      createdAt: ManagerItem.CreatedAt = ManagerItem.CreatedAt(""),
      updatedAt: ManagerItem.UpdatedAt = ManagerItem.UpdatedAt(""),
      run: Option[Agents.RunInfo] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ManagerItem:
    type Ref = Ref.T
    object Ref extends Newtype[String, Pure]
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Status = com.histmersion.api.automation.ItemStatus.Wire
    val Status: com.histmersion.api.automation.ItemStatus.Wire.type = com.histmersion.api.automation.ItemStatus.Wire
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]
    type Attempts = Attempts.T
    object Attempts extends Newtype[Int, Pure]
    type Failures = Failures.T
    object Failures extends Newtype[Int, Pure]
    type NotBefore = NotBefore.T
    object NotBefore extends Newtype[String, Pure]
    type Outcome = Outcome.T
    object Outcome extends Newtype[String, Pure]
    type Origin = ItemOrigin.Wire
    val Origin: ItemOrigin.Wire.type = ItemOrigin.Wire
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]

  final case class HintEffect(
      action: HintEffect.Action,
      targets: List[HintEffect.Targets] = Nil,
      tag: Option[HintEffect.Tag] = None,
      topic: Option[HintEffect.Topic] = None,
      goal: Option[Goal.Slug] = None,
      until: Option[HintEffect.Until] = None,
      untilSource: Option[HintEffect.UntilSource] = None,
      priority: Option[HintEffect.Priority] = None,
      config: Option[ManagerConfig] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object HintEffect:

    type Action = Action.T
    object Action extends Newtype[String, Pure]
    type Targets = Targets.T
    object Targets extends Newtype[String, Pure]
    type Tag = Tag.T
    object Tag extends Newtype[String, Pure]
    type Topic = Topic.T
    object Topic extends Newtype[String, Pure]
    type Until = Until.T
    object Until extends Newtype[String, Pure]
    type UntilSource = UntilSource.T
    object UntilSource extends Newtype[String, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]

    val Prioritise = "prioritise"
    val HoldAction = "hold"
    val Skip = "skip"
    val Config = "config"

    val Actions = List(Prioritise, HoldAction, Skip)

    val Proposable = Actions :+ Config

  final case class HintProposal(
      managerId: ManagerInfo.Id,
      hint: HintProposal.Hint,
      text: HintProposal.Text,
      effect: HintEffect,
      by: HintProposal.By = HintProposal.By(""),
      why: Rationale = Rationale("")
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object HintProposal:
    type Hint = Hint.T
    object Hint extends Newtype[Long, Pure]
    type Text = Text.T
    object Text extends Newtype[String, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]

  final case class ManagerHint(
      id: ManagerHint.Id,
      managerId: ManagerInfo.Id,
      itemId: Option[ManagerHint.ItemId] = None,
      text: ManagerHint.Text = ManagerHint.Text(""),
      effect: Option[HintEffect] = None,
      author: ManagerHint.Author = ManagerHint.Author(""),
      standing: ManagerHint.Standing = ManagerHint.Standing(true),
      createdAt: ManagerHint.CreatedAt = ManagerHint.CreatedAt(""),
      retiredAt: Option[ManagerHint.RetiredAt] = None,
      uses: ManagerHint.Uses = ManagerHint.Uses(0),
      proposal: Option[ManagerHint.Proposal] = None,
      proposed: Option[HintEffect] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ManagerHint:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    type ItemId = ItemId.T
    object ItemId extends Newtype[Long, Pure]
    type Text = Text.T
    object Text extends Newtype[String, Pure]
    type Author = Author.T
    object Author extends Newtype[String, Pure]
    type Standing = Standing.T
    object Standing extends Newtype[Boolean, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type RetiredAt = RetiredAt.T
    object RetiredAt extends Newtype[String, Pure]
    type Uses = Uses.T
    object Uses extends Newtype[Int, Pure]
    type Proposal = Proposal.T
    object Proposal extends Newtype[Long, Pure]

  final case class ManagerDetail(
      manager: ManagerInfo,
      current: List[ManagerItem] = Nil,
      hints: List[ManagerHint] = Nil,
      events: List[ManagerEvent] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class ItemPage(
      items: List[ManagerItem],
      total: ItemPage.Total,
      offset: ItemPage.Offset,
      limit: ItemPage.Limit
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ItemPage:
    type Total = Total.T
    object Total extends Newtype[Int, Pure]
    type Offset = Offset.T
    object Offset extends Newtype[Int, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]

  final case class EventPage(events: List[ManagerEvent], before: Option[EventPage.Before] = None)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object EventPage:
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    type Before = Before.T
    object Before extends Newtype[Long, Pure]

  final case class ManagerCreate(
      kind: ManagerCreate.Kind,
      goal: Option[Goal.Slug] = None,
      config: ManagerConfig = ManagerConfig(),
      paused: Option[ManagerCreate.Paused] = None,
      cron: Option[ManagerCreate.Cron] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ManagerCreate:
    export Manager.Kind.Wire as Kind
    type Paused = Paused.T
    object Paused extends Newtype[Boolean, Pure]
    type Cron = Cron.T
    object Cron extends Newtype[String, Pure]

  final case class ManagerUpdate(config: Option[ManagerConfig] = None, cron: Option[ManagerUpdate.Cron] = None)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ManagerUpdate:
    type Cron = Cron.T
    object Cron extends Newtype[String, Pure]

  final case class EnqueueRequest(
      kind: EnqueueRequest.Kind,
      target: EnqueueRequest.Target,
      priority: Option[EnqueueRequest.Priority] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object EnqueueRequest:
    export EnqueueKind.Wire as Kind
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]

  final case class EnqueueResult(
      managerId: ManagerInfo.Id,
      items: List[ManagerItem] = Nil,
      skipped: List[EnqueueResult.Skipped] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object EnqueueResult:
    type Skipped = Skipped.T
    object Skipped extends Newtype[String, Pure]

  final case class ItemPatch(
      action: ItemPatch.Action,
      priority: Option[ItemPatch.Priority] = None,
      until: Option[ItemPatch.Until] = None,
      untilSource: Option[ItemPatch.UntilSource] = None,
      reason: Option[Rationale] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object ItemPatch:

    type Action = Action.T
    object Action extends Newtype[String, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]
    type Until = Until.T
    object Until extends Newtype[String, Pure]
    type UntilSource = UntilSource.T
    object UntilSource extends Newtype[String, Pure]

    val Actions = List("hold", "release", "priority", "skip", "reopen")

  final case class HintAdd(
      text: HintAdd.Text,
      item: Option[HintAdd.Item] = None,
      once: Option[HintAdd.Once] = None,
      effect: Option[HintEffect] = None,
      author: Option[HintAdd.Author] = None,
      propose: Option[HintAdd.Propose] = None,
      suggest: Option[HintAdd.Suggest] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object HintAdd:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
    type Item = Item.T
    object Item extends Newtype[String, Pure]
    type Once = Once.T
    object Once extends Newtype[Boolean, Pure]
    type Author = Author.T
    object Author extends Newtype[String, Pure]
    type Propose = Propose.T
    object Propose extends Newtype[Boolean, Pure]
    type Suggest = Suggest.T
    object Suggest extends Newtype[Boolean, Pure]

  val Actions: List[Actions] = List(ActionsValue("pause"), ActionsValue("resume"), ActionsValue("stop"))
