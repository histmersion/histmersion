package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PlaceCounts(sources: Int, findings: Int, articles: Int, events: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceCounts:
  given Schema[PlaceCounts] = summon[KindlingsSchema[PlaceCounts]].schema
