package com.histmersion
package api.library
import com.histmersion.api.Problem
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class CandidateWatch(
    goal: CandidateWatch.Goal,
    kind: CandidateWatch.Kind,
    subject: CandidateWatch.Subject,
    index: CandidateWatch.Index,
    watermark: Option[CandidateWatch.Watermark] = None,
    lastRun: Option[CandidateWatch.LastRun] = None,
    lastError: Option[Problem] = None,
    lastFound: CandidateWatch.LastFound = CandidateWatch.LastFound(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CandidateWatch:
  export com.histmersion.api.research.ReviewGoal.Slug as Goal
  export Candidates.WatchKind.Wire as Kind
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  export Candidates.Index
  type Watermark = Watermark.T
  object Watermark extends Newtype[String, Pure]
  type LastRun = LastRun.T
  object LastRun extends Newtype[String, Pure]
  type LastFound = LastFound.T
  object LastFound extends Newtype[Int, Pure]
