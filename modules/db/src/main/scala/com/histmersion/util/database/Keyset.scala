package com.histmersion
package util.database
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.Paging
import io.github.iltotore.iron.constraint.any.Pure

/** Keyset paging of a newest-first list (`core.Paging`): rows ordered by `created_at DESC, id DESC`, a cursor being the
  * id of a row of the same table. The cursor's `created_at` is looked up by a row subquery, so the comparison is at the
  * column's full precision and the order is total. Indexed by `(…, created_at, id)` (V086); Postgres walks those
  * backwards for the descending order.
  */
private[histmersion] object Keyset:
  type State = State.T
  object State extends Newtype[String, Pure]

  /** The order every paged list uses. `alias` = the table's alias in the query (with its dot). */

  def orderBy(at: String): Frag =
    val alias = SqlLiteral(at)
    sql" ORDER BY ${alias}created_at DESC, ${alias}id DESC"

  /** ` AND (created_at, id) < (that row's)` — the rows older than the cursor (an id, bound through its codec). */
  def olderThan[C](alias: String, table: String, cursor: C)(using DbCodec[C]): Frag =
    sql" AND (${SqlLiteral(alias)}created_at, ${SqlLiteral(alias)}id) < (SELECT created_at, id FROM ${SqlLiteral(table)} WHERE id = $cursor)"

  /** ` AND (created_at, id) >= (that row's)` — the rows from the newest down to the cursor, itself included.
    */
  def atOrNewer[C](alias: String, table: String, cursor: C)(using DbCodec[C]): Frag =
    sql" AND (${SqlLiteral(alias)}created_at, ${SqlLiteral(alias)}id) >= (SELECT created_at, id FROM ${SqlLiteral(table)} WHERE id = $cursor)"

  /** Is there a row with this id? A cursor naming no row would silently give an empty page. */
  def existsOn[C](table: String, cursor: C)(using DbCodec[C], DbCon): Boolean =
    sql"SELECT 1 FROM ${SqlLiteral(table)} WHERE id = $cursor".query[Int].run().nonEmpty

  /** `SELECT <state>, count(*) … GROUP BY <state>`, largest first then by name. */
  def countsOn(from: Frag, stateCol: String)(using DbCon): List[(State, RowCount)] =
    sql"SELECT ${SqlLiteral(stateCol)}, count(*)::int $from GROUP BY ${SqlLiteral(stateCol)}"
      .query[(State, RowCount)]
      .run()
      .toList
      .sortBy((s, n) => (-n.value, s.value))

  /** One page: `limit + 1` rows fetched (the extra one only says "there is more"). With `upTo` the page runs down to
    * that row; whether anything is older than the page then takes one indexed probe. `select` = the projection and its
    * `FROM` (the table under `alias`, plus any joins); `where` the filters, starting ` WHERE` (` WHERE true` for none).
    */
  def pageOn[R, C, K](
      select: Frag,
      where: Frag,
      alias: String,
      table: String,
      limit: PageSize,
      before: Option[C],
      upTo: Option[C],
      id: R => K
  )(using DbCodec[R], DbCodec[C], DbCodec[K], DbCon): (Vector[R], Boolean) =
    val bounds = sql"${before.fold(sql"")(olderThan(alias, table, _))}${upTo.fold(sql"")(atOrNewer(alias, table, _))}"
    val rows =
      sql"$select$where$bounds${orderBy(alias)} LIMIT ${math.min(limit.value, Int.MaxValue - 1) + 1}".query[R].run()
    val (shown, more) = Paging.cut(rows, Paging.Limit(limit.value))
    if more || upTo.isEmpty then (shown, more)
    else
      val older = shown.lastOption.exists { last =>
        sql"SELECT 1 FROM ${SqlLiteral(table)} ${SqlLiteral(alias.stripSuffix("."))}$where${olderThan(alias, table, id(last))} LIMIT 1"
          .query[Int]
          .run()
          .nonEmpty
      }
      (shown, older)
