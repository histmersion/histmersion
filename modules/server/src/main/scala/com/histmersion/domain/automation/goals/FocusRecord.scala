package com.histmersion.domain.automation.goals

import java.time.Instant

/** What the focus recorded about one goal, as [[FocusService.InMemory]] holds it. */
final case class FocusRecord(
    active: Boolean,
    pinned: Boolean,
    priority: Int,
    since: Option[Instant],
    why: Option[String],
    leftAt: Option[Instant],
    leftWhy: Option[String]
)

object FocusRecord:
  val none: FocusRecord = FocusRecord(false, false, 0, None, None, None, None)
