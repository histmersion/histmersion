package com.histmersion
package api.research

import com.histmersion.api.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure

enum DraftMode(val wire: DraftMode.Wire):
  case Create extends DraftMode(DraftMode.Wire("create"))
  case Extend extends DraftMode(DraftMode.Wire("extend"))

object DraftMode:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]


  def parse(s: Wire): Option[DraftMode] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[DraftMode]:
    def all: Array[DraftMode] = values
    def wire(e: DraftMode): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[DraftMode] = parse(Wire((s).value))
