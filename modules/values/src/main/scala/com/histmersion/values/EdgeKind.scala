package com.histmersion.values

/** The typed relationships between sources. Wire representation is the lowercase/underscore string (e.g.
  * `superseded_by`). The enum replaces the `EdgeKind` string constants.
  */

import com.histmersion.WireEnumValues
enum EdgeKind:
  case References, Challenges, Refutes, Updates, SupersededBy, Supersedes,
    Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies

object EdgeKind:
  private val wireMap: Map[String, EdgeKind] = values.map(e => wireOf(e) -> e).toMap

  /** The wire string for an edge kind: lowercase with underscores preserved. `SupersededBy` → `"superseded_by"`,
    * `FoundationalFor` → `"foundational_for"`.
    */
  def wireOf(e: EdgeKind): String = e match
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

  def fromWire(s: String): Option[EdgeKind] = wireMap.get(s.toLowerCase)

  /** All wire strings, for validators and legends. */
  val allWire: List[String] = values.map(wireOf).toList

  given WireEnumValues[EdgeKind]:
    def all: Array[EdgeKind] = EdgeKind.values
    def wire(e: EdgeKind): String = wireOf(e)
    def fromWire(s: String): Option[EdgeKind] = EdgeKind.fromWire(s)
