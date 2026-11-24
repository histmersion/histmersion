package com.histmersion
package domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
enum MatchMethod:
  case Uid

  case Ledger

  case StrongKey

  case Score

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
