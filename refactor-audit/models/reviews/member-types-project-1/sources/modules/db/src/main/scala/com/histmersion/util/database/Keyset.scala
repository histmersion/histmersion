package com.histmersion.util.database
import com.augustnagro.magnum.*
import com.histmersion.api.Paging

/** Keyset paging of a newest-first list (`core.Paging`): rows ordered by `created_at DESC, id DESC`, a cursor being the
  * id of a row of the same table. The cursor's `created_at` is looked up by a row subquery, so the comparison is at the
  * column's full precision and the order is total. Indexed by `(…, created_at, id)` (V086); Postgres walks those
  * backwards for the descending order.
  */
private[histmersion] object Keyset:

  /** The order every paged list uses. `alias` = the table's alias in the query (with its dot). */
  type OrderByAlias = OrderByAlias.T
  object OrderByAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderThanAlias = OlderThanAlias.T
  object OlderThanAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderThanTable = OlderThanTable.T
  object OlderThanTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AtOrNewerAlias = AtOrNewerAlias.T
  object AtOrNewerAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AtOrNewerTable = AtOrNewerTable.T
  object AtOrNewerTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistsOnResult = ExistsOnResult.T
  object ExistsOnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistsOnTable = ExistsOnTable.T
  object ExistsOnTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsOnResultPart1 = CountsOnResultPart1.T
  object CountsOnResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsOnResultPart2 = CountsOnResultPart2.T
  object CountsOnResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsOnStateCol = CountsOnStateCol.T
  object CountsOnStateCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageOnResultPart2 = PageOnResultPart2.T
  object PageOnResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageOnAlias = PageOnAlias.T
  object PageOnAlias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageOnTable = PageOnTable.T
  object PageOnTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageOnLimit = PageOnLimit.T
  object PageOnLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def orderBy(alias: com.histmersion.util.database.Keyset.OrderByAlias): Sql = Sql.raw(s" ORDER BY ${alias}created_at DESC, ${alias}id DESC")

  /** ` AND (created_at, id) < (that row's)` — the rows older than the cursor. */
  def olderThan(alias: com.histmersion.util.database.Keyset.OlderThanAlias, table: com.histmersion.util.database.Keyset.OlderThanTable, cursor: Param): Sql =
    Sql.of(s" AND (${alias}created_at, ${alias}id) < (SELECT created_at, id FROM $table WHERE id = ?)", cursor)

  /** ` AND (created_at, id) >= (that row's)` — the rows from the newest down to the cursor, itself included.
    */
  def atOrNewer(alias: com.histmersion.util.database.Keyset.AtOrNewerAlias, table: com.histmersion.util.database.Keyset.AtOrNewerTable, cursor: Param): Sql =
    Sql.of(s" AND (${alias}created_at, ${alias}id) >= (SELECT created_at, id FROM $table WHERE id = ?)", cursor)

  /** Is there a row with this id? A cursor naming no row would silently give an empty page. */
  def existsOn(table: com.histmersion.util.database.Keyset.ExistsOnTable, cursor: Param)(using DbCon): com.histmersion.util.database.Keyset.ExistsOnResult =
    Sql.of(s"SELECT 1 FROM $table WHERE id = ?", cursor).frag.query[Int].run().nonEmpty

  /** `SELECT <state>, count(*) … GROUP BY <state>`, largest first then by name. */
  def countsOn(from: Sql, stateCol: com.histmersion.util.database.Keyset.CountsOnStateCol)(using DbCon): List[(com.histmersion.util.database.Keyset.CountsOnResultPart1, com.histmersion.util.database.Keyset.CountsOnResultPart2)] =
    (Sql.raw(s"SELECT $stateCol, count(*)::int ") ++ from ++ Sql
      .raw(s" GROUP BY $stateCol")).frag.query[(String, Int)].run().toList.sortBy((s, n) => (-n, s))

  /** One page: `limit + 1` rows fetched (the extra one only says "there is more"). With `upTo` the page runs down to
    * that row; whether anything is older than the page then takes one indexed probe. `select` = the projection and its
    * `FROM` (the table under `alias`, plus any joins); `where` the filters, starting ` WHERE` (` WHERE true` for none).
    */
  def pageOn[R](
      select: Sql,
      where: Sql,
      alias: com.histmersion.util.database.Keyset.PageOnAlias,
      table: com.histmersion.util.database.Keyset.PageOnTable,
      limit: com.histmersion.util.database.Keyset.PageOnLimit,
      before: Option[Param],
      upTo: Option[Param],
      id: R => Param
  )(using DbCodec[R], DbCon): (Vector[R], com.histmersion.util.database.Keyset.PageOnResultPart2) =
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
