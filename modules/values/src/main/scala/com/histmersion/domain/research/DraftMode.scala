package com.histmersion.domain.research

import com.histmersion.WireEnumValues
enum DraftMode(val wire: String):
  case Create extends DraftMode("create")
  case Extend extends DraftMode("extend")

object DraftMode:
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.domain.research.DraftMode.ParseS): Option[DraftMode] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[DraftMode]:
    def all: Array[DraftMode] = DraftMode.values
    def wire(e: DraftMode): com.histmersion.domain.research.DraftMode.WireResult = e.wire
    def fromWire(s: com.histmersion.domain.research.DraftMode.FromWireS): Option[DraftMode] = parse(s)
