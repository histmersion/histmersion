package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class CandidateWatch(
    goal: CandidateWatch.Goal,
    kind: CandidateWatch.Kind,
    subject: CandidateWatch.Subject,
    index: CandidateWatch.Index,
    watermark: Option[CandidateWatch.Watermark] = None,
    lastRun: Option[CandidateWatch.LastRun] = None,
    lastError: Option[CandidateWatch.LastError] = None,
    lastFound: CandidateWatch.LastFound = _root_.com.histmersion.api.library.CandidateWatch.LastFound(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CandidateWatch:
  type Goal = com.histmersion.api.research.ReviewGoal.Slug
  val Goal: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
  type Kind = com.histmersion.api.library.Candidates.WatchKind.Wire
  val Kind: _root_.com.histmersion.api.library.Candidates.WatchKind.Wire.type = _root_.com.histmersion.api.library.Candidates.WatchKind.Wire
  type Subject = Subject.T
  object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Index = com.histmersion.api.library.Candidates.Index
  val Index: _root_.com.histmersion.api.library.Candidates.Index.type = _root_.com.histmersion.api.library.Candidates.Index
  type Watermark = Watermark.T
  object Watermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastRun = LastRun.T
  object LastRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastError = LastError.T
  object LastError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastFound = LastFound.T
  object LastFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
