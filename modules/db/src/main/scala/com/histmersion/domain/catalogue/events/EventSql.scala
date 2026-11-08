package com.histmersion
package domain.catalogue.events

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{EventPrecision, EventTaxonomy, EventTier}
import com.histmersion.api.library.Filter
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.{EnvelopeSql, LikePattern, RangeSql}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.catalogue.Place

/** The events' SQL other queries of the catalogue embed: an event's tier and anchor, the selection of the event list
  * (and of the map's events layer), the map's event tiles' candidates.
  */
object EventSql:
  type Point = Point.T
  object Point extends RefinedType[String, Pure]

  /** The SQL of an event's TIER over `events e`: attested when it names a source or an attestation names it. */

  type TierSql = TierSqlValue
  type TierSqlValue = String
  object TierSqlValue:
    inline def apply(v: String): String = v
  type AnchorSql = AnchorSqlValue
  type AnchorSqlValue = String
  object AnchorSqlValue:
    inline def apply(v: String): String = v

  val TierSql: TierSql =
    "CASE WHEN e.citekey IS NOT NULL OR EXISTS (SELECT 1 FROM attestations ta WHERE ta.target_type = 'event' " +
      s"AND ta.target_id = e.id) THEN '${EventTier.Attested}' ELSE '${EventTier.Catalogue}' END"

  /** An event's anchor point over `events e` with its place `ep` joined: its own geometry, else its place's (a point,
    * or the point-on-surface of an area).
    */
  val AnchorSql: AnchorSql =
    "(SELECT CASE WHEN GeometryType(g) = 'POINT' THEN g ELSE ST_PointOnSurface(g) END " +
      "FROM (SELECT coalesce(e.geom, ep.geom) AS g) x)"

  /** SQL: the box `km` around the point `pt` in degrees — what lets the findings' geometry index answer before the
    * exact geography distance is computed (a `::geography` cast cannot use it).
    */
  def boxAround(pt: Point, km: CatalogMerge.Tolerance.Km): String =
    val dy = km.value / 111.32
    s"ST_Expand($pt, $dy / greatest(cos(radians(ST_Y($pt))), 0.05), $dy)"

  /** `events e` with its place (`ep`) joined. */
  val fromEvents = "events e LEFT JOIN places ep ON ep.id = e.place_id"

  /** What only the catalogue work narrows events by: the tier, the catalogue context of the map's layer, and the area
    * the anchor lies in.
    */
  final case class Narrow(tier: Option[Narrow.Tier] = None, context: Narrow.Context = Narrow.Context(false), area: Option[BBox] = None)

  object Narrow:
    type Tier = Tier.T
    object Tier extends RefinedType[String, Pure]
    type Context = Context.T
    object Context extends RefinedType[Boolean, Pure]

  /** The WHERE of the event list over `events e` with its place `ep` (the area reads the anchor): the events related to
    * the filter's sources ([[EventSourceFilter]]), narrowed by the event's own `text` (label/description substring),
    * kind, historicity, place and [[Narrow]].
    */
  def listWhere(
      related: EventSourceFilter,
      filter: Filter,
      scope: PlaceScope,
      text: Option[EventSelection.Text],
      kind: Option[EventSelection.Kind],
      historicity: Option[EventSelection.Historicity],
      placeId: Option[Place.Id],
      more: Narrow = Narrow()
  ): Sql =
    def clean(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
    val ps = List.newBuilder[Sql]
    val selected = related.eventWhere(filter, scope)
    val catalogueOnly = s"($TierSql) = '${EventTier.Catalogue}'"
    if selected.text.value != "TRUE" then
      if more.context.value then
        // the catalogue's events of the period stay, whatever the sources selected
        val inPeriod = related.eventWhere(Filter(period = filter.period, title = filter.title), PlaceScope.empty)
        ps += (Sql.raw(Sql.RawText("(")) ++ selected ++ Sql.raw(Sql.Text(s" OR ($catalogueOnly AND ")) ++ inPeriod ++ Sql.raw(Sql.Text("))")))
      else ps += selected
    clean(more.tier.map(_.value)).map(_.toLowerCase).foreach(t => ps += Sql.of(Sql.Text(s"($TierSql) = ?"), Param.S(Param.S.V(t))))
    more.area.foreach(b => ps += EnvelopeSql.envelopes(s"coalesce(e.geom, ep.geom)", b))
    clean(text.map(_.value)).foreach { t =>
      val c = LikePattern.contains(t)
      ps += Sql.of(Sql.Text("(e.label ILIKE ? ESCAPE '\\' OR e.description ILIKE ? ESCAPE '\\')"), Param.S(Param.S.V(c)), Param.S(Param.S.V(c)))
    }
    EventTaxonomy.condition(clean(kind.map(_.value)).map(k => EventTaxonomy.KindCondition.Filter(k))).foreach { c =>
      ps += Sql.of(
        Sql.Text(if c.negate.value then "NOT (e.kind = ANY(?::text[]))" else "e.kind = ANY(?::text[])"),
        Param.A(c.kinds.map(k => Param.A.V(k.value)))
      )
    }
    clean(historicity.map(_.value)).map(_.toLowerCase).foreach(h => ps += Sql.of(Sql.Text("e.historicity = ?"), Param.S(Param.S.V(h))))
    clean(placeId.map(_.value)).foreach(p => ps += Sql.of(Sql.Text("e.place_id = ?"), Param.S(Param.S.V(p))))
    val parts = ps.result()
    if parts.isEmpty then Sql.raw(Sql.RawText("TRUE"))
    else Sql(Sql.Text(parts.map(_.text.value).mkString(" AND ")), parts.flatMap(_.params))

  /** An event's ANCHOR over `events e` with its place `ep`: its own geometry, else its place's (`s.g`, `s.src`), as a
    * point — the point itself, or the point-on-surface of an area (`a.pt`).
    */
  val anchorJoins: String =
    """CROSS JOIN LATERAL (SELECT coalesce(e.geom, ep.geom) AS g,
                                 CASE WHEN e.geom IS NOT NULL THEN 'event' ELSE 'place' END AS src) s
       CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(s.g) = 'POINT' THEN s.g
                                       ELSE ST_PointOnSurface(s.g) END AS pt) a"""

  /** The map's events as the tiles select them: the anchor's id, place and dates, for the list's selection (`where`,
    * over `events e` with `ep`), in the tile's box `envelope`.
    */
  def tileCandidates(where: Sql, envelope: Sql): Sql =
    Sql.raw(Sql.Text(s"""SELECT e.id AS key, ST_X(a.pt) AS lon, ST_Y(a.pt) AS lat,
                  ${RangeSql.start("e.outer_span")} AS ps, ${RangeSql.end("e.outer_span")} AS pe, false AS ub
                FROM $fromEvents $anchorJoins
                WHERE s.g IS NOT NULL AND e.uncertainty <= ${EventPrecision.ApproximateUpTo} AND s.g && """)) ++
      envelope ++ Sql.raw(Sql.Text(" AND ")) ++ where

  /** An event with no known date: its outer span open on both sides. */
  val UndatedSql = "(lower_inf(e.outer_span) AND upper_inf(e.outer_span))"

  /** [[tileCandidates]] and, beside them, the events `undatedWhere` selects that have no known date (whatever the span:
    * `undatedWhere` is the selection without it), each marked (`ub`).
    */
  def tileCandidatesWithUndated(where: Sql, undatedWhere: Sql, envelope: Sql): Sql =
    Sql.raw(Sql.Text(s"""SELECT e.id AS key, ST_X(a.pt) AS lon, ST_Y(a.pt) AS lat,
                  ${RangeSql.start("e.outer_span")} AS ps, ${RangeSql.end("e.outer_span")} AS pe, $UndatedSql AS ub
                FROM $fromEvents $anchorJoins
                WHERE s.g IS NOT NULL AND s.g && """)) ++ envelope ++
      Sql.raw(Sql.Text(s" AND ((e.uncertainty <= ${EventPrecision.ApproximateUpTo} AND ")) ++ where ++
      Sql.raw(Sql.Text(s") OR ($UndatedSql AND ")) ++ undatedWhere ++ Sql.raw(Sql.Text("))"))
