package com.histmersion.domain.research

import com.histmersion.WireEnumValues
enum DraftMode(val wire: DraftMode.Wire):
  case Create extends DraftMode(_root_.com.histmersion.domain.research.DraftMode.Wire("create"))
  case Extend extends DraftMode(_root_.com.histmersion.domain.research.DraftMode.Wire("extend"))

object DraftMode:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: com.histmersion.domain.research.DraftMode.Wire): Option[DraftMode] = values.find(_.wire == _root_.com.histmersion.domain.research.DraftMode.Wire((s).value.trim.toLowerCase))
  given WireEnumValues[DraftMode]:
    def all: Array[DraftMode] = DraftMode.values
    def wire(e: DraftMode): _root_.com.histmersion.WireEnumValues.Wire = _root_.com.histmersion.WireEnumValues.Wire((e.wire).value)
    def fromWire(s: _root_.com.histmersion.WireEnumValues.Wire): _root_.scala.Option[DraftMode] = parse(_root_.com.histmersion.domain.research.DraftMode.Wire((s).value))
