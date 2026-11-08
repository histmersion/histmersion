package com.histmersion
package api.library
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.research.Article
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

enum NeighborTarget:
  case Source, Article, Unresolved

object NeighborTarget:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[NeighborTarget]:
    def all: Array[NeighborTarget] = values
    def wire(e: NeighborTarget): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
    def fromWire(s: WireEnumValues.Wire): Option[NeighborTarget] =
      values.find(_.toString.equalsIgnoreCase((s).value))

  given Encoder[NeighborTarget] = WireEnum.encoder
  given Decoder[NeighborTarget] = WireEnum.decoder
  given Schema[NeighborTarget] = WireEnum.schema
