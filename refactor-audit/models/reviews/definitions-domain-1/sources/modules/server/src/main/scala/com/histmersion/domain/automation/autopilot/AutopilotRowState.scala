package com.histmersion.domain.automation.autopilot

import java.time.Instant

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
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangedBy = ChangedBy.T
  object ChangedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Paused = Paused.T
  object Paused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Strategy = Strategy.T
  object Strategy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategyBy = StrategyBy.T
  object StrategyBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainBy = DrainBy.T
  object DrainBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def made(now: Instant): AutopilotRowState =
    AutopilotRowState(false, now, "", Nil, None, None, None, None, None, None, None, None, None, None)
