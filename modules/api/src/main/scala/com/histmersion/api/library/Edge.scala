package com.histmersion.api.library
import com.histmersion.api.WireEnumValues
import com.histmersion.api.given
import com.histmersion.api.StrictJson
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import scala.annotation.tailrec
import sttp.tapir.Schema
final case class Edge(src: com.histmersion.api.library.Citekey, dst: com.histmersion.api.library.Citekey, kind: String)

object Edge:

  /** The typed relationships between sources. Wire representation is the lowercase/underscore string (e.g.
    * `superseded_by`). The enum replaces the `EdgeKind` string constants.
    */
  enum Kind:
    case References, Challenges, Refutes, Updates, SupersededBy, Supersedes,
      Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies

  object Kind:
    private val wireMap: Map[String, Kind] = values.map(e => wireOf(e) -> e).toMap

    /** The wire string for an edge kind: lowercase with underscores preserved. `SupersededBy` → `"superseded_by"`,
      * `FoundationalFor` → `"foundational_for"`.
      */
    def wireOf(e: Kind): String = e match
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

    def fromWire(s: String): Option[Kind] = wireMap.get(s.toLowerCase)

    /** All wire strings, for validators and legends. */
    val allWire: List[String] = values.map(wireOf).toList

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = wireOf(e)
      def fromWire(s: String): Option[Kind] = Kind.fromWire(s)

  final case class Input(dst: String, kind: com.histmersion.api.library.Edge.Kind, src: Option[String] = None)
      derives KindlingsSchema, com.histmersion.api.json.Schema
  object Input:


    given Codec.AsObject[Input] = StrictJson.codec(KindlingsCodecAsObject.derived[Input])
