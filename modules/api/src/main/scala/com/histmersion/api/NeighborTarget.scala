package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** What the far end of a source's edge is. Since V026 `edges` connects stored sources only, so a `Source` target always
  * resolves; an edge parked in `unresolved_edges` names either a compiled article (`Article`, linked into the wiki) or
  * a work not in the corpus yet (`Unresolved`).
  */
enum NeighborTarget:
  case Source, Article, Unresolved

object NeighborTarget:
  given WireEnumValues[NeighborTarget]:
    def all: Array[NeighborTarget] = NeighborTarget.values
    def wire(e: NeighborTarget): String = e.toString.toLowerCase
    def fromWire(s: String): Option[NeighborTarget] =
      NeighborTarget.values.find(_.toString.equalsIgnoreCase(s))

  given Encoder[NeighborTarget] = WireEnum.encoder
  given Decoder[NeighborTarget] = WireEnum.decoder
  given Schema[NeighborTarget] = WireEnum.schema
