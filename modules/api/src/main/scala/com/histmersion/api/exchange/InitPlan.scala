package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class InitPlan(
    gazetteer: InitPlan.Gazetteer = _root_.com.histmersion.api.exchange.InitPlan.Gazetteer(false),
    periodPresets: InitPlan.PeriodPresets = _root_.com.histmersion.api.exchange.InitPlan.PeriodPresets(false),
    fetch: List[InitPlan.Fetch] = Nil,
    check: InitPlan.Check = _root_.com.histmersion.api.exchange.InitPlan.Check(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def isEmpty: com.histmersion.api.exchange.InitPlan.IsEmptyResult = _root_.com.histmersion.api.exchange.InitPlan.IsEmptyResult(!(gazetteer).value && !(periodPresets).value && fetch.isEmpty)

object InitPlan:


  /** What an init job imports, in its detail. */
  type IsEmptyResult = IsEmptyResult.T
  object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Gazetteer = Gazetteer.T
  object Gazetteer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPresets = PeriodPresets.T
  object PeriodPresets extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fetch = Fetch.T
  object Fetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Check = Check.T
  object Check extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[InitPlan]] =
    com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("init"), None)

  val JobKind = com.histmersion.api.exchange.ExchangeJobKinds.Init.kind.value

  def describe(p: InitPlan): com.histmersion.api.exchange.InitPlan.DescribeResult =
    val bundled = List(
      Option.when(p.gazetteer)("bundled gazetteer"),
      Option.when(p.periodPresets)("bundled period presets")
    ).flatten
    val fetched =
      Option.when(p.fetch.nonEmpty)(s"${if (p.check).value then "dry run of" else "fetch"} ${p.fetch.mkString(", ")}")
    (bundled ++ fetched) match
      case Nil   => _root_.com.histmersion.api.exchange.InitPlan.DescribeResult("nothing to import")
      case parts => parts.mkString(", ")
