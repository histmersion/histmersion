package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class Backlink(kind: String, id: String, title: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
