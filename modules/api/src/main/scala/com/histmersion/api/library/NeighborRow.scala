package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class NeighborRow(
    direction: NeighborRow.Direction,
    kind: Edge.Kind,
    citekey: Citekey,
    title: Option[Title],
    status: Option[Source.Status] = None,
    target: NeighborTarget = NeighborTarget.Source
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object NeighborRow:
  export ContradictionRow.Direction.Wire as Direction
