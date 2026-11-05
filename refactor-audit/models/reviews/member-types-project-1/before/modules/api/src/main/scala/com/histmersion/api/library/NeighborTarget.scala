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
  given WireEnumValues[NeighborTarget]:
    def all: Array[NeighborTarget] = NeighborTarget.values
    def wire(e: NeighborTarget): String = e.toString.toLowerCase
    def fromWire(s: String): Option[NeighborTarget] =
      NeighborTarget.values.find(_.toString.equalsIgnoreCase(s))

  given Encoder[NeighborTarget] = WireEnum.encoder
  given Decoder[NeighborTarget] = WireEnum.decoder
  given Schema[NeighborTarget] = WireEnum.schema
