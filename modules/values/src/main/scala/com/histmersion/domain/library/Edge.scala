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
      case References      => "references"
      case Challenges      => "challenges"
      case Refutes         => "refutes"
      case Updates         => "updates"
      case SupersededBy    => "superseded_by"
      case Supersedes      => "supersedes"
      case Confirms        => "confirms"
      case Cites           => "cites"
      case FoundationalFor => "foundational_for"
      case CompilationOf   => "compilation_of"
      case ReviewOf        => "review_of"
      case Verifies        => "verifies"

    def fromWire(s: com.histmersion.domain.library.Edge.Kind.FromWireS): Option[Kind] = wireMap.get(s.toLowerCase)

    /** All wire strings, for validators and legends. */
    val allWire: List[com.histmersion.domain.library.Edge.Kind.AllWire] = values.map(wireOf).toList

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.domain.library.Edge.Kind.WireResult = wireOf(e)
      def fromWire(s: com.histmersion.domain.library.Edge.Kind.FromWireS): Option[Kind] = Kind.fromWire(s)
