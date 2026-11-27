package com.histmersion

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
@atBoundary(BoundaryKind.Json, "lists an enum's wire spellings and how to read one back")
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): WireEnumValues.Wire
  def fromWire(s: WireEnumValues.Wire): Option[E]

object WireEnumValues:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
