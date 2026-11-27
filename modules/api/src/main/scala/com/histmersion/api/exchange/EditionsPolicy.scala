package com.histmersion
package api.exchange

import com.histmersion.api.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

enum EditionsPolicy:
  /** Merge them (the year issue then takes the earliest year). */
  case Merge

  /** Keep them as two sources. */
  case Separate

object EditionsPolicy:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[EditionsPolicy]:
    def all: Array[EditionsPolicy] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: EditionsPolicy): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[EditionsPolicy] =
      values.find(_.toString.equalsIgnoreCase((s).value.trim))
