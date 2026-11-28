package com.histmersion
package domain.managerpolicy

import com.histmersion.api.given
import java.time.Instant
import com.histmersion.domain.ManagerPolicy.ApprovalId
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.api.automation.managers.ItemState
import io.github.iltotore.iron.constraint.any.Pure

enum Next:
  /** Run it again (not before `notBefore`). */
  case Again(notBefore: Option[Instant], state: ItemState, note: Item.Note)

  /** It reached its end. */
  case Done(outcome: Item.Outcome, state: ItemState)

  /** It stops here; `task` is the backlog line a person acts on (None: nothing to act on). */
  case Stop(outcome: Item.Outcome, task: Option[Item.Task], state: ItemState)

  /** Its run failed: try again after the backoff (`failures` counted). */
  case Retry(notBefore: Instant, failures: Next.Retry.Failures, note: Item.Note)

  /** Its run left a proposal for a person (approval `approval`): the item is held until it is decided, then settled
    * with the decision — never run again for it.
    */
  case Wait(approval: ApprovalId, note: Item.Note)

object Next:
  object Retry:
    type Failures = Failures.T
    object Failures extends Newtype[Int, Pure]
