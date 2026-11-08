package com.histmersion
package api.instance
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
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
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CorpusCounts:
  type Sources = Sources.T
  object Sources extends Newtype[Int, Pure]
  type Findings = Findings.T
  object Findings extends Newtype[Int, Pure]
  type Articles = Articles.T
  object Articles extends Newtype[Int, Pure]
  type Events = Events.T
  object Events extends Newtype[Int, Pure]
  type Places = Places.T
  object Places extends Newtype[Int, Pure]
  type Periods = Periods.T
  object Periods extends Newtype[Int, Pure]:
    type Collection = Collection.T
    object Collection extends Newtype[String, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Source = Source.T
    object Source extends Newtype[String, Pure]
  type Presets = Presets.T
  object Presets extends Newtype[Int, Pure]
