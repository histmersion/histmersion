package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A background job's id: a UUID in its lower-case text form, as the `jobs` table keeps it. */
type JobId = JobId.T

object JobId extends RefinedType[String, Match["[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"]]
