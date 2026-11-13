package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobDetail

final case class InitPlan(
    gazetteer: InitPlan.Gazetteer = InitPlan.Gazetteer(false),
    periodPresets: InitPlan.PeriodPresets = InitPlan.PeriodPresets(false),
    fetch: List[InitPlan.Fetch] = Nil,
    check: InitPlan.Check = InitPlan.Check(false)
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def isEmpty: Boolean = (!(gazetteer).value && !(periodPresets).value && fetch.isEmpty)

object InitPlan:

  /** What an init job imports, in its detail. */

  type Gazetteer = Gazetteer.T
  object Gazetteer extends Newtype[Boolean, Pure]
  type PeriodPresets = PeriodPresets.T
  object PeriodPresets extends Newtype[Boolean, Pure]
  type Fetch = Fetch.T
  object Fetch extends Newtype[String, Pure]
  type Check = Check.T
  object Check extends Newtype[Boolean, Pure]

  val Detail: JobDetail.Field[Option[InitPlan]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("init"), None)

  val JobKind = ExchangeJobKinds.Init.kind.value

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
