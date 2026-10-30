package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** An event's id, a slug (`uluburun_shipwreck`). */
type EventId = EventId.T

object EventId extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]
