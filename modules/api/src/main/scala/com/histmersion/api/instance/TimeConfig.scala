package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

final case class TimeConfig(min: Year, max: Year, defaultFrom: Year, defaultTo: Year)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
