package com.histmersion.api.library
import com.histmersion.api.WireEnumValues
import com.histmersion.api.given
import com.histmersion.api.StrictJson
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import scala.annotation.tailrec
import sttp.tapir.Schema
final case class Edge(src: com.histmersion.api.library.Citekey, dst: com.histmersion.api.library.Citekey, kind: com.histmersion.api.library.Edge.Kind)

object Edge:

  /** The typed relationships between sources. Wire representation is the lowercase/underscore string (e.g.
    * `superseded_by`). The enum replaces the `EdgeKind` string constants.
    */
  enum Kind:
    case References, Challenges, Refutes, Updates, SupersededBy, Supersedes,
      Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies

  object Kind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private val wireMap: Map[com.histmersion.api.library.Edge.Kind.Wire, Kind] = values.map(e => wireOf(e) -> e).toMap

    /** The wire string for an edge kind: lowercase with underscores preserved. `SupersededBy` → `"superseded_by"`,
      * `FoundationalFor` → `"foundational_for"`.
      */
    def wireOf(e: Kind): com.histmersion.api.library.Edge.Kind.Wire = e match
      case References      => _root_.com.histmersion.api.library.Edge.Kind.Wire("references")
      case Challenges      => _root_.com.histmersion.api.library.Edge.Kind.Wire("challenges")
      case Refutes         => _root_.com.histmersion.api.library.Edge.Kind.Wire("refutes")
      case Updates         => _root_.com.histmersion.api.library.Edge.Kind.Wire("updates")
      case SupersededBy    => _root_.com.histmersion.api.library.Edge.Kind.Wire("superseded_by")
      case Supersedes      => _root_.com.histmersion.api.library.Edge.Kind.Wire("supersedes")
      case Confirms        => _root_.com.histmersion.api.library.Edge.Kind.Wire("confirms")
      case Cites           => _root_.com.histmersion.api.library.Edge.Kind.Wire("cites")
      case FoundationalFor => _root_.com.histmersion.api.library.Edge.Kind.Wire("foundational_for")
      case CompilationOf   => _root_.com.histmersion.api.library.Edge.Kind.Wire("compilation_of")
      case ReviewOf        => _root_.com.histmersion.api.library.Edge.Kind.Wire("review_of")
      case Verifies        => _root_.com.histmersion.api.library.Edge.Kind.Wire("verifies")

    def fromWire(s: com.histmersion.api.library.Edge.Kind.Wire): Option[Kind] = wireMap.get(_root_.com.histmersion.api.library.Edge.Kind.Wire((s).value.toLowerCase))

    /** All wire strings, for validators and legends. */
    val allWire: List[com.histmersion.api.library.Edge.Kind.Wire] = (values.map(wireOf).toList).map(refactorValue => _root_.com.histmersion.api.library.Edge.Kind.Wire((refactorValue).value))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((wireOf(e)).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[Kind] = Kind.fromWire(_root_.com.histmersion.api.library.Edge.Kind.Wire((s).value))

  final case class Input(dst: com.histmersion.api.library.Citekey, kind: com.histmersion.api.library.Edge.Kind, src: Option[com.histmersion.api.library.Citekey] = None)
      derives KindlingsSchema, com.histmersion.api.json.Schema
  object Input:


    given Codec.AsObject[Input] = StrictJson.codec(KindlingsCodecAsObject.derived[Input])
