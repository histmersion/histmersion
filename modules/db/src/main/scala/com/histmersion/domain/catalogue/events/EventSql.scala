package com.histmersion
package domain.catalogue.events

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{EventPrecision, EventTaxonomy}
import com.histmersion.api.library.Filter
import com.histmersion.util.database.{joined, literal, EnvelopeSql, LikePattern, RangeSql}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.catalogue.Place
import com.augustnagro.magnum.{sql, Frag}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.lint.{tolerated, LintRule}

/** The events' SQL other queries of the catalogue embed: an event's tier and anchor, the selection of the event list
  * (and of the map's events layer), the map's event tiles' candidates.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object EventSql:

  /** The SQL of an event's TIER over `events e`: attested when it names a source or an attestation names it. The two
    * answers are `EventTier.Attested` and `EventTier.Catalogue` word for word (`EventSqlSuite` holds it).
    */
  val TierSql: Frag =
    sql"CASE WHEN e.citekey IS NOT NULL OR EXISTS (SELECT 1 FROM attestations ta WHERE ta.target_type = 'event' AND ta.target_id = e.id) THEN 'attested' ELSE 'catalogue' END"

  /** The event is of the catalogue tier only ([[TierSql]]). */
  val CatalogueOnly: Frag = sql"($TierSql) = 'catalogue'"

  /** An event's anchor point over `events e` with its place `ep` joined: its own geometry, else its place's (a point,
    * or the point-on-surface of an area).
    */
  val AnchorSql: Frag =
    sql"(SELECT CASE WHEN GeometryType(g) = 'POINT' THEN g ELSE ST_PointOnSurface(g) END FROM (SELECT coalesce(e.geom, ep.geom) AS g) x)"

  /** SQL: the box `km` around the point `pt` (an SQL expression) in degrees — what lets the findings' geometry index
    * answer before the exact geography distance is computed (a `::geography` cast cannot use it).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def boxAround(pt: Frag, km: CatalogMerge.Tolerance.Km): Frag =
    val dy = (km.value / 111.32).literal
    sql"ST_Expand($pt, $dy / greatest(cos(radians(ST_Y($pt))), 0.05), $dy)"

  /** `events e` with its place (`ep`) joined. */
  val fromEvents: Frag = sql"events e LEFT JOIN places ep ON ep.id = e.place_id"

  /** What only the catalogue work narrows events by: the tier, the catalogue context of the map's layer, and the area
    * the anchor lies in.
    */
  final case class Narrow(
      tier: Option[Narrow.Tier] = None,
      context: Narrow.Context = Narrow.Context(false),
      area: Option[BBox] = None
  )

  object Narrow:
    type Tier = Tier.T
    object Tier extends Newtype[String, Pure]
    type Context = Context.T
    object Context extends Newtype[Boolean, Pure]

  /** The WHERE of the event list over `events e` with its place `ep` (the area reads the anchor): the events related to
    * the filter's sources ([[EventSourceFilter]]), narrowed by the event's own `text` (label/description substring),
    * kind, historicity, place and [[Narrow]].
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def listWhere(
      related: EventSourceFilter,
      filter: Filter,
      scope: PlaceScope,
      text: Option[EventSelection.Text],
      kind: Option[EventSelection.Kind],
      historicity: Option[EventSelection.Historicity],
      placeId: Option[Place.Id],
      more: Narrow = Narrow()
  ): Frag =
    def clean(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
    val ps = List.newBuilder[Frag]
    val selected = related.eventWhere(filter, scope)
    if selected.sqlString != "TRUE" then
      if more.context.value then
        // the catalogue's events of the period stay, whatever the sources selected
        val inPeriod = related.eventWhere(Filter(period = filter.period, title = filter.title), PlaceScope.empty)
        ps += (sql"($selected OR ($CatalogueOnly AND $inPeriod))")
      else ps += selected
    clean(more.tier.map(_.value))
      .map(_.toLowerCase)
      .foreach(t => ps += sql"($TierSql) = $t")
    more.area.foreach(b => ps += EnvelopeSql.envelopes(sql"coalesce(e.geom, ep.geom)", b))
    clean(text.map(_.value)).foreach { t =>
      val c = LikePattern.contains(t)
      ps += sql"(e.label ILIKE $c ESCAPE '\\' OR e.description ILIKE $c ESCAPE '\\')"
    }
    EventTaxonomy.condition(clean(kind.map(_.value)).map(k => EventTaxonomy.KindCondition.Filter(k))).foreach { c =>
      ps += (if c.negate.value then sql"NOT (e.kind = ANY(${c.kinds}::text[]))"
             else sql"e.kind = ANY(${c.kinds}::text[])")
    }
    clean(historicity.map(_.value))
      .map(_.toLowerCase)
      .foreach(h => ps += sql"e.historicity = $h")
    clean(placeId.map(_.value)).foreach(p => ps += sql"e.place_id = $p")
    val parts = ps.result()
    if parts.isEmpty then sql"TRUE"
    else parts.joined(sql" AND ")

  /** An event's ANCHOR over `events e` with its place `ep`: its own geometry, else its place's (`s.g`, `s.src`), as a
    * point — the point itself, or the point-on-surface of an area (`a.pt`).
    */
  val anchorJoins: Frag =
    sql"""CROSS JOIN LATERAL (SELECT coalesce(e.geom, ep.geom) AS g,
                                 CASE WHEN e.geom IS NOT NULL THEN 'event' ELSE 'place' END AS src) s
       CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(s.g) = 'POINT' THEN s.g
                                       ELSE ST_PointOnSurface(s.g) END AS pt) a"""

  /** The map's events as the tiles select them: the anchor's id, place and dates, for the list's selection (`where`,
    * over `events e` with `ep`), in the tile's box `envelope`.
    */
  def tileCandidates(where: Frag, envelope: Frag): Frag =
    sql"""SELECT e.id AS key, ST_X(a.pt) AS lon, ST_Y(a.pt) AS lat,
                  ${RangeSql.start(sql"e.outer_span")} AS ps, ${RangeSql.end(sql"e.outer_span")} AS pe, false AS ub
                FROM $fromEvents $anchorJoins
                WHERE s.g IS NOT NULL AND e.uncertainty <= ${EventPrecision.ApproximateUpTo.literal} AND s.g && $envelope AND $where"""

  /** An event with no known date: its outer span open on both sides. */
  val UndatedSql: Frag = sql"(lower_inf(e.outer_span) AND upper_inf(e.outer_span))"

  /** [[tileCandidates]] and, beside them, the events `undatedWhere` selects that have no known date (whatever the span:
    * `undatedWhere` is the selection without it), each marked (`ub`).
    */
  def tileCandidatesWithUndated(where: Frag, undatedWhere: Frag, envelope: Frag): Frag =
    sql"""SELECT e.id AS key, ST_X(a.pt) AS lon, ST_Y(a.pt) AS lat,
                  ${RangeSql.start(sql"e.outer_span")} AS ps, ${RangeSql.end(
        sql"e.outer_span"
      )} AS pe, $UndatedSql AS ub
                FROM $fromEvents $anchorJoins
                WHERE s.g IS NOT NULL AND s.g && $envelope AND ((e.uncertainty <= ${EventPrecision.ApproximateUpTo.literal} AND $where) OR ($UndatedSql AND $undatedWhere))"""
