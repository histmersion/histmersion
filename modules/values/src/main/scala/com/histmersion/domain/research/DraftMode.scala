package com.histmersion
package domain.research

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
enum DraftMode(val wire: DraftMode.Wire):
  case Create extends DraftMode(DraftMode.Wire("create"))
  case Extend extends DraftMode(DraftMode.Wire("extend"))

object DraftMode:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[DraftMode] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[DraftMode]:
    def all: Array[DraftMode] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: DraftMode): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[DraftMode] = parse(Wire((s).value))
