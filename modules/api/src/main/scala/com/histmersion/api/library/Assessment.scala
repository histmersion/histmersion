package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class Assessment(
    target: Assessment.Target,
    doi: Option[Doi],
    citekey: Option[Citekey],
    year: Option[Year],
    citedByCount: Option[Assessment.CitedByCount],
    influentialCitations: Option[Assessment.InfluentialCitations],
    isRetracted: Assessment.IsRetracted,
    retractionWatch: List[RetractionNotice],
    crossrefUpdates: List[UpdateNotice],
    flagged: Assessment.Flagged,
    verdict: Assessment.Verdict,
    recommendation: Assessment.Recommendation,
    applied: Option[Assessment.Applied] = None,
    checkedAt: Option[Assessment.CheckedAt] = None,
    kept: Option[Source.Status] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Assessment:
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  export Acquire.CitedByCount
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends Newtype[Int, Pure]
  enum IsRetracted derives CanEqual:
    case Retracted, Standing
  object IsRetracted extends TwoStates[IsRetracted]:
    extension (isRetracted: IsRetracted) def isRetracted: Boolean = isRetracted == Retracted
  enum Flagged derives CanEqual:
    case Flagged, Clear
  object Flagged extends TwoStates[Flagged]:
    extension (flagged: Flagged) def flagged: Boolean = flagged == Flagged
  type Verdict = Verdict.T
  object Verdict extends Newtype[String, Pure]
  export Acquire.Recommendation
  type Applied = Applied.T
  object Applied extends Newtype[String, Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends Newtype[String, Pure]
