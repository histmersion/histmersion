package com.histmersion
package domain.automation.scheduler

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.scheduler.JobType
import com.histmersion.domain.automation.scheduler.Scored
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class PassState(started: Map[JobType, PassState.StartedValue], admitted: List[Scored])

object PassState:
  type StartedValue = StartedValue.T
  object StartedValue extends Newtype[Int, Pure]
