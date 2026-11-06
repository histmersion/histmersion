package com.histmersion
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): com.histmersion.WireEnumValues.Wire
  def fromWire(s: com.histmersion.WireEnumValues.Wire): Option[E]

object WireEnumValues:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
