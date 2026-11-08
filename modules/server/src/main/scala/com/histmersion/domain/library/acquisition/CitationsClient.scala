package com.histmersion
package domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{FollowupRow, WorkRef}
import io.github.iltotore.iron.constraint.any.Pure

/** Papers CITING a work, with whatever intent signal the backend has. */
trait CitationsClient:
  /** `(backend, rows)` — Semantic Scholar (intents + influence + contexts), OpenAlex as fallback. */
  def citing(work: WorkRef, since: Option[Year], limit: CitationsClient.CitingLimit): Either[CitationsClient.Problem, (CitationsClient.CitingResultPart2Part1, List[FollowupRow])]

object CitationsClient:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type CitingResultPart2Part1 = CitingResultPart2Part1.T
  object CitingResultPart2Part1 extends Newtype[String, Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends Newtype[Int, Pure]
