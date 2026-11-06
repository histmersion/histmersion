package com.histmersion.domain.library

import com.histmersion.{Ascii, WireEnumValues}
enum EditionsPolicy:
  /** Merge them (the year issue then takes the earliest year). */
  case Merge

  /** Keep them as two sources. */
  case Separate

object EditionsPolicy:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[EditionsPolicy]:
    def all: Array[EditionsPolicy] = EditionsPolicy.values
    def wire(e: EditionsPolicy): _root_.com.histmersion.WireEnumValues.WireResult = Ascii.lower(_root_.com.histmersion.Ascii.LowerS(e.toString))
    def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[EditionsPolicy] = EditionsPolicy.values.find(_.toString.equalsIgnoreCase((s).value.trim))
