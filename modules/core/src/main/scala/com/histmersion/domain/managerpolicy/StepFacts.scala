package com.histmersion
package domain.managerpolicy

import com.histmersion.api.given
import com.histmersion.domain.ManagerPolicy.ApprovalId
import com.histmersion.domain.ManagerPolicy.Item

enum StepFacts:
  case Finished(outcome: Item.Outcome)
  case Waiting(approval: ApprovalId, what: Item.Note)
  case NotDone(outcome: Item.Outcome, task: Option[Item.Task])

  /** Its work was refused by a check that a new run may pass (a draft the autopilot's checks rejected): run it again
    * after the backoff, counted as a failure, stopped with a task after `maxFailures`.
    */
  case Redo(outcome: Item.Outcome)
