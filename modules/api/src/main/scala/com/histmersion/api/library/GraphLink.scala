package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class GraphLink(
    source: String,
    target: String,
    kind: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object GraphLink:
  given Schema[GraphLink] = summon[KindlingsSchema[GraphLink]].schema
