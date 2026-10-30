package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What the first-run `init` job (#720) imports, decided when it STARTS and stored on the job, so the jobs list can say
  * what a job is about before it has written a word of progress (#770): the bundled gazetteer, the bundled period
  * presets, and the network sources it fetches (`init.fetch-sources`). `check` makes the fetch steps a DRY RUN — only
  * the gazetteer sources of `histmersion places load` (#1064) have one: they read and join everything, then roll it
  * back and report the counts.
  */
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

  /** The `jobs.kind` of an init job. */
  val JobKind = com.histmersion.api.JobKind.Init.wire

  /** `bundled gazetteer, bundled period presets, fetch periodo and pleiades` — for a person. Pure. */
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
