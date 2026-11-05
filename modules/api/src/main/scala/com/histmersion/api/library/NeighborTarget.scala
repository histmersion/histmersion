package com.histmersion.api.library
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.research.Article
import com.histmersion.api.given

enum NeighborTarget:
  case Source, Article, Unresolved

object NeighborTarget:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[NeighborTarget]:
    def all: Array[NeighborTarget] = NeighborTarget.values
    def wire(e: NeighborTarget): com.histmersion.api.library.NeighborTarget.WireResult = e.toString.toLowerCase
    def fromWire(s: com.histmersion.api.library.NeighborTarget.FromWireS): Option[NeighborTarget] =
      NeighborTarget.values.find(_.toString.equalsIgnoreCase(s))

  given Encoder[NeighborTarget] = WireEnum.encoder
  given Decoder[NeighborTarget] = WireEnum.decoder
  given Schema[NeighborTarget] = WireEnum.schema
