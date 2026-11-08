package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class CandidateWatch(
    goal: CandidateWatch.Goal,
    kind: CandidateWatch.Kind,
    subject: CandidateWatch.Subject,
    index: CandidateWatch.Index,
    watermark: Option[CandidateWatch.Watermark] = None,
    lastRun: Option[CandidateWatch.LastRun] = None,
    lastError: Option[CandidateWatch.LastError] = None,
    lastFound: CandidateWatch.LastFound = CandidateWatch.LastFound(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CandidateWatch:
  export com.histmersion.api.research.ReviewGoal.{Slug as Goal}
  export Candidates.WatchKind.{Wire as Kind}
  type Subject = Subject.T
  object Subject extends RefinedType[String, Pure]
  export Candidates.Index
  type Watermark = Watermark.T
  object Watermark extends RefinedType[String, Pure]
  type LastRun = LastRun.T
  object LastRun extends RefinedType[String, Pure]
  type LastError = LastError.T
  object LastError extends RefinedType[String, Pure]
  type LastFound = LastFound.T
  object LastFound extends RefinedType[Int, Pure]
