package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobDetail
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

final case class InitPlan(
    gazetteer: InitPlan.Gazetteer = InitPlan.Gazetteer.Skipped,
    periodPresets: InitPlan.PeriodPresets = InitPlan.PeriodPresets.Skipped,
    fetch: List[InitPlan.Fetch] = Nil,
    check: InitPlan.Check = InitPlan.Check.NoCheck
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isEmpty: Boolean = (!(gazetteer).included && !(periodPresets).included && fetch.isEmpty)

object InitPlan:

  /** What an init job imports, in its detail. */
  enum Gazetteer derives CanEqual:
    case Included, Skipped
  object Gazetteer extends TwoStates[Gazetteer]:
    extension (gazetteer: Gazetteer) def included: Boolean = gazetteer == Included
  enum PeriodPresets derives CanEqual:
    case Included, Skipped
  object PeriodPresets extends TwoStates[PeriodPresets]:
    extension (periodPresets: PeriodPresets) def included: Boolean = periodPresets == Included
  type Fetch = Fetch.T
  object Fetch extends Newtype[String, Pure]
  enum Check derives CanEqual:
    case Check, NoCheck
  object Check extends TwoStates[Check]:
    extension (check: Check) def check: Boolean = check == Check

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Detail: JobDetail.Field[Option[InitPlan]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("init"), None)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val JobKind = ExchangeJobKinds.Init.kind.value

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe(p: InitPlan): String =
    val bundled = List(
      Option.when(((p.gazetteer) == Gazetteer.Included))("bundled gazetteer"),
      Option.when(((p.periodPresets) == PeriodPresets.Included))("bundled period presets")
    ).flatten
    val fetched =
      Option.when(p.fetch.nonEmpty)(s"${if (p.check).check then "dry run of" else "fetch"} ${p.fetch.mkString(", ")}")
    (bundled ++ fetched) match
      case Nil   => ("nothing to import")
      case parts => (parts.mkString(", "))
