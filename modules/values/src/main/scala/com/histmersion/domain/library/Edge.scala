package com.histmersion
package domain.library

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

object Edge:

  enum Kind:
    case References, Challenges, Refutes, Updates, SupersededBy, Supersedes,
      Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    private val wireMap: Map[Wire, Kind] = values.map(e => wireOf(e) -> e).toMap

    def wireOf(e: Kind): Wire = e match
      case References      => Wire("references")
      case Challenges      => Wire("challenges")
      case Refutes         => Wire("refutes")
      case Updates         => Wire("updates")
      case SupersededBy    => Wire("superseded_by")
      case Supersedes      => Wire("supersedes")
      case Confirms        => Wire("confirms")
      case Cites           => Wire("cites")
      case FoundationalFor => Wire("foundational_for")
      case CompilationOf   => Wire("compilation_of")
      case ReviewOf        => Wire("review_of")
      case Verifies        => Wire("verifies")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: Wire): Option[Kind] = wireMap.get(Wire((s).value.toLowerCase))

    val allWire: List[Wire] = (values.map(wireOf).toList).map(refactorValue => refactorValue)

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = Kind.fromWire(Wire((s).value))
