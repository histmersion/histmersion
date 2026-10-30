package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** One reference edge touching a source, oriented relative to it (out = this cites/relates dst). `citekey` is the OTHER
  * end's id — a citekey for a [[NeighborTarget.Source]], a slug for an [[NeighborTarget.Article]], the raw stored
  * target for an [[NeighborTarget.Unresolved]] one. `target` is decided once, by the query in `SourceRepo.neighbors`.
  */
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
