package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A source's stable identity across renames, merges and databases (`sources.uid`). */
type SourceUid = SourceUid.T

object SourceUid extends RefinedType[java.util.UUID, Pure]
