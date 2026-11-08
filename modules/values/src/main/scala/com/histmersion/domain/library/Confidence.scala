package com.histmersion
package domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
enum Confidence:
  case High, Medium, Low, Contested

  /** The wire / storage spelling (`high`). */
  def wire: Confidence.Wire = Confidence.Wire(Ascii.lower((toString)))

object Confidence:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def fromString(s: Wire): Option[Confidence] =
    values.find(_.toString.equalsIgnoreCase((s).value))

  given WireEnumValues[Confidence]:
    def all: Array[Confidence] = values
    def wire(e: Confidence): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
    def fromWire(s: WireEnumValues.Wire): Option[Confidence] =
      values.find(_.toString.equalsIgnoreCase((s).value))
