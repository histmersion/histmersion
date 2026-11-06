package com.histmersion.api

/** A wire vocabulary's spellings: every value, how one is written, and how one is read back. The api's own copy of the
  * kernel's (the api names no package outside `com.histmersion.api`).
  */
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): com.histmersion.api.WireEnumValues.Wire
  def fromWire(s: com.histmersion.api.WireEnumValues.Wire): Option[E]

object WireEnumValues:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
