package com.histmersion.api.library

import com.histmersion.api.{Ascii, WireEnumValues}

enum MatchMethod:
  /** The same uid. */
  case Uid

  /** A redirect (`uid_aliases`) or the ledger of an earlier merge. */
  case Ledger

  /** A strong key: the same DOI, original file (sha256) or ISBN. */
  case StrongKey

  /** The weighted score. */
  case Score

  /** Nothing here is that work. */
  case Unmatched

object MatchMethod:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[MatchMethod]:
    def all: Array[MatchMethod] = MatchMethod.values
    def wire(e: MatchMethod): _root_.com.histmersion.api.WireEnumValues.WireResult = Ascii.lower(_root_.com.histmersion.api.Ascii.LowerS(e.toString))
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[MatchMethod] = MatchMethod.values.find(_.toString.equalsIgnoreCase((s).value.trim))
