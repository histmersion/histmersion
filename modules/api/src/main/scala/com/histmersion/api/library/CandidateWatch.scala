package com.histmersion
package api.library

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class CandidateWatch(
    goal: CandidateWatch.Goal,
    kind: CandidateWatch.Kind,
    subject: CandidateWatch.Subject,
    index: CandidateWatch.Index,
    watermark: Option[CandidateWatch.Watermark] = None,
    lastRun: Option[CandidateWatch.LastRun] = None,
    lastError: Option[Problem] = None,
    lastFound: CandidateWatch.LastFound = CandidateWatch.LastFound(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CandidateWatch:
  export api.research.ReviewGoal.Slug as Goal
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
