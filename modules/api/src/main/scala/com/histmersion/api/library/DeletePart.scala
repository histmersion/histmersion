package com.histmersion.api.library
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.given

enum DeletePart(val wire: String, val label: String):
  case Findings extends DeletePart("findings", "findings")
  case FindingPlaces extends DeletePart("finding-places", "gazetteer assignments of its findings")
  case EdgesOut extends DeletePart("edges-out", "edges from it")
  case ParkedOut extends DeletePart("parked-edges-out", "parked edges from it (to works not in the corpus)")
  case EdgesIn extends DeletePart("edges-in", "edges from other sources to it — parked, not lost")
  case Chunks extends DeletePart("chunks", "full-text chunks")
  case Assets extends DeletePart("assets", "figures")
  case AssetLinks extends DeletePart("asset-links", "links of its figures")
  case AssetLinksTo extends DeletePart("asset-links-to", "links of other figures to it or its findings")
  case Attestations extends DeletePart("attestations", "attestations it makes")
  case AttestationsOf
      extends DeletePart("attestations-of", "other sources' attestations of its findings, edges and datings")
  case Assessments extends DeletePart("assessments", "assessment")
  case Aliases extends DeletePart("aliases", "old citekeys")
  case Identifiers extends DeletePart("identifiers", "identifiers (DOI, file digest)")
  case EventDatings extends DeletePart("event-datings", "event datings it gives")
  case GoalLinks extends DeletePart("goal-links", "goal links to it")
  case CoverageReasons extends DeletePart("coverage-reasons", "articles' reasons for leaving it out")

object DeletePart:
  given WireEnumValues[DeletePart]:
    def all: Array[DeletePart] = DeletePart.values
    def wire(e: DeletePart): String = e.wire
    def fromWire(s: String): Option[DeletePart] = DeletePart.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DeletePart] = WireEnum.encoder
  given Decoder[DeletePart] = WireEnum.decoder
  given Schema[DeletePart] = WireEnum.schema
