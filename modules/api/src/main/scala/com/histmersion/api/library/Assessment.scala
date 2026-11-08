package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

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
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Assessment:
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  export Acquire.CitedByCount
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends Newtype[Int, Pure]
  type IsRetracted = IsRetracted.T
  object IsRetracted extends Newtype[Boolean, Pure]
  type Flagged = Flagged.T
  object Flagged extends Newtype[Boolean, Pure]
  type Verdict = Verdict.T
  object Verdict extends Newtype[String, Pure]
  type Recommendation = Recommendation.T
  object Recommendation extends Newtype[String, Pure]
  type Applied = Applied.T
  object Applied extends Newtype[String, Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends Newtype[String, Pure]
