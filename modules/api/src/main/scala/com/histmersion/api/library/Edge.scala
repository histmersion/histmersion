package com.histmersion
package api.library
import com.histmersion.api.WireEnumValues
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.StrictJson
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import scala.annotation.tailrec
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
final case class Edge(src: Citekey, dst: Citekey, kind: Edge.Kind)

object Edge:

  /** The typed relationships between sources. Wire representation is the lowercase/underscore string (e.g.
    * `superseded_by`). The enum replaces the `EdgeKind` string constants.
    */
  enum Kind derives CanEqual:
    case References, Challenges, Refutes, Updates, SupersededBy, Supersedes,
      Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    private val wireMap: Map[Wire, Kind] = values.map(e => wireOf(e) -> e).toMap

    /** The wire string for an edge kind: lowercase with underscores preserved. `SupersededBy` → `"superseded_by"`,
      * `FoundationalFor` → `"foundational_for"`.
      */
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

    /** All wire strings, for validators and legends. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val allWire: List[Wire] = (values.map(wireOf).toList).map(refactorValue => Wire((refactorValue).value))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = Kind.fromWire(Wire((s).value))

  final case class Input(dst: Citekey, kind: Kind, src: Option[Citekey] = None)
      derives StrictJson,
        KindlingsSchema,
        api.json.Schema
