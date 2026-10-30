package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class NeighborRow(
    direction: String,
    kind: String,
    citekey: String,
    title: Option[String],
    status: Option[SourceStatus] = None,
    target: NeighborTarget = NeighborTarget.Source
) derives KindlingsCodecAsObject,
      KindlingsSchema
object NeighborRow:
  given Schema[NeighborRow] = summon[KindlingsSchema[NeighborRow]].schema
