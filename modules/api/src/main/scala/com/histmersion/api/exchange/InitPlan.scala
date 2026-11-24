package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobDetail
import com.histmersion.lint.{LintRule, tolerated}

final case class InitPlan(
    gazetteer: InitPlan.Gazetteer = InitPlan.Gazetteer(false),
    periodPresets: InitPlan.PeriodPresets = InitPlan.PeriodPresets(false),
    fetch: List[InitPlan.Fetch] = Nil,
    check: InitPlan.Check = InitPlan.Check(false)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isEmpty: Boolean = (!(gazetteer).value && !(periodPresets).value && fetch.isEmpty)

object InitPlan:

  /** What an init job imports, in its detail. */

  type Gazetteer = Gazetteer.T
  object Gazetteer extends Newtype[Boolean, Pure]
  type PeriodPresets = PeriodPresets.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PeriodPresets extends Newtype[Boolean, Pure]
  type Fetch = Fetch.T
  object Fetch extends Newtype[String, Pure]
  type Check = Check.T
  object Check extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Detail: JobDetail.Field[Option[InitPlan]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("init"), None)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val JobKind = ExchangeJobKinds.Init.kind.value

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe(p: InitPlan): String =
    val bundled = List(
      Option.when(((p.gazetteer) == Gazetteer(true)))("bundled gazetteer"),
      Option.when(((p.periodPresets) == PeriodPresets(true)))("bundled period presets")
    ).flatten
    val fetched =
      Option.when(p.fetch.nonEmpty)(s"${if (p.check).value then "dry run of" else "fetch"} ${p.fetch.mkString(", ")}")
    (bundled ++ fetched) match
      case Nil   => ("nothing to import")
      case parts => (parts.mkString(", "))
