package com.histmersion

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): WireEnumValues.Wire
  def fromWire(s: WireEnumValues.Wire): Option[E]

object WireEnumValues:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
