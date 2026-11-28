package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.agents.ModelOverride
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.automation.agents.RunParams

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
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
    models: Option[List[ModelOverride]] = None,
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
      api.json.Schema:
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
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
  enum InferFilter derives CanEqual:
    case Infer, Explicit
  object InferFilter extends TwoStates[InferFilter]:
    extension (inferFilter: InferFilter) def inferFilter: Boolean = inferFilter == Infer
  export RunParams.MinSources
  export RunParams.MaxArticles
  enum AutoIngest derives CanEqual:
    case Auto, Manual
  object AutoIngest extends TwoStates[AutoIngest]:
    extension (autoIngest: AutoIngest) def autoIngest: Boolean = autoIngest == Auto
  type AutoIngestPerDay = AutoIngestPerDay.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object AutoIngestPerDay extends Newtype[Int, Pure]
  type DailyUsd = DailyUsd.T
  object DailyUsd extends Newtype[Double, Pure]
  enum AutoApprove derives CanEqual:
    case Auto, Manual
  object AutoApprove extends TwoStates[AutoApprove]:
    extension (autoApprove: AutoApprove) def autoApprove: Boolean = autoApprove == Auto
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends Newtype[Int, Pure]

  val Keys: Vector[String] = Vector(
    "max-runs",
    "max-open",
    "rounds",
    "max-runs-per-article",
    "no-progress-runs",
    "reopen-limit",
    "cooldown-minutes",
    "low-water",
    "refill-batch",
    "max-failures",
    "stale-after-days",
    "min-sources",
    "max-articles",
    "auto-ingest-per-day",
    "infer-filter",
    "auto-ingest",
    "auto-approve",
    "extend-min-new",
    "sweeps",
    "daily-usd",
    "budget-usd",
    "include-tags",
    "include-goal",
    "models"
  )

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
                bool(k, (v)).map(b =>
                  c.copy(inferFilter = Some((if b then InferFilter.Infer else InferFilter.Explicit)))
                )
              )
              case "autoIngest" | "auto-ingest" => (
                bool(k, (v)).map(b => c.copy(autoIngest = Some((if b then AutoIngest.Auto else AutoIngest.Manual))))
              )
              case "autoApprove" | "auto-approve" => (
                bool(k, (v)).map(b => c.copy(autoApprove = Some((if b then AutoApprove.Auto else AutoApprove.Manual))))
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
                    val refactorFunction2 = ModelOverride.parse;
                    (refactorValue5) => refactorFunction2(ModelOverride.Wire(refactorValue5))
                  }
                  .partitionMap(identity) match
                  case (Nil, ms) => Right(c.copy(models = Some(ms)))
                  case (errs, _) => Left(Problems.from(errs.toVector).getOrElse(errs.head).at(Field(s"$k")))
              case other => Left(Invalid.Undeclared.here(Field(s"$other"), Keys.map(key => Field(s"$key"))))
          case _ => Left(Invalid.Unreadable.here(TypeName.of(Spec), Raw(s"$kv"), Vector(Raw("max-runs=2"))))
      }
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bool(k: String, v: String): Either[Problem, Boolean] = (v).toLowerCase match
    case "true" | "on" | "yes"  => Right((true))
    case "false" | "off" | "no" => Right((false))
    case _                      =>
      Left(
        Invalid.Unknown(TypeName.named[Boolean], Raw(s"$v"), Vector(Raw("true"), Raw("false")), Vector(Field(s"$k")))
      )
