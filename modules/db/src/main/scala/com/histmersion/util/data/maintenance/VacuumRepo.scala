package com.histmersion.util.data.maintenance

import com.histmersion.api.maintenance.Vacuum

import com.augustnagro.magnum.*

/** `VACUUM` for `histmersion maintenance vacuum`. VACUUM cannot run inside a transaction block, so it runs on a
  * connection in autocommit mode — never in a transaction.
  */
object VacuumRepo:

  /** One table's dead row versions (the statistics collector's count) and its size with indexes and TOAST.
    */
  final case class TableState(deadRows: TableState.DeadRows, bytes: TableState.Bytes)

  object TableState:
    type DeadRows = DeadRows.T
    object DeadRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The ordinary tables of the `public` schema — what a vacuum may name. */
  def tables(using DbCon): Set[String] =
    sql"SELECT tablename FROM pg_tables WHERE schemaname = 'public'".query[String].run().toSet

  def state(table: String)(using DbCon): TableState =
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
  def vacuum(table: String, analyze: Boolean)(using con: DbCon): Unit =
    val c = con.connection
    val st = c.createStatement()
    try { val _ = st.execute(Vacuum.statement(table, analyze)) }
    finally st.close()
