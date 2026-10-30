package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class GraphLink(
    source: String,
    target: String,
    kind: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object GraphLink:
  given Schema[GraphLink] = summon[KindlingsSchema[GraphLink]].schema
