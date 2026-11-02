package com.histmersion.api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum CritiqueState(val wire: String):
  case Addressed extends CritiqueState("addressed")
  case Reasoned extends CritiqueState("reasoned")
  case Open extends CritiqueState("open")

object CritiqueState:
  def parse(s: String): Option[CritiqueState] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[CritiqueState]:
    def all: Array[CritiqueState] = CritiqueState.values
    def wire(e: CritiqueState): String = e.wire
    def fromWire(s: String): Option[CritiqueState] = parse(s)
  given Encoder[CritiqueState] = WireEnum.encoder
  given Decoder[CritiqueState] = WireEnum.decoder
  given Schema[CritiqueState] = WireEnum.schema
