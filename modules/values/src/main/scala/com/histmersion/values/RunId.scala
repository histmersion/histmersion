package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** An agent run's id. A run is a background job, so this is that job's id; its own type says which jobs are runs. */
type RunId = RunId.T

object RunId extends RefinedType[String, Match["[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"]]
