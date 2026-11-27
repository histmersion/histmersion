package com.histmersion
package api

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, movesTo, BoundaryKind, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** A wire vocabulary's spellings: every value, how one is written, and how one is read back. The api's own copy of the
  * kernel's (the api names no package outside `com.histmersion.api`).
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.Json, "lists an enum's wire spellings and how to read one back")
@movesTo(FeaturePlace.Kernel)
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): WireEnumValues.Wire
  def fromWire(s: WireEnumValues.Wire): Option[E]

object WireEnumValues:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]:
    /** The spelling a value of any newtype holds (`Wire.of(verb.word)`): unwrapped here, like `Problem.Raw.of`. */
    def of[T](value: T)(using RefinedType.Mirror[T]): Wire = Wire(s"$value")
