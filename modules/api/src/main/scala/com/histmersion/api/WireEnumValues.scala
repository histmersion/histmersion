package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure

/** A wire vocabulary's spellings: every value, how one is written, and how one is read back. The api's own copy of the
  * kernel's (the api names no package outside `com.histmersion.api`).
  */
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): WireEnumValues.Wire
  def fromWire(s: WireEnumValues.Wire): Option[E]

object WireEnumValues:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
