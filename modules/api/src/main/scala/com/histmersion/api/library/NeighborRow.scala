package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class NeighborRow(
    direction: String,
    kind: String,
    citekey: String,
    title: Option[String],
    status: Option[com.histmersion.api.library.Source.Status] = None,
    target: NeighborTarget = NeighborTarget.Source
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
