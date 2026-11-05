package com.histmersion.api.exchange

import com.histmersion.api.{Ascii, WireEnumValues}

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
    def wire(e: EditionsPolicy): com.histmersion.api.exchange.EditionsPolicy.WireResult = Ascii.lower(e.toString)
    def fromWire(s: com.histmersion.api.exchange.EditionsPolicy.FromWireS): Option[EditionsPolicy] = EditionsPolicy.values.find(_.toString.equalsIgnoreCase(s.trim))
