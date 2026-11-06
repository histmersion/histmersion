package com.histmersion.api.library
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.given

enum DeletePart(val wire: DeletePart.Wire, val label: DeletePart.Label):
  case Findings extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("findings"), _root_.com.histmersion.api.library.DeletePart.Label("findings"))
  case FindingPlaces extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("finding-places"), _root_.com.histmersion.api.library.DeletePart.Label("gazetteer assignments of its findings"))
  case EdgesOut extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("edges-out"), _root_.com.histmersion.api.library.DeletePart.Label("edges from it"))
  case ParkedOut extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("parked-edges-out"), _root_.com.histmersion.api.library.DeletePart.Label("parked edges from it (to works not in the corpus)"))
  case EdgesIn extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("edges-in"), _root_.com.histmersion.api.library.DeletePart.Label("edges from other sources to it — parked, not lost"))
  case Chunks extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("chunks"), _root_.com.histmersion.api.library.DeletePart.Label("full-text chunks"))
  case Assets extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("assets"), _root_.com.histmersion.api.library.DeletePart.Label("figures"))
  case AssetLinks extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("asset-links"), _root_.com.histmersion.api.library.DeletePart.Label("links of its figures"))
  case AssetLinksTo extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("asset-links-to"), _root_.com.histmersion.api.library.DeletePart.Label("links of other figures to it or its findings"))
  case Attestations extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("attestations"), _root_.com.histmersion.api.library.DeletePart.Label("attestations it makes"))
  case AttestationsOf
      extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("attestations-of"), _root_.com.histmersion.api.library.DeletePart.Label("other sources' attestations of its findings, edges and datings"))
  case Assessments extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("assessments"), _root_.com.histmersion.api.library.DeletePart.Label("assessment"))
  case Aliases extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("aliases"), _root_.com.histmersion.api.library.DeletePart.Label("old citekeys"))
  case Identifiers extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("identifiers"), _root_.com.histmersion.api.library.DeletePart.Label("identifiers (DOI, file digest)"))
  case EventDatings extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("event-datings"), _root_.com.histmersion.api.library.DeletePart.Label("event datings it gives"))
  case GoalLinks extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("goal-links"), _root_.com.histmersion.api.library.DeletePart.Label("goal links to it"))
  case CoverageReasons extends DeletePart(_root_.com.histmersion.api.library.DeletePart.Wire("coverage-reasons"), _root_.com.histmersion.api.library.DeletePart.Label("articles' reasons for leaving it out"))

object DeletePart:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[DeletePart]:
    def all: Array[DeletePart] = DeletePart.values
    def wire(e: DeletePart): com.histmersion.api.library.DeletePart.WireResult = e.wire
    def fromWire(s: com.histmersion.api.library.DeletePart.FromWireS): Option[DeletePart] = DeletePart.values.find(_.wire == _root_.com.histmersion.api.library.DeletePart.Wire((s).value.trim.toLowerCase))
  given Encoder[DeletePart] = WireEnum.encoder
  given Decoder[DeletePart] = WireEnum.decoder
  given Schema[DeletePart] = WireEnum.schema
