package com.histmersion
package domain.automation.worklists

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A work-list item's id (`queue_items.id`): what `queue patch|rm <id>` and a goal's `queue-item` link name. */
type QueueItemId = QueueItemId.T

object QueueItemId extends RefinedType[Int, Pure]
