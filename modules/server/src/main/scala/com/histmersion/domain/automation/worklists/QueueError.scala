package com.histmersion
package domain.automation.worklists

/** Why a change to a work-list item was refused. */
enum QueueError derives CanEqual:
  case NotFound(id: QueueItemId)

  def message: String = this match
    case NotFound(id) => s"No queue item #${id.value}"
