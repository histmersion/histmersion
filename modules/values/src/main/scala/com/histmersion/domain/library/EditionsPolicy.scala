package com.histmersion
package domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
enum EditionsPolicy derives CanEqual:
  /** Merge them (the year issue then takes the earliest year). */
  case Merge

  /** Keep them as two sources. */
  case Separate

object EditionsPolicy:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[EditionsPolicy]:
    def all: Array[EditionsPolicy] = values
    def wire(e: EditionsPolicy): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
    def fromWire(s: WireEnumValues.Wire): Option[EditionsPolicy] = values.find(_.toString.equalsIgnoreCase((s).value.trim))
