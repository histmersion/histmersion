package com.histmersion
package domain.automation.autopilot
import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Managers
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The autopilot's one row (V096, docs/research-process-managers.md "As built: the autopilot"): the switch, who turned
  * it and when, the managers it paused when it was turned off, and when it last ensured the goals' managers and ran its
  * housekeeping. Every statement runs on the connection it is given (`AutopilotRowService` opens it).
  *
  * The migration makes the row (`V109__schema.sql`, as V096 did before the squash), so a read only reads: a database
  * without it (a row deleted by hand) reads as the row the migration makes, and the first write makes it again.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the autopilot tables and their row codecs")
object AutopilotRepo:
  type Digest = Digest.T
  object Digest extends Newtype[String, Pure]
  type StrategyName = StrategyName.T
  object StrategyName extends Newtype[String, Pure]
  type DrainSince = DrainSince.T
  object DrainSince extends Newtype[String, Pure]
  type DrainedAt = DrainedAt.T
  object DrainedAt extends Newtype[String, Pure]

  /** The row as read. Times are ISO instants (UTC). */
  final case class Row(
      enabled: Row.Enabled,
      changedAt: Row.ChangedAt,
      changedBy: Row.ChangedBy,
      paused: List[Row.Paused],
      lastEnsureAt: Option[Row.LastEnsureAt],
      lastBackupAt: Option[Row.LastBackupAt],
      lastLintAt: Option[Row.LastLintAt]
  ) derives DbCodec

  object Row:
    type Enabled = Enabled.T
    object Enabled extends Newtype[Boolean, Pure]
    type ChangedAt = ChangedAt.T
    object ChangedAt extends Newtype[String, Pure]
    type ChangedBy = ChangedBy.T
    object ChangedBy extends Newtype[String, Pure]
    type Paused = Paused.T
    object Paused extends Newtype[String, Pure]
    type LastEnsureAt = LastEnsureAt.T
    object LastEnsureAt extends Newtype[String, Pure]
    type LastBackupAt = LastBackupAt.T
    object LastBackupAt extends Newtype[String, Pure]
    type LastLintAt = LastLintAt.T
    object LastLintAt extends Newtype[String, Pure]

  /** The row; read only. Without one (deleted by hand: the migration makes it), the row the migration makes — off,
    * changed now by nobody, nothing paused, nothing done yet — and nothing is written.
    */
  def get()(using DbCon): Row =
    sql"""SELECT coalesce(a.enabled, false), ${Timestamps.isoSeconds(sql"coalesce(a.changed_at, now())")},
                       coalesce(a.changed_by, ''), coalesce(a.paused, '{}'::text[]), ${Timestamps.isoSeconds(
        sql"a.last_ensure_at"
      )},
                       ${Timestamps.isoSeconds(sql"a.last_backup_at")}, ${Timestamps.isoSeconds(sql"a.last_lint_at")}
                       FROM (VALUES (true)) AS one (id) LEFT JOIN agent_autopilot a ON a.id"""
      .query[Row]
      .run()
      .head

  /** The row made again before a write, if it was deleted (the migration makes it; reads never do). */
  private def made()(using DbCon): Unit =
    Unused(
      sql"INSERT INTO agent_autopilot (id) VALUES (true) ON CONFLICT DO NOTHING".update.run(),
      Unused.Why.Idempotent
    )

  /** Switch it; `paused` = the managers it paused (turning it off) or none (turning it on). True = it changed (it was
    * the other way).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def set(enabled: Row.Enabled, by: Row.ChangedBy, paused: List[Row.Paused])(using DbCon): Boolean =
    made()
    sql"""UPDATE agent_autopilot SET enabled = $enabled, changed_at = now(), changed_by = $by, paused = $paused::text[],
                                     last_ensure_at = NULL
          WHERE id AND enabled <> $enabled""".update.run() > 0

  /** It made sure of the goals' managers now. */
  def ensured(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    made()
    Unused(sql"UPDATE agent_autopilot SET last_ensure_at = $t::timestamptz WHERE id".update.run(), Unused.Why.Held)

  /** Ensure again at the next tick (a plan created sub-goals, a goal was added). */
  def ensureSoon()(using DbCon): Unit =
    made()
    Unused(sql"UPDATE agent_autopilot SET last_ensure_at = NULL WHERE id".update.run(), Unused.Why.Held)

  def backedUp(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    made()
    Unused(sql"UPDATE agent_autopilot SET last_backup_at = $t::timestamptz WHERE id".update.run(), Unused.Why.Held)

  def linted(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    made()
    Unused(sql"UPDATE agent_autopilot SET last_lint_at = $t::timestamptz WHERE id".update.run(), Unused.Why.Held)

  /** A paused manager the autopilot resumed (it no longer counts as one it paused). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def unpaused(managerId: Managers.ManagerInfo.Id)(using DbCon): Unit =
    made()
    Unused(
      sql"UPDATE agent_autopilot SET paused = array_remove(paused, $managerId) WHERE id".update.run(),
      Unused.Why.Held
    )

  /** The scheduler's strategy a person chose (V100): (name, by, at); None = agents.scheduler.strategy. */
  def strategy()(using DbCon): Option[(StrategyName, Row.ChangedBy, Row.ChangedAt)] =
    sql"""SELECT scheduler_strategy, coalesce(scheduler_by, ''), coalesce(${Timestamps.isoSeconds(
        sql"scheduler_at"
      )}, '')
                FROM agent_autopilot WHERE id AND scheduler_strategy IS NOT NULL"""
      .query[(StrategyName, Row.ChangedBy, Row.ChangedAt)]
      .run()
      .headOption

  /** Set (Some) or clear (None: back to the config's) the scheduler's strategy. */
  def setStrategy(name: Option[StrategyName], by: Row.ChangedBy)(using DbCon): Unit =
    made()
    Unused(
      sql"UPDATE agent_autopilot SET scheduler_strategy = $name, scheduler_by = $by, scheduler_at = now() WHERE id".update
        .run(),
      Unused.Why.Held
    )

  /** When the daily approval digest was last written (V101), if ever. */
  def lastDigest()(using DbCon): Option[Digest] =
    sql"SELECT ${Timestamps.isoSeconds(sql"last_digest_at")} FROM agent_autopilot WHERE id"
      .query[Option[Digest]]
      .run()
      .headOption
      .flatten

  /** The digest was written at `now`. */
  def digested(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    made()
    Unused(sql"UPDATE agent_autopilot SET last_digest_at = $t::timestamptz WHERE id".update.run(), Unused.Why.Held)

  /** Draining (V102): since when, by whom, and when it was first seen drained; None = not draining. */
  def drain()(using DbCon): Option[(DrainSince, Row.ChangedBy, Option[DrainedAt])] =
    sql"""SELECT ${Timestamps.isoSeconds(sql"drain_since")}, coalesce(drain_by, ''), ${Timestamps.isoSeconds(
        sql"drained_at"
      )}
                FROM agent_autopilot WHERE id AND drain_since IS NOT NULL"""
      .query[(DrainSince, Row.ChangedBy, Option[DrainedAt])]
      .run()
      .headOption

  /** Start draining (true; a drain under way keeps its start) or stop (false). True = it changed. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def setDrain(on: Boolean, by: Row.ChangedBy)(using DbCon): Boolean =
    made()
    if on then sql"""UPDATE agent_autopilot SET drain_since = now(), drain_by = $by, drained_at = NULL
                     WHERE id AND drain_since IS NULL""".update.run() > 0
    else sql"""UPDATE agent_autopilot SET drain_since = NULL, drain_by = NULL, drained_at = NULL
                WHERE id AND drain_since IS NOT NULL""".update.run() > 0

  /** The drain was first seen drained at `now` (kept until it is lifted or work runs again: `None`). */
  def drained(at: Option[java.time.Instant])(using DbCon): Unit =
    val t = at.map(_.toString)
    made()
    Unused(
      sql"UPDATE agent_autopilot SET drained_at = $t::timestamptz WHERE id AND drain_since IS NOT NULL".update.run(),
      Unused.Why.Idempotent
    )
