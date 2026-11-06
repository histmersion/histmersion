package com.histmersion.api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum CritiqueState(val wire: CritiqueState.Wire):
  case Addressed extends CritiqueState(_root_.com.histmersion.api.research.CritiqueState.Wire("addressed"))
  case Reasoned extends CritiqueState(_root_.com.histmersion.api.research.CritiqueState.Wire("reasoned"))
  case Open extends CritiqueState(_root_.com.histmersion.api.research.CritiqueState.Wire("open"))

object CritiqueState:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.research.CritiqueState.ParseS): Option[CritiqueState] = values.find(_.wire == _root_.com.histmersion.api.research.CritiqueState.Wire((s).value.trim.toLowerCase))
  given WireEnumValues[CritiqueState]:
    def all: Array[CritiqueState] = CritiqueState.values
    def wire(e: CritiqueState): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((e.wire).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[CritiqueState] = parse(_root_.com.histmersion.api.research.CritiqueState.ParseS((s).value))
  given Encoder[CritiqueState] = WireEnum.encoder
  given Decoder[CritiqueState] = WireEnum.decoder
  given Schema[CritiqueState] = WireEnum.schema
