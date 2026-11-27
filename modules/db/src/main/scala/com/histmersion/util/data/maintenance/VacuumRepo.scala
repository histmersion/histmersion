package com.histmersion
package util.data.maintenance

import com.histmersion.api.maintenance.Vacuum

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `VACUUM` for `histmersion maintenance vacuum`. VACUUM cannot run inside a transaction block, so it runs on a
  * connection in autocommit mode — never in a transaction.
  */
@atBoundary(BoundaryKind.Sql, "runs vacuum and reads table maintenance state")
object VacuumRepo:
  type Analyze = Analyze.T
  object Analyze extends Newtype[Boolean, Pure]

  /** One table's dead row versions (the statistics collector's count) and its size with indexes and TOAST.
    */

  final case class TableState(deadRows: TableState.DeadRows, bytes: TableState.Bytes)

  object TableState:
    type DeadRows = DeadRows.T
    object DeadRows extends Newtype[Long, Pure]
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Bytes = ByteCount
    val Bytes: ByteCount.type = ByteCount

  /** The ordinary tables of the `public` schema — what a vacuum may name. */
  def tables(using DbCon): Set[String] =
    sql"SELECT tablename FROM pg_tables WHERE schemaname = 'public'".query[String].run().toSet

  def state(table: String)(using DbCon): TableState =
    sql"""SELECT coalesce((SELECT n_dead_tup FROM pg_stat_user_tables
                           WHERE schemaname = 'public' AND relname = $table), 0)::int8,
                 pg_total_relation_size(format('public.%I', $table::text)::regclass)::int8"""
      .query[(TableState.DeadRows, TableState.Bytes)]
      .run()
      .headOption
      .map(TableState.apply)
      .getOrElse(TableState(TableState.DeadRows(0), TableState.Bytes(0)))

  /** Run `VACUUM [(ANALYZE)]` on one table, on a connection outside any transaction. The name must have passed
    * [[Vacuum.plan]] (shape and existence).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def vacuum(table: String, analyze: Analyze)(using con: DbCon): Unit =
    val c = con.connection
    val st = c.createStatement()
    try { val _ = st.execute(Vacuum.statement(Vacuum.Table(table), Vacuum.Request.Analyze(analyze.value))) }
    finally st.close()
