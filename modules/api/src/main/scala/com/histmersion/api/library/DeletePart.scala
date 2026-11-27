package com.histmersion
package api.library
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

enum DeletePart(val wire: DeletePart.Wire, val label: DeletePart.Label) derives CanEqual:
  case Findings extends DeletePart(DeletePart.Wire("findings"), DeletePart.Label("findings"))
  case FindingPlaces
      extends DeletePart(DeletePart.Wire("finding-places"), DeletePart.Label("gazetteer assignments of its findings"))
  case EdgesOut extends DeletePart(DeletePart.Wire("edges-out"), DeletePart.Label("edges from it"))
  case ParkedOut
      extends DeletePart(
        DeletePart.Wire("parked-edges-out"),
        DeletePart.Label("parked edges from it (to works not in the corpus)")
      )
  case EdgesIn
      extends DeletePart(
        DeletePart.Wire("edges-in"),
        DeletePart.Label("edges from other sources to it — parked, not lost")
      )
  case Chunks extends DeletePart(DeletePart.Wire("chunks"), DeletePart.Label("full-text chunks"))
  case Assets extends DeletePart(DeletePart.Wire("assets"), DeletePart.Label("figures"))
  case AssetLinks extends DeletePart(DeletePart.Wire("asset-links"), DeletePart.Label("links of its figures"))
  case AssetLinksTo
      extends DeletePart(
        DeletePart.Wire("asset-links-to"),
        DeletePart.Label("links of other figures to it or its findings")
      )
  case Attestations extends DeletePart(DeletePart.Wire("attestations"), DeletePart.Label("attestations it makes"))
  case AttestationsOf
      extends DeletePart(
        DeletePart.Wire("attestations-of"),
        DeletePart.Label("other sources' attestations of its findings, edges and datings")
      )
  case Assessments extends DeletePart(DeletePart.Wire("assessments"), DeletePart.Label("assessment"))
  case Aliases extends DeletePart(DeletePart.Wire("aliases"), DeletePart.Label("old citekeys"))
  case Identifiers
      extends DeletePart(DeletePart.Wire("identifiers"), DeletePart.Label("identifiers (DOI, file digest)"))
  case EventDatings extends DeletePart(DeletePart.Wire("event-datings"), DeletePart.Label("event datings it gives"))
  case GoalLinks extends DeletePart(DeletePart.Wire("goal-links"), DeletePart.Label("goal links to it"))
  case CoverageReasons
      extends DeletePart(DeletePart.Wire("coverage-reasons"), DeletePart.Label("articles' reasons for leaving it out"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object DeletePart:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  given WireEnumValues[DeletePart]:
    def all: Array[DeletePart] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: DeletePart): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[DeletePart] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given JsonValueCodec[DeletePart] = WireEnum.codec
  given Schema[DeletePart] = WireEnum.schema
