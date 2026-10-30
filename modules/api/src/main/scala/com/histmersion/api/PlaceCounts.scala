package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** How much of the corpus a place selects: what `place=<id>` narrows each list to — the place AND its descendants in
  * the gazetteer, with no other filter. The same numbers the lists show after the place is chosen as a filter (before
  * any other filter narrows them further).
  */
final case class PlaceCounts(sources: Int, findings: Int, articles: Int, events: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceCounts:
  given Schema[PlaceCounts] = summon[KindlingsSchema[PlaceCounts]].schema
