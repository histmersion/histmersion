package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

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

  val JobKind = com.histmersion.api.JobKindsAdapter.Init.value

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
