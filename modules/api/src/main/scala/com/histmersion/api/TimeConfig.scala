package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TimeConfig(min: Long, max: Long, defaultFrom: Long, defaultTo: Long)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object TimeConfig:
  given Schema[TimeConfig] = summon[KindlingsSchema[TimeConfig]].schema
