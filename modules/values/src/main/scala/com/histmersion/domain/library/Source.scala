package com.histmersion
package domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Source:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    def wire: Status.Wire = Status.Wire(Ascii.lower((toString)))

  object Status:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def fromString(s: Wire): Status =
      values.find(_.toString.equalsIgnoreCase((s).value)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = values
      def wire(e: Status): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
      def fromWire(s: WireEnumValues.Wire): Option[Status] =
        values.find(_.toString.equalsIgnoreCase((s).value))

  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]
