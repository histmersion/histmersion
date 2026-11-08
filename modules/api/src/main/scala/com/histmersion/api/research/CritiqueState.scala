package com.histmersion
package api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

enum CritiqueState(val wire: CritiqueState.Wire) derives CanEqual:
  case Addressed extends CritiqueState(CritiqueState.Wire("addressed"))
  case Reasoned extends CritiqueState(CritiqueState.Wire("reasoned"))
  case Open extends CritiqueState(CritiqueState.Wire("open"))

object CritiqueState:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Option[CritiqueState] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[CritiqueState]:
    def all: Array[CritiqueState] = values
    def wire(e: CritiqueState): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[CritiqueState] = parse(Wire((s).value))
  given Encoder[CritiqueState] = WireEnum.encoder
  given Decoder[CritiqueState] = WireEnum.decoder
  given Schema[CritiqueState] = WireEnum.schema
