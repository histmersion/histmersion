package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventEdge(
    srcId: String,
    dstId: String,
    kind: String,
    citekey: String,
    note: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
