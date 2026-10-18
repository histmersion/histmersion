package com.histmersion.db

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The autopilot's one row (V096, docs/research-process-managers.md "As built: the autopilot"): the
  * switch, who turned it and when, the managers it paused when it was turned off, and when it last
  * ensured the goals' managers and ran its housekeeping. */
object AutopilotRepo:

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
  private def at(col: String) = s"to_char($col AT TIME ZONE 'UTC', $ts)"

  /** The row as read. Times are ISO instants (UTC). */
  final case class Row(enabled: Boolean, changedAt: String, changedBy: String, paused: List[String],
      lastEnsureAt: Option[String], lastBackupAt: Option[String], lastLintAt: Option[String])
  private given DbCodec[Row] = DbCodec.derived

  def get(ds: DataSource): Row =
    connect(ds):
      sql"INSERT INTO agent_autopilot (id) VALUES (true) ON CONFLICT DO NOTHING".update.run()
      Sql.raw(s"""SELECT enabled, ${at("changed_at")}, changed_by, paused, ${at("last_ensure_at")},
                         ${at("last_backup_at")}, ${at("last_lint_at")} FROM agent_autopilot WHERE id""")
        .frag.query[Row].run().head

  /** Switch it; `paused` = the managers it paused (turning it off) or none (turning it on). True = it
    * changed (it was the other way). */
  def set(ds: DataSource, enabled: Boolean, by: String, paused: List[String]): Boolean =
    val _ = get(ds)
    transact(ds):
      sql"""UPDATE agent_autopilot SET enabled = $enabled, changed_at = now(), changed_by = $by, paused = $paused::text[],
                                       last_ensure_at = NULL
            WHERE id AND enabled <> $enabled""".update.run() > 0

  /** It made sure of the goals' managers now. */
  def ensured(ds: DataSource, now: java.time.Instant): Unit =
    val t = now.toString
    val _ = transact(ds)(sql"UPDATE agent_autopilot SET last_ensure_at = $t::timestamptz WHERE id".update.run())

  /** Ensure again at the next tick (a plan created sub-goals, a goal was added). */
  def ensureSoon(ds: DataSource): Unit =
    val _ = transact(ds)(sql"UPDATE agent_autopilot SET last_ensure_at = NULL WHERE id".update.run())

  def backedUp(ds: DataSource, now: java.time.Instant): Unit =
    val t = now.toString
    val _ = transact(ds)(sql"UPDATE agent_autopilot SET last_backup_at = $t::timestamptz WHERE id".update.run())

  def linted(ds: DataSource, now: java.time.Instant): Unit =
    val t = now.toString
    val _ = transact(ds)(sql"UPDATE agent_autopilot SET last_lint_at = $t::timestamptz WHERE id".update.run())

  /** A paused manager the autopilot resumed (it no longer counts as one it paused). */
  def unpaused(ds: DataSource, managerId: String): Unit =
    val _ = transact(ds)(sql"UPDATE agent_autopilot SET paused = array_remove(paused, $managerId) WHERE id".update.run())
