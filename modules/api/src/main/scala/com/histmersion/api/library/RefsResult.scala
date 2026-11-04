package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class RefsResult(
    target: String,
    doi: Option[String],
    backend: String,
    count: Int,
    resolved: Int,
    edgesAdded: Int,
    references: List[RefRow]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
