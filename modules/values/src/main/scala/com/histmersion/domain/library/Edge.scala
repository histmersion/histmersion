package com.histmersion.domain.library

import com.histmersion.WireEnumValues

object Edge:

  /** The typed relationships between sources. Wire representation is the lowercase/underscore string (e.g.
    * `superseded_by`). The enum replaces the `EdgeKind` string constants.
    */
  enum Kind:
    case References, Challenges, Refutes, Updates, SupersededBy, Supersedes,
      Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies

  object Kind:
    type WireMapPart1 = WireMapPart1.T
    object WireMapPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireOfResult = WireOfResult.T
    object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllWire = AllWire.T
    object AllWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private val wireMap: Map[com.histmersion.domain.library.Edge.Kind.WireMapPart1, Kind] = values.map(e => wireOf(e) -> e).toMap

    /** The wire string for an edge kind: lowercase with underscores preserved. `SupersededBy` → `"superseded_by"`,
      * `FoundationalFor` → `"foundational_for"`.
      */
    def wireOf(e: Kind): com.histmersion.domain.library.Edge.Kind.WireOfResult = e match
      case References      => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("references")
      case Challenges      => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("challenges")
      case Refutes         => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("refutes")
      case Updates         => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("updates")
      case SupersededBy    => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("superseded_by")
      case Supersedes      => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("supersedes")
      case Confirms        => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("confirms")
      case Cites           => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("cites")
      case FoundationalFor => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("foundational_for")
      case CompilationOf   => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("compilation_of")
      case ReviewOf        => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("review_of")
      case Verifies        => _root_.com.histmersion.domain.library.Edge.Kind.WireOfResult("verifies")

    def fromWire(s: com.histmersion.domain.library.Edge.Kind.FromWireS): Option[Kind] = wireMap.get(s.toLowerCase)

    /** All wire strings, for validators and legends. */
    val allWire: List[com.histmersion.domain.library.Edge.Kind.AllWire] = values.map(wireOf).toList

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.domain.library.Edge.Kind.WireResult = wireOf(e)
      def fromWire(s: com.histmersion.domain.library.Edge.Kind.FromWireS): Option[Kind] = Kind.fromWire(s)
