package com.histmersion.util.data.maintenance

import com.histmersion.api.maintenance.Vacuum

import com.augustnagro.magnum.*

/** `VACUUM` for `histmersion maintenance vacuum`. VACUUM cannot run inside a transaction block, so it runs on a
  * connection in autocommit mode — never in a transaction.
  */
object VacuumRepo:

  /** One table's dead row versions (the statistics collector's count) and its size with indexes and TOAST.
    */
  type TablesResult = TablesResult.T
  object TablesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateTable = StateTable.T
  object StateTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VacuumTable = VacuumTable.T
  object VacuumTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VacuumAnalyze = VacuumAnalyze.T
  object VacuumAnalyze extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class TableState(deadRows: TableState.DeadRows, bytes: TableState.Bytes)

  object TableState:
    type DeadRows = DeadRows.T
    object DeadRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The ordinary tables of the `public` schema — what a vacuum may name. */
  def tables(using DbCon): Set[com.histmersion.util.data.maintenance.VacuumRepo.TablesResult] =
    sql"SELECT tablename FROM pg_tables WHERE schemaname = 'public'".query[String].run().toSet

  def state(table: com.histmersion.util.data.maintenance.VacuumRepo.StateTable)(using DbCon): TableState =
    sql"""SELECT coalesce((SELECT n_dead_tup FROM pg_stat_user_tables
                           WHERE schemaname = 'public' AND relname = $table), 0)::int8,
                 pg_total_relation_size(format('public.%I', $table::text)::regclass)::int8"""
      .query[(Long, Long)]
      .run()
      .headOption
      .map(TableState.apply)
      .getOrElse(TableState(0, 0))

  /** Run `VACUUM [(ANALYZE)]` on one table, on a connection outside any transaction. The name must have passed
    * [[Vacuum.plan]] (shape and existence).
    */
  def vacuum(table: com.histmersion.util.data.maintenance.VacuumRepo.VacuumTable, analyze: com.histmersion.util.data.maintenance.VacuumRepo.VacuumAnalyze)(using con: DbCon): Unit =
    val c = con.connection
    val st = c.createStatement()
    try { val _ = st.execute(Vacuum.statement(table, analyze)) }
    finally st.close()
