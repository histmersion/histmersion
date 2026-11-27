package com.histmersion
package api.library

import com.histmersion.api.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: MatchMethod): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[MatchMethod] = values.find(_.toString.equalsIgnoreCase((s).value.trim))
