package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** How big the store is: the corpus, the compiled wiki and the reference data. */
final case class CorpusCounts(
    sources: Int,
    findings: Int,
    articles: Int,
    events: Int,
    /** gazetteer places */
    places: Int,
    /** historical periods (every row of the `periods` table) */
    periods: Int,
    /** of those, the ones the timeline offers as presets (`preset` set, V062) */
    presets: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CorpusCounts:
  given Schema[CorpusCounts] = summon[KindlingsSchema[CorpusCounts]].schema
