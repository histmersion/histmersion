package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{FollowupRow, WorkRef}

/** Papers CITING a work, with whatever intent signal the backend has. */
trait CitationsClient:
  /** `(backend, rows)` — Semantic Scholar (intents + influence + contexts), OpenAlex as fallback. */
  def citing(work: WorkRef, since: Option[com.histmersion.Year], limit: com.histmersion.domain.library.acquisition.CitationsClient.CitingLimit): Either[com.histmersion.domain.library.acquisition.CitationsClient.Problem, (com.histmersion.domain.library.acquisition.CitationsClient.CitingResultPart2Part1, List[FollowupRow])]

object CitationsClient:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingResultPart2Part1 = CitingResultPart2Part1.T
  object CitingResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
