package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class TimeConfig(min: Long, max: Long, defaultFrom: Long, defaultTo: Long)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
