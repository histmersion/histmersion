package com.histmersion
package domain.automation.autopilot

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure

/** The autopilot's one row as [[AutopilotRowService.InMemory]] holds it: the switch and who turned it, the managers it
  * paused, its housekeeping times, a person's scheduler strategy, the digest's time and the drain.
  */
final case class AutopilotRowState(
    enabled: AutopilotRowState.Enabled,
    changedAt: Instant,
    changedBy: AutopilotRowState.ChangedBy,
    paused: List[AutopilotRowState.Paused],
    lastEnsureAt: Option[Instant],
    lastBackupAt: Option[Instant],
    lastLintAt: Option[Instant],
    strategy: Option[AutopilotRowState.Strategy],
    strategyBy: Option[AutopilotRowState.StrategyBy],
    strategyAt: Option[Instant],
    lastDigestAt: Option[Instant],
    drainSince: Option[Instant],
    drainBy: Option[AutopilotRowState.DrainBy],
    drainedAt: Option[Instant]
)

object AutopilotRowState:
  /** The row as the database makes it: off, changed by nobody at `now`, nothing paused. */
  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  type ChangedBy = ChangedBy.T
  object ChangedBy extends Newtype[String, Pure]
  type Paused = Paused.T
  object Paused extends Newtype[String, Pure]
  type Strategy = Strategy.T
  object Strategy extends Newtype[String, Pure]
  type StrategyBy = StrategyBy.T
  object StrategyBy extends Newtype[String, Pure]
  type DrainBy = DrainBy.T
  object DrainBy extends Newtype[String, Pure]

  def made(now: Instant): AutopilotRowState =
    AutopilotRowState(Enabled(false), now, ChangedBy(""), Nil, None, None, None, None, None, None, None, None, None, None)
