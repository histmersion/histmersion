package com.histmersion
package domain.acquirerecheck

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.domain.library

final case class LastCheck(
    at: Instant,
    generation: LastCheck.Generation,
    failed: LastCheck.Failed = LastCheck.Failed(false)
)

object LastCheck:
  type Generation = Generation.T
  object Generation extends Newtype[String, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[Boolean, Pure]:
    def of(attempt: library.acquisition.AcquireAttempt): Failed = Failed(attempt.failed)

  def of(attempt: library.acquisition.AcquireAttempt): LastCheck =
    LastCheck(attempt.at, attempt.generation, Failed.of(attempt))
