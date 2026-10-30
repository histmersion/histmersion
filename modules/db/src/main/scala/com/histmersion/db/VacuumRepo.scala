package com.histmersion.db

import com.histmersion.api.Vacuum

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** `VACUUM` for `histmersion maintenance vacuum` (#1445). VACUUM cannot run inside a transaction block, so it gets a
  * connection of its own in autocommit mode — never `transact`.
  */
object VacuumRepo:

  /** One table's dead row versions (the statistics collector's count) and its size with indexes and TOAST.
    */
  final case class TableState(deadRows: Long, bytes: Long)

  /** The ordinary tables of the `public` schema — what a vacuum may name. */
  def tables(ds: DataSource): Set[String] =
    connect(ds):
      sql"SELECT tablename FROM pg_tables WHERE schemaname = 'public'".query[String].run().toSet

  def state(ds: DataSource, table: String): TableState =
    connect(ds):
      sql"""SELECT coalesce((SELECT n_dead_tup FROM pg_stat_user_tables
                             WHERE schemaname = 'public' AND relname = $table), 0)::int8,
                   pg_total_relation_size(format('public.%I', $table::text)::regclass)::int8"""
        .query[(Long, Long)]
        .run()
        .headOption
        .map(TableState.apply)
        .getOrElse(TableState(0, 0))

  /** Run `VACUUM [(ANALYZE)]` on one table, outside any transaction. The name must have passed [[Vacuum.plan]] (shape
    * and existence).
    */
  def vacuum(ds: DataSource, table: String, analyze: Boolean): Unit =
    val c = ds.getConnection()
    try
      c.setAutoCommit(true)
      val st = c.createStatement()
      try { val _ = st.execute(Vacuum.statement(table, analyze)) }
      finally st.close()
    finally c.close()
