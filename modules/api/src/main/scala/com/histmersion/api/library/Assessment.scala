package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class Assessment(
    target: Assessment.Target,
    doi: Option[com.histmersion.api.library.Doi],
    citekey: Option[com.histmersion.api.library.Citekey],
    year: Option[com.histmersion.api.catalogue.Year],
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
    kept: Option[com.histmersion.api.library.Source.Status] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Assessment:
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export com.histmersion.api.library.Acquire.CitedByCount
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsRetracted = IsRetracted.T
  object IsRetracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Flagged = Flagged.T
  object Flagged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Verdict = Verdict.T
  object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Recommendation = Recommendation.T
  object Recommendation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Applied = Applied.T
  object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
