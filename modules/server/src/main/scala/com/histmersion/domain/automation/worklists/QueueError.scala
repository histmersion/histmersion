package com.histmersion
package domain.automation.worklists

import com.histmersion.util.problem.Problem

/** Why a change to a work-list item was refused. */
enum QueueError:
  case NotFound(id: QueueItemId)

  def problem: Problem = this match
    case NotFound(id) => Problem.NotFound.Entity(Problem.TypeName.of(QueueItemId), Problem.Raw.of(id))
