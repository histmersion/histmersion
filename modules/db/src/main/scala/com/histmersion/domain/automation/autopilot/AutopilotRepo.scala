package com.histmersion.domain.automation.autopilot
import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql
import com.histmersion.util.database.given

/** The autopilot's one row (V096, docs/research-process-managers.md "As built: the autopilot"): the switch, who turned
  * it and when, the managers it paused when it was turned off, and when it last ensured the goals' managers and ran its
  * housekeeping. Every statement runs on the connection it is given (`AutopilotRowService` opens it).
  */
object AutopilotRepo:
  type Digest = Digest.T
  object Digest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategyName = StrategyName.T
  object StrategyName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainSince = DrainSince.T
  object DrainSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainedAt = DrainedAt.T
  object DrainedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
  private def at(col: String) = s"to_char($col AT TIME ZONE 'UTC', $ts)"

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
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangedAt = ChangedAt.T
    object ChangedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangedBy = ChangedBy.T
    object ChangedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Paused = Paused.T
    object Paused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastEnsureAt = LastEnsureAt.T
    object LastEnsureAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastBackupAt = LastBackupAt.T
    object LastBackupAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastLintAt = LastLintAt.T
    object LastLintAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The row, made first if it is not there yet. */
  def get()(using DbCon): Row =
    val _ = sql"INSERT INTO agent_autopilot (id) VALUES (true) ON CONFLICT DO NOTHING".update.run()
    Sql
      .raw(Sql.RawText(s"""SELECT enabled, ${at("changed_at")}, changed_by, paused, ${at("last_ensure_at")},
                       ${at("last_backup_at")}, ${at("last_lint_at")} FROM agent_autopilot WHERE id"""))
      .frag
      .query[Row]
      .run()
      .head

  /** Switch it; `paused` = the managers it paused (turning it off) or none (turning it on). True = it changed (it was
    * the other way).
    */
  def set(enabled: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Enabled, by: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedBy, paused: List[com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Paused])(using DbCon): Boolean =
    val _ = get()
    sql"""UPDATE agent_autopilot SET enabled = $enabled, changed_at = now(), changed_by = $by, paused = $paused::text[],
                                     last_ensure_at = NULL
          WHERE id AND enabled <> $enabled""".update.run() > 0

  /** It made sure of the goals' managers now. */
  def ensured(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    val _ = sql"UPDATE agent_autopilot SET last_ensure_at = $t::timestamptz WHERE id".update.run()

  /** Ensure again at the next tick (a plan created sub-goals, a goal was added). */
  def ensureSoon()(using DbCon): Unit =
    val _ = sql"UPDATE agent_autopilot SET last_ensure_at = NULL WHERE id".update.run()

  def backedUp(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    val _ = sql"UPDATE agent_autopilot SET last_backup_at = $t::timestamptz WHERE id".update.run()

  def linted(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    val _ = sql"UPDATE agent_autopilot SET last_lint_at = $t::timestamptz WHERE id".update.run()

  /** A paused manager the autopilot resumed (it no longer counts as one it paused). */
  def unpaused(managerId: com.histmersion.domain.automation.agents.AgentRepo.RunRow.ManagerId)(using DbCon): Unit =
    val _ = sql"UPDATE agent_autopilot SET paused = array_remove(paused, $managerId) WHERE id".update.run()

  /** The scheduler's strategy a person chose (V100): (name, by, at); None = agents.scheduler.strategy. */
  def strategy()(using DbCon): Option[(com.histmersion.domain.automation.autopilot.AutopilotRepo.StrategyName, com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedBy, com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedAt)] =
    val _ = get()
    Sql
      .raw(Sql.RawText(s"""SELECT scheduler_strategy, coalesce(scheduler_by, ''), coalesce(${at("scheduler_at")}, '')
                FROM agent_autopilot WHERE id AND scheduler_strategy IS NOT NULL"""))
      .frag
      .query[(StrategyName, Row.ChangedBy, Row.ChangedAt)]
      .run()
      .headOption

  /** Set (Some) or clear (None: back to the config's) the scheduler's strategy. */
  def setStrategy(name: Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.StrategyName], by: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedBy)(using DbCon): Unit =
    val _ = get()
    val _ =
      sql"""UPDATE agent_autopilot SET scheduler_strategy = $name, scheduler_by = $by, scheduler_at = now() WHERE id""".update
        .run()

  /** When the daily approval digest was last written (V101), if ever. */
  def lastDigest()(using DbCon): Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.Digest] =
    val _ = get()
    Sql.raw(Sql.RawText(s"SELECT ${at("last_digest_at")} FROM agent_autopilot WHERE id")).frag.query[Option[Digest]].run().head

  /** The digest was written at `now`. */
  def digested(now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    val _ = sql"UPDATE agent_autopilot SET last_digest_at = $t::timestamptz WHERE id".update.run()

  /** Draining (V102): since when, by whom, and when it was first seen drained; None = not draining. */
  def drain()(using DbCon): Option[(DrainSince, Row.ChangedBy, Option[DrainedAt])] =
    val _ = get()
    Sql
      .raw(Sql.RawText(s"""SELECT ${at("drain_since")}, coalesce(drain_by, ''), ${at("drained_at")}
                FROM agent_autopilot WHERE id AND drain_since IS NOT NULL"""))
      .frag
      .query[(DrainSince, Row.ChangedBy, Option[DrainedAt])]
      .run()
      .headOption

  /** Start draining (true; a drain under way keeps its start) or stop (false). True = it changed. */
  def setDrain(on: Boolean, by: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedBy)(using DbCon): Boolean =
    val _ = get()
    if on then sql"""UPDATE agent_autopilot SET drain_since = now(), drain_by = $by, drained_at = NULL
                     WHERE id AND drain_since IS NULL""".update.run() > 0
    else sql"""UPDATE agent_autopilot SET drain_since = NULL, drain_by = NULL, drained_at = NULL
                WHERE id AND drain_since IS NOT NULL""".update.run() > 0

  /** The drain was first seen drained at `now` (kept until it is lifted or work runs again: `None`). */
  def drained(at: Option[java.time.Instant])(using DbCon): Unit =
    val t = at.map(_.toString)
    val _ =
      sql"UPDATE agent_autopilot SET drained_at = $t::timestamptz WHERE id AND drain_since IS NOT NULL".update.run()
