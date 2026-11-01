package com.histmersion.db

import com.histmersion.util.database.RangeSql

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Tree.{RegionNode, TreeRow}

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The ROWS behind the §1.6 aggregation tree. The database selects; [[com.histmersion.api.Tree]] (pure) buckets and
  * nests — the same split as [[AggregateRepo]], and for the same reason: adaptive bucket sizes, the as-of hierarchy
  * walk and the non-additive counting are all unit-testable without Postgres, and the strip, the CLI tree and the web
  * UI tabs cannot drift apart.
  *
  * Everything here is built from the SHARED [[FilterSql]] fragments and takes the [[PlaceScope]] the handler resolved
  * once per request, so a `place=` chip narrows the tree exactly as it narrows the sidebar.
  */
object TreeRepo:

  /** A closed [[Period]] from the two bounds SQL returned (`lower`, `upper - 1`); `None` unless both are present — an
    * int8range with an open end is "always", which is what `None` means here.
    */
  private def periodOf(start: Option[Long], end: Option[Long]): Option[Period] =
    (start, end) match
      case (Some(a), Some(b)) => Some(if a <= b then Period(a, b) else Period(b, a))
      case _                  => None

  /** One row as SQL returns it — the place ids arrive as a `text[]`. */
  final private[db] case class TreeRowRaw(
      key: String,
      citekey: String,
      title: String,
      periodStart: Option[Long],
      periodEnd: Option[Long],
      placeIds: List[String]
  ) derives DbCodec:
    def toRow: TreeRow = TreeRow(key, citekey, title, periodStart, periodEnd, placeIds.distinct)

  final private[db] case class RegionNodeRow(
      id: String,
      label: String,
      kind: String,
      periodStart: Option[Long],
      periodEnd: Option[Long],
      contested: Boolean,
      withdrawn: Boolean
  ) derives DbCodec:
    def toNode: RegionNode =
      RegionNode(id, label, kind, periodOf(periodStart, periodEnd), contested, withdrawn)

  final private[db] case class RelationRow(
      childId: String,
      parentId: String,
      kind: String,
      periodStart: Option[Long],
      periodEnd: Option[Long]
  ) derives DbCodec:
    def toRelation: PlaceRelation =
      PlaceRelation(childId, parentId, kind, periodOf(periodStart, periodEnd))

  // ── the counted units ────────────────────────────────────────────────────────────────────────

  /** The gazetteer places one FINDING was materialised into, as an array (correlated on `fa`). */
  private def placesOf(fa: String): String =
    s"ARRAY(SELECT fp.place_id FROM finding_places fp WHERE fp.finding_id = $fa.id ORDER BY fp.place_id)"

  /** The SQL behind [[rows]] — a **pure** value, so the predicate can be asserted on without a database (plan
    * §1.0/§2.2).
    *
    *   - `unit = "findings"` → one row per matching finding: its own period and its own places.
    *   - `unit = "sources"` → one row per matching SOURCE (the unit of every result, §1.0): its own period, falling
    *     back to the envelope of its matching findings when it is undated, and the UNION of those findings' places. The
    *     window/gate logic mirrors [[AggregateRepo.periodsQuery]] exactly, so the tree and the timeline strip always
    *     agree about which sources are in scope.
    *
    * Ordering is the caller's `sort=`, which is what makes `samples` under a leaf the *top* sources for that bucket
    * rather than an arbitrary few.
    */
  def rowsQuery(unit: String, filter: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    if unit == "findings" then
      Sql.raw(
        s"SELECT f.id, f.citekey, s.title, ${RangeSql.cols("f.period")}, ${placesOf("f")}" +
          " FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE "
      ) ++
        FilterSql.sourceWhere(filter) ++ Sql.raw(" AND ") ++ FilterSql.findingWhere(filter, scope = scope) ++
        Sql.raw(" ") ++ FilterSql.orderBySql(filter)
    else
      val gate =
        if FilterSql.hasFindingPlaceCriteria(filter) then Sql.raw(" AND fp.matched > 0") else Sql.empty
      val ownPeriod = filter.period.fold(Sql.raw("TRUE"))(RangeSql.overlaps("s.period", _))
      Sql.raw(
        "SELECT s.citekey, s.citekey, s.title," +
          s" COALESCE(${RangeSql.start("s.period")}, fp.min_start), COALESCE(${RangeSql.end("s.period")}, fp.max_end)," +
          " coalesce(fp.place_ids, '{}'::text[])" +
          " FROM sources s LEFT JOIN LATERAL (" +
          s"SELECT min(${RangeSql.start("f.period")}) AS min_start, max(${RangeSql.end("f.period")}) AS max_end," +
          " count(DISTINCT f.id) AS matched," +
          " count(DISTINCT f.id) FILTER (WHERE "
      ) ++
        FilterSql.findingPeriodWhere(filter) ++
        Sql.raw(
          ") AS in_window, array_agg(DISTINCT l.place_id) FILTER (WHERE l.place_id IS NOT NULL) AS place_ids" +
            " FROM findings f LEFT JOIN finding_places l ON l.finding_id = f.id" +
            " WHERE f.citekey = s.citekey AND "
        ) ++
        FilterSql.findingPlaceWhere(filter, scope = scope) ++
        Sql.raw(") fp ON true WHERE ") ++ FilterSql.sourceWhere(filter) ++ gate ++
        Sql.raw(" AND (") ++ ownPeriod ++ Sql.raw(" OR fp.in_window > 0) ") ++
        FilterSql.orderBySql(filter)

  /** Run [[rowsQuery]]. `unit` is `sources` (default) or `findings`. */
  def rows(ds: DataSource, unit: String, filter: Filter, scope: PlaceScope = PlaceScope.empty): Vector[TreeRow] =
    connect(ds)(rowsQuery(unit, filter, scope).frag.query[TreeRowRaw].run()).map(_.toRow)

  // ── the gazetteer the rows hang off ──────────────────────────────────────────────────────────

  /** The upward closure of `ids`: every place reachable from them through `part_of`, whatever the validity of the
    * edges. Deliberately UNFILTERED by `asOf` — `by=time,region` reads the hierarchy at a different year in every time
    * bucket, so the as-of predicate belongs in [[Tree]], applied to the (small) closure, not in this query.
    */
  private def closure(ids: List[String]): String =
    s"""WITH RECURSIVE up(id) AS (
          SELECT p.id FROM places p WHERE p.id = ANY(?::text[])
          UNION
          SELECT r.parent_id FROM place_relations r JOIN up ON r.child_id = up.id
          WHERE r.kind = '${PlaceRelation.PartOf}'
        )"""

  private def closureParams(ids: List[String]) = List(Param.A(ids))

  /** The nodes of the closure — id, label, kind, own validity, and whether the shape is contested. */
  /** `withdrawn` is Pleiades's own marker for a superseded record. Some of the export carries it as a property, some
    * only as a `Withdrawn (duplicate): …` label — [[Tree.rootEligible]] checks the label too, so both spellings are
    * caught wherever they come from.
    */
  def nodesQuery(ids: List[String]): Sql =
    Sql(
      closure(ids) +
        s" SELECT p.id, p.label, p.kind, ${RangeSql.cols("p.period")}," +
        " coalesce((p.properties->>'contested')::boolean, false)," +
        " coalesce((p.properties->>'withdrawn')::boolean, false)" +
        " FROM places p JOIN up ON up.id = p.id",
      closureParams(ids)
    )

  /** The `part_of` edges WITHIN the closure, each with its own validity. */
  def relationsQuery(ids: List[String]): Sql =
    Sql(
      closure(ids) +
        s" SELECT r.child_id, r.parent_id, r.kind, ${RangeSql.cols("r.period")}" +
        " FROM place_relations r" +
        " WHERE r.child_id IN (SELECT id FROM up) AND r.parent_id IN (SELECT id FROM up)",
      closureParams(ids)
    )

  /** The gazetteer slice the given rows need: every place they were materialised into plus all its ancestors, and the
    * edges between them. Empty when nothing is located — no query is run then.
    */
  def gazetteer(ds: DataSource, rows: Seq[TreeRow]): (List[RegionNode], List[PlaceRelation]) =
    val ids = rows.iterator.flatMap(_.placeIds).toList.distinct
    if ids.isEmpty then (Nil, Nil)
    else
      connect(ds):
        val ns = nodesQuery(ids).frag.query[RegionNodeRow].run().map(_.toNode).toList
        val rs = relationsQuery(ids).frag.query[RelationRow].run().map(_.toRelation).toList
        (ns, rs)
