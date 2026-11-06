package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class TimeConfig(min: com.histmersion.api.catalogue.Year, max: com.histmersion.api.catalogue.Year, defaultFrom: com.histmersion.api.catalogue.Year, defaultTo: com.histmersion.api.catalogue.Year)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
