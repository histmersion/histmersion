package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** An Internet Archive item identifier (`hittiteempire00gurn`). */
type ArchiveId = ArchiveId.T

object ArchiveId extends RefinedType[String, Pure]
