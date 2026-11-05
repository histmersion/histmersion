package com.histmersion.domain.automation.worklists

/** Why a change to a work-list item was refused. */
enum QueueError:
  case NotFound(id: QueueItemId)

  def message: com.histmersion.domain.automation.worklists.QueueError.MessageResult = this match
    case NotFound(id) => s"No queue item #${id.value}"

object QueueError:
  type MessageResult = MessageResult.T
  object MessageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
