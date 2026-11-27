package com.histmersion
package domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

object Source:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire: Status.Wire = Status.Wire(Ascii.lower((toString)))

  object Status:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromString(s: Wire): Status =
      values.find(_.toString.equalsIgnoreCase((s).value)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Status): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Status] =
        values.find(_.toString.equalsIgnoreCase((s).value))

  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]
