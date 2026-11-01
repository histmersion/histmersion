package com.histmersion.domain.automation.autopilot

import java.time.Instant

/** The autopilot's one row as [[AutopilotRowService.InMemory]] holds it: the switch and who turned it, the managers it
  * paused, its housekeeping times, a person's scheduler strategy, the digest's time and the drain.
  */
final case class AutopilotRowState(
    enabled: Boolean,
    changedAt: Instant,
    changedBy: String,
    paused: List[String],
    lastEnsureAt: Option[Instant],
    lastBackupAt: Option[Instant],
    lastLintAt: Option[Instant],
    strategy: Option[String],
    strategyBy: Option[String],
    strategyAt: Option[Instant],
    lastDigestAt: Option[Instant],
    drainSince: Option[Instant],
    drainBy: Option[String],
    drainedAt: Option[Instant]
)

object AutopilotRowState:
  /** The row as the database makes it: off, changed by nobody at `now`, nothing paused. */
  def made(now: Instant): AutopilotRowState =
    AutopilotRowState(false, now, "", Nil, None, None, None, None, None, None, None, None, None, None)
