package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class InitPlan(
    gazetteer: Boolean = false,
    periodPresets: Boolean = false,
    fetch: List[String] = Nil,
    check: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def isEmpty: Boolean = !gazetteer && !periodPresets && fetch.isEmpty

object InitPlan:
  given Schema[InitPlan] = summon[KindlingsSchema[InitPlan]].schema

  /** What an init job imports, in its detail. */
  val Detail: com.histmersion.api.jobs.JobDetailField[Option[InitPlan]] =
    com.histmersion.api.jobs.JobDetailField("init", None)

  val JobKind = com.histmersion.api.exchange.ExchangeJobKinds.Init.kind.value

  def describe(p: InitPlan): String =
    val bundled = List(
      Option.when(p.gazetteer)("bundled gazetteer"),
      Option.when(p.periodPresets)("bundled period presets")
    ).flatten
    val fetched =
      Option.when(p.fetch.nonEmpty)(s"${if p.check then "dry run of" else "fetch"} ${p.fetch.mkString(", ")}")
    (bundled ++ fetched) match
      case Nil   => "nothing to import"
      case parts => parts.mkString(", ")
