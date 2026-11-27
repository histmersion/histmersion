package com.histmersion
package domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
enum Confidence:
  case High, Medium, Low, Contested

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: Confidence.Wire = Confidence.Wire(Ascii.lower((toString)))

object Confidence:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromString(s: Wire): Option[Confidence] =
    values.find(_.toString.equalsIgnoreCase((s).value))

  given WireEnumValues[Confidence]:
    def all: Array[Confidence] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: Confidence): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[Confidence] =
      values.find(_.toString.equalsIgnoreCase((s).value))
