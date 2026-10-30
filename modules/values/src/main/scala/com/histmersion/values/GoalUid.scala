package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A goal's stable identity across renames, merges and databases (`goals.uid`). */
type GoalUid = GoalUid.T

object GoalUid extends RefinedType[java.util.UUID, Pure]
