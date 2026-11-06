package com.histmersion.api.research

import com.histmersion.api.WireEnumValues

enum DraftMode(val wire: DraftMode.Wire):
  case Create extends DraftMode(_root_.com.histmersion.api.research.DraftMode.Wire("create"))
  case Extend extends DraftMode(_root_.com.histmersion.api.research.DraftMode.Wire("extend"))

object DraftMode:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.research.DraftMode.ParseS): Option[DraftMode] = values.find(_.wire == _root_.com.histmersion.api.research.DraftMode.Wire((s).value.trim.toLowerCase))
  given WireEnumValues[DraftMode]:
    def all: Array[DraftMode] = DraftMode.values
    def wire(e: DraftMode): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((e.wire).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[DraftMode] = parse(_root_.com.histmersion.api.research.DraftMode.ParseS((s).value))
