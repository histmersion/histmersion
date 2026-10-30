package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** An event's stable identity across renames, merges and databases (`events.uid`). */
type EventUid = EventUid.T

object EventUid extends RefinedType[java.util.UUID, Pure]
