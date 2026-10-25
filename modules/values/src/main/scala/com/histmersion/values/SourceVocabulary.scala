package com.histmersion.values

enum SourceStatus:
  case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed
  /** The wire / storage spelling (`current`), as the JSON codec writes it. */
  def wire: String = Ascii.lower(toString)

object SourceStatus:

  def fromString(s: String): SourceStatus =
    values.find(_.toString.equalsIgnoreCase(s)).getOrElse(Unassessed)

  given WireEnumValues[SourceStatus]:
    def all: Array[SourceStatus] = SourceStatus.values
    def wire(e: SourceStatus): String = e.toString.toLowerCase
    def fromWire(s: String): Option[SourceStatus] =
      SourceStatus.values.find(_.toString.equalsIgnoreCase(s))

/** Confidence of a finding: how firmly the source states the claim.
  * Stored in the DB as a lowercase string; the enum is the contract. */
enum Confidence:
  case High, Medium, Low, Contested

  /** The wire / storage spelling (`high`). */
  def wire: String = Ascii.lower(toString)

object Confidence:
  def fromString(s: String): Option[Confidence] =
    values.find(_.toString.equalsIgnoreCase(s))

  given WireEnumValues[Confidence]:
    def all: Array[Confidence] = Confidence.values
    def wire(e: Confidence): String = e.toString.toLowerCase
    def fromWire(s: String): Option[Confidence] =
      Confidence.values.find(_.toString.equalsIgnoreCase(s))

/** The typed relationships between sources. Wire representation is the lowercase/underscore string
  * (e.g. `superseded_by`). The enum replaces the `EdgeKind` string constants. */
enum EdgeKind:
  case References, Challenges, Refutes, Updates, SupersededBy, Supersedes,
       Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies

object EdgeKind:
  private val wireMap: Map[String, EdgeKind] = values.map(e => wireOf(e) -> e).toMap

  /** The wire string for an edge kind: lowercase with underscores preserved.
    * `SupersededBy` → `"superseded_by"`, `FoundationalFor` → `"foundational_for"`. */
  def wireOf(e: EdgeKind): String = e match
    case References     => "references"
    case Challenges     => "challenges"
    case Refutes        => "refutes"
    case Updates        => "updates"
    case SupersededBy   => "superseded_by"
    case Supersedes     => "supersedes"
    case Confirms       => "confirms"
    case Cites          => "cites"
    case FoundationalFor => "foundational_for"
    case CompilationOf  => "compilation_of"
    case ReviewOf       => "review_of"
    case Verifies       => "verifies"

  def fromWire(s: String): Option[EdgeKind] = wireMap.get(s.toLowerCase)

  /** All wire strings, for validators and legends. */
  val allWire: List[String] = values.map(wireOf).toList

  given WireEnumValues[EdgeKind]:
    def all: Array[EdgeKind] = EdgeKind.values
    def wire(e: EdgeKind): String = wireOf(e)
    def fromWire(s: String): Option[EdgeKind] = EdgeKind.fromWire(s)
