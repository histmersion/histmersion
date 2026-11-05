package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class NeighborRow(
    direction: NeighborRow.Direction,
    kind: com.histmersion.api.library.Edge.Kind,
    citekey: com.histmersion.api.library.Citekey,
    title: Option[com.histmersion.api.library.Title],
    status: Option[com.histmersion.api.library.Source.Status] = None,
    target: NeighborTarget = NeighborTarget.Source
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object NeighborRow:
  type Direction = Direction.T
  object Direction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
