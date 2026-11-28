package com.histmersion
package domain.automation.managers

import com.histmersion.api.given
import java.time.Instant
import com.histmersion.domain.ManagerPolicy.ApprovalId
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.api.automation.managers.ItemState
import io.github.iltotore.iron.constraint.any.Pure

enum Next:
  case Again(notBefore: Option[Instant], state: ItemState, note: Item.Note)

  case Done(outcome: Item.Outcome, state: ItemState)

  case Stop(outcome: Item.Outcome, task: Option[Item.Task], state: ItemState)

  case Retry(notBefore: Instant, failures: Next.Retry.Failures, note: Item.Note)

  case Wait(approval: ApprovalId, note: Item.Note)

object Next:
  object Retry:
    type Failures = Failures.T
    object Failures extends Newtype[Int, Pure]
