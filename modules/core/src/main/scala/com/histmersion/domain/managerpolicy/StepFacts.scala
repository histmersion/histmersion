package com.histmersion
package domain.managerpolicy

import com.histmersion.api.given
import com.histmersion.domain.ManagerPolicy.ApprovalId
import com.histmersion.domain.ManagerPolicy.Item

enum StepFacts:
  case Finished(outcome: Item.Outcome)
  case Waiting(approval: ApprovalId, what: Item.Note)
  case NotDone(outcome: Item.Outcome, task: Option[Item.Task])

  case Redo(outcome: Item.Outcome)
