package com.histmersion
package api.library

import com.histmersion.api.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure

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
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[MatchMethod]:
    def all: Array[MatchMethod] = values
    def wire(e: MatchMethod): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
    def fromWire(s: WireEnumValues.Wire): Option[MatchMethod] = values.find(_.toString.equalsIgnoreCase((s).value.trim))
