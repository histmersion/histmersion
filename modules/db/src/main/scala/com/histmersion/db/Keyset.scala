package com.histmersion.db

import com.augustnagro.magnum.*
import com.histmersion.api.Paging
import javax.sql.DataSource

/** Keyset paging of a newest-first list (`core.Paging`): rows ordered by `created_at DESC, id DESC`, a cursor being the
  * id of a row of the same table. The cursor's `created_at` is looked up by a row subquery, so the comparison is at the
  * column's full precision and the order is total. Indexed by `(…, created_at, id)` (V086); Postgres walks those
  * backwards for the descending order.
  */
private[histmersion] object Keyset:

  /** The order every paged list uses. `alias` = the table's alias in the query (with its dot). */
  def orderBy(alias: String): Sql = Sql.raw(s" ORDER BY ${alias}created_at DESC, ${alias}id DESC")

  /** ` AND (created_at, id) < (that row's)` — the rows older than the cursor. */
  def olderThan(alias: String, table: String, cursor: Param): Sql =
    Sql.of(s" AND (${alias}created_at, ${alias}id) < (SELECT created_at, id FROM $table WHERE id = ?)", cursor)

  /** ` AND (created_at, id) >= (that row's)` — the rows from the newest down to the cursor, itself included.
    */
  def atOrNewer(alias: String, table: String, cursor: Param): Sql =
    Sql.of(s" AND (${alias}created_at, ${alias}id) >= (SELECT created_at, id FROM $table WHERE id = ?)", cursor)

  /** Is there a row with this id? A cursor naming no row would silently give an empty page. */
  def exists(ds: DataSource, table: String, cursor: Param): Boolean = connect(ds)(existsOn(table, cursor))

  /** [[exists]] on the caller's connection. */
  def existsOn(table: String, cursor: Param)(using DbCon): Boolean =
    Sql.of(s"SELECT 1 FROM $table WHERE id = ?", cursor).frag.query[Int].run().nonEmpty

  /** `SELECT <state>, count(*) … GROUP BY <state>`, largest first then by name. */
  def counts(ds: DataSource, from: Sql, stateCol: String): List[(String, Int)] = connect(ds)(countsOn(from, stateCol))

  /** [[counts]] on the caller's connection. */
  def countsOn(from: Sql, stateCol: String)(using DbCon): List[(String, Int)] =
    (Sql.raw(s"SELECT $stateCol, count(*)::int ") ++ from ++ Sql
      .raw(s" GROUP BY $stateCol")).frag.query[(String, Int)].run().toList.sortBy((s, n) => (-n, s))

  /** One page: `limit + 1` rows fetched (the extra one only says "there is more"). With `upTo` the page runs down to
    * that row; whether anything is older than the page then takes one indexed probe. `select` = the projection and its
    * `FROM` (the table under `alias`, plus any joins); `where` the filters, starting ` WHERE` (` WHERE true` for none).
    */
  def page[R](
      ds: DataSource,
      select: Sql,
      where: Sql,
      alias: String,
      table: String,
      limit: Int,
      before: Option[Param],
      upTo: Option[Param],
      id: R => Param
  )(using DbCodec[R]): (Vector[R], Boolean) = connect(ds)(pageOn(select, where, alias, table, limit, before, upTo, id))

  /** [[page]] on the caller's connection. */
  def pageOn[R](
      select: Sql,
      where: Sql,
      alias: String,
      table: String,
      limit: Int,
      before: Option[Param],
      upTo: Option[Param],
      id: R => Param
  )(using DbCodec[R], DbCon): (Vector[R], Boolean) =
    val bounds = before.fold(Sql.empty)(olderThan(alias, table, _)) ++ upTo.fold(Sql.empty)(atOrNewer(alias, table, _))
    val rows =
      (select ++ where ++ bounds ++ orderBy(alias) ++ Sql.of(
        " LIMIT ?",
        Param.I(math.min(limit, Int.MaxValue - 1) + 1)
      )).frag.query[R].run()
    val (shown, more) = Paging.cut(rows, limit)
    if more || upTo.isEmpty then (shown, more)
    else
      val older = shown.lastOption.exists { last =>
        (Sql.raw(s"SELECT 1 FROM $table ${alias.stripSuffix(".")}") ++ where ++
          olderThan(alias, table, id(last)) ++ Sql.raw(" LIMIT 1")).frag.query[Int].run().nonEmpty
      }
      (shown, older)
