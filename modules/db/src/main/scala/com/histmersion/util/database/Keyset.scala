package com.histmersion
package util.database
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.Paging
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Keyset paging of a newest-first list (`core.Paging`): rows ordered by `created_at DESC, id DESC`, a cursor being the
  * id of a row of the same table. The cursor's `created_at` is looked up by a row subquery, so the comparison is at the
  * column's full precision and the order is total. Indexed by `(…, created_at, id)` (V086); Postgres walks those
  * backwards for the descending order.
  */
@atBoundary(BoundaryKind.Sql, "builds the keyset-paging SQL for newest-first lists")
private[histmersion] object Keyset:
  type State = State.T
  object State extends Newtype[String, Pure]

  /** A paged table as the page's query names it: `name` (`jobs`), its `alias` in the `FROM` (`j`, or `sql""`) and the
    * `prefix` its columns take (`j.`, or `sql""`).
    */
  final case class Table(name: Frag, alias: Frag, prefix: Frag)

  object Table:
    /** A table the query does not alias. */
    def apply(name: Frag): Table = Table(name, sql"", sql"")

  /** The order every paged list uses. `alias` = the table's column prefix in the query (with its dot). */
  @tolerated(LintRule.TotalOrder, "created_at, then id, the key, behind the caller's alias")
  def orderBy(alias: Frag): Frag =
    sql" ORDER BY ${alias}created_at DESC, ${alias}id DESC"

  /** ` AND (created_at, id) < (that row's)` — the rows older than the cursor (an id, bound through its codec). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def olderThan[C](table: Table, cursor: C)(using DbCodec[C]): Frag =
    sql" AND (${table.prefix}created_at, ${table.prefix}id) < (SELECT created_at, id FROM ${table.name} WHERE id = $cursor)"

  /** ` AND (created_at, id) >= (that row's)` — the rows from the newest down to the cursor, itself included.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def atOrNewer[C](table: Table, cursor: C)(using DbCodec[C]): Frag =
    sql" AND (${table.prefix}created_at, ${table.prefix}id) >= (SELECT created_at, id FROM ${table.name} WHERE id = $cursor)"

  /** Is there a row with this id? A cursor naming no row would silently give an empty page. */
  def existsOn[C](table: Frag, cursor: C)(using DbCodec[C], DbCon): Boolean =
    sql"SELECT 1 FROM $table WHERE id = $cursor".query[Int].run().nonEmpty

  /** `SELECT <state>, count(*) … GROUP BY <state>`, largest first then by name. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def countsOn(from: Frag, stateCol: Frag)(using DbCon): List[(State, RowCount)] =
    sql"SELECT $stateCol, count(*)::int $from GROUP BY $stateCol"
      .query[(State, RowCount)]
      .run()
      .toList
      .sortBy((s, n) => (-n.value, s.value))

  /** One page: `limit + 1` rows fetched (the extra one only says "there is more"). With `upTo` the page runs down to
    * that row; whether anything is older than the page then takes one indexed probe. `select` = the projection and its
    * `FROM` (the table under its alias, plus any joins); `where` the filters, starting ` WHERE` (` WHERE true` for
    * none).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def pageOn[R, C, K](
      select: Frag,
      where: Frag,
      table: Table,
      limit: PageSize,
      before: Option[C],
      upTo: Option[C],
      id: R => K
  )(using DbCodec[R], DbCodec[C], DbCodec[K], DbCon): (Vector[R], Boolean) =
    val bounds = sql"${before.fold(sql"")(olderThan(table, _))}${upTo.fold(sql"")(atOrNewer(table, _))}"
    val rows =
      sql"$select$where$bounds${orderBy(table.prefix)} LIMIT ${math.min(limit.value, Int.MaxValue - 1) + 1}"
        .query[R]
        .run()
    val (shown, more) = Paging.cut(rows, Paging.Limit(limit.value))
    if more || upTo.isEmpty then (shown, more)
    else
      val older = shown.lastOption.exists { last =>
        sql"SELECT 1 FROM ${table.name} ${table.alias}$where${olderThan(table, id(last))} LIMIT 1"
          .query[Int]
          .run()
          .nonEmpty
      }
      (shown, older)
