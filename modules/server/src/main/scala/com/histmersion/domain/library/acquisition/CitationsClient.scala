package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{FollowupRow, WorkRef}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Papers CITING a work, with whatever intent signal the backend has. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait CitationsClient:
  /** `(backend, rows)` — Semantic Scholar (intents + influence + contexts), OpenAlex as fallback. */
  def citing(
      work: WorkRef,
      since: Option[Year],
      limit: CitationsClient.CitingLimit
  ): Either[Problem, (CitationsClient.Backend, List[FollowupRow])]

object CitationsClient:
  type Backend = Backend.T
  object Backend extends Newtype[String, Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends Newtype[Int, Pure]
