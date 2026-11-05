package com.histmersion.domain.automation.worklists

/** Why a change to a work-list item was refused. */
enum QueueError:
  case NotFound(id: QueueItemId)

  def message: String = this match
    case NotFound(id) => s"No queue item #${id.value}"
