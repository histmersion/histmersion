package com.histmersion
package api.instance
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class CorpusCounts(
    sources: CorpusCounts.Sources,
    findings: CorpusCounts.Findings,
    articles: CorpusCounts.Articles,
    events: CorpusCounts.Events,
    places: CorpusCounts.Places,
    periods: CorpusCounts.Periods,
    presets: CorpusCounts.Presets = CorpusCounts.Presets(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CorpusCounts:
  type Sources = Sources.T
  object Sources extends RefinedType[Int, Pure]
  type Findings = Findings.T
  object Findings extends RefinedType[Int, Pure]
  type Articles = Articles.T
  object Articles extends RefinedType[Int, Pure]
  type Events = Events.T
  object Events extends RefinedType[Int, Pure]
  type Places = Places.T
  object Places extends RefinedType[Int, Pure]
  type Periods = Periods.T
  object Periods extends RefinedType[Int, Pure]:
    type Collection = Collection.T
    object Collection extends RefinedType[String, Pure]
    type Name = Name.T
    object Name extends RefinedType[String, Pure]
    type Source = Source.T
    object Source extends RefinedType[String, Pure]
  type Presets = Presets.T
  object Presets extends RefinedType[Int, Pure]
