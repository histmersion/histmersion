package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{
  ArticleEventLead,
  CatalogRecord,
  EventEntry,
  EventFindingRef,
  EventSourceRef,
  EventTier
}
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.events.EventSql
import com.histmersion.util.database.RangeSql

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.events.CatalogMerge
import com.histmersion.domain.DeepTime

/** What the events' leads and cards are computed from: the sources attesting an event, the findings near it, the facts
  * of its lead, the catalogue events an article's region and period hold. Every method runs on the connection it is
  * given.
  */
private[evidence] object LeadRepo:

  /** The sources attesting one event: its own citekey, then the attestations naming it. */

  def attestingSources(id: Event.Id)(using DbCon): List[EventSourceRef] =
    val own = sql"""SELECT s.citekey, s.title, s.year FROM events e JOIN sources s ON s.citekey = e.citekey
                      WHERE e.id = $id"""
      .query[(Citekey, Option[EventSourceRef.Title], Option[Year])]
      .run()
      .toList
      .map((ck, t, y) => EventSourceRef(ck, t, y, EventSourceRef.Via("citekey")))
    val att = sql"""SELECT DISTINCT ON (a.citekey) a.citekey, s.title, s.year, a.quote
                      FROM attestations a JOIN sources s ON s.citekey = a.citekey
                      WHERE a.target_type = 'event' AND a.target_id = $id ORDER BY a.citekey, a.id"""
      .query[(Citekey, Option[EventSourceRef.Title], Option[Year], Option[EventSourceRef.Quote])]
      .run()
      .toList
      .map((ck, t, y, q) => EventSourceRef(ck, t, y, EventSourceRef.Via("attestation"), q))
    (own ++ att.filterNot(a => own.exists(_.citekey == a.citekey)))

  /** The dated, placed findings within `km` of an event's anchor and `years` of its outer span, nearest in time then
    * space first: their distance and the years between (0 = overlapping).
    */
  def nearFindings(id: Event.Id, km: CatalogMerge.Tolerance.Km, years: CatalogMerge.Tolerance.Years, limit: PageSize)(
      using DbCon
  ): List[EventFindingRef] =
    sql"""WITH ev AS (
              SELECT ${SqlLiteral(EventSql.AnchorSql)} AS pt, e.outer_span AS span
              FROM events e LEFT JOIN places ep ON ep.id = e.place_id WHERE e.id = $id)
            SELECT f.id, f.citekey, f.claim,
                   ST_Distance(f.geom::geography, ev.pt::geography) / 1000.0,
                   CASE WHEN f.period && ev.span THEN 0
                        ELSE greatest(${RangeSql.start(sql"f.period")} - (${RangeSql.end(sql"ev.span")}),
                                      ${RangeSql.start(sql"ev.span")} - (${RangeSql.end(sql"f.period")})) END,
                   ${RangeSql.cols(sql"f.period")}
            FROM ev JOIN findings f
              ON f.geom IS NOT NULL AND ev.pt IS NOT NULL AND ev.span IS NOT NULL
             AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
             AND f.geom && ${SqlLiteral(EventSql.boxAround(EventSql.Point("ev.pt"), km))}
             AND ST_DWithin(f.geom::geography, ev.pt::geography, ${km.value * 1000.0})
             AND f.period && int8range(${RangeSql.start(sql"ev.span")} - $years, ${RangeSql.end(
        sql"ev.span"
      )} + $years, '[]')
            ORDER BY 5, 4, f.id LIMIT $limit"""
      .query[NearFindingRow]
      .run()
      .toList
      .map(r =>
        EventFindingRef(
          EventFindingRef.Id(r.id.value),
          Citekey(r.citekey.value),
          EventFindingRef.Claim(r.claim.value),
          EventFindingRef.Km(r.km.value),
          EventFindingRef.YearGap(r.yearGap.value.max(0)),
          r.periodStart.map(y => Year(y.value.toLong)),
          r.periodEnd.map(y => Year(y.value.toLong))
        )
      )

  /** The facts of the leads of the events `where` selects (over `events e`, `ep` its place): tier, size, findings near
    * (within `km`, `years`), proposed and confirmed sources. Catalogue events first, the largest first.
    */
  def leadRows(
      where: Frag,
      km: CatalogMerge.Tolerance.Km,
      years: CatalogMerge.Tolerance.Years,
      limit: PageSize,
      offset: Offset
  )(using DbCon): (RowCount, List[LeadRow]) =
    val total = sql"SELECT count(*)::int FROM events e LEFT JOIN places ep ON ep.id = e.place_id WHERE $where"
      .query[Int]
      .run()
      .head
    val rows = (sql"""SELECT e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after,
                 ${SqlLiteral(EventSql.TierSql)} AS tier,
                 (SELECT max(c.vei) FROM event_catalogue c WHERE c.event_id = e.id),
                 (SELECT max(c.magnitude) FROM event_catalogue c WHERE c.event_id = e.id),
                 (SELECT count(*)::int FROM findings f
                   WHERE a.pt IS NOT NULL AND e.outer_span IS NOT NULL AND f.geom IS NOT NULL
                     AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
                     AND f.geom && ${SqlLiteral(EventSql.boxAround(EventSql.Point("a.pt"), km))}
                     AND ST_DWithin(f.geom::geography, a.pt::geography, ${km.value * 1000.0})
                     AND f.period && int8range(${RangeSql.start(sql"e.outer_span")} - $years, ${RangeSql.end(
        sql"e.outer_span"
      )} + $years, '[]')),
                 (SELECT count(*)::int FROM event_link_candidates k WHERE k.event_id = e.id AND k.status = 'proposed'),
                 (SELECT count(*)::int FROM event_link_candidates k WHERE k.event_id = e.id AND k.status = 'confirmed')
               FROM events e LEFT JOIN places ep ON ep.id = e.place_id
               CROSS JOIN LATERAL (SELECT ${SqlLiteral(
        EventSql.AnchorSql
      )} AS pt) a WHERE $where ORDER BY (${SqlLiteral(EventSql.TierSql)}) = '${SqlLiteral(
        EventTier.Catalogue
      )}' DESC, (SELECT coalesce(max(c.vei), 0) + coalesce(max(c.magnitude), 0) FROM event_catalogue c WHERE c.event_id = e.id) DESC, coalesce(e.not_before, e.begin_by), e.id LIMIT ${limit.value
        .max(1)} OFFSET ${offset.value.max(0)}""")
    (RowCount(total), rows.query[LeadRow].run().toList)

  /** Catalogue events an article's period and region hold: those overlapping `period` within `km` of one of `citekeys`'
    * dated findings of that period, and large enough (`minVei`, `minMagnitude`, or a war) — with how many of those
    * findings are near each.
    */
  def articleCandidates(
      citekeys: List[com.histmersion.domain.library.Citekey],
      period: Option[(DeepTime.SignedYear, DeepTime.SignedYear)],
      km: CatalogMerge.Tolerance.Km,
      minVei: CatalogRecord.Vei,
      minMagnitude: CatalogRecord.Magnitude,
      limit: PageSize
  )(using
      DbCon
  ): List[
    (
        Event.Id,
        CatalogRecord.Label,
        CatalogRecord.Kind,
        TimeSpan,
        List[EventEntry.Names],
        Option[CatalogRecord.Vei],
        Option[CatalogRecord.Magnitude],
        ArticleEventLead.FindingsNear,
        CatalogRecord.Provider
    )
  ] =
    if citekeys.isEmpty then Nil
    else
      val (p0, p1) = period.map((a, b) => (a.value, b.value)).getOrElse((Long.MinValue / 4, Long.MaxValue / 4))
      sql"""WITH fs AS (
                SELECT f.geom, f.period FROM findings f
                WHERE f.citekey = ANY($citekeys::text[]) AND f.geom IS NOT NULL
                  AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
                  AND f.period && int8range($p0, $p1, '[]'))
              SELECT e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after, e.names,
                     (SELECT max(c.vei) FROM event_catalogue c WHERE c.event_id = e.id),
                     (SELECT max(c.magnitude) FROM event_catalogue c WHERE c.event_id = e.id),
                     (SELECT count(*)::int FROM fs
                        WHERE ST_DWithin(fs.geom::geography, e.geom::geography, ${km.value * 1000.0})),
                     coalesce(e.catalogue, '')
              FROM events e
              WHERE e.catalogue IS NOT NULL AND e.geom IS NOT NULL AND e.outer_span && int8range($p0, $p1, '[]')
                AND (e.kind = 'war' OR EXISTS (SELECT 1 FROM event_catalogue c WHERE c.event_id = e.id
                       AND (c.vei >= $minVei OR c.magnitude >= $minMagnitude)))
                AND EXISTS (SELECT 1 FROM fs WHERE ST_DWithin(fs.geom::geography, e.geom::geography, ${km.value * 1000.0}))
              ORDER BY 11 DESC, e.id LIMIT $limit"""
        .query[
          (
              String,
              String,
              String,
              Option[Long],
              Option[Long],
              Option[Long],
              Option[Long],
              List[String],
              Option[Int],
              Option[Double],
              Int,
              String
          )
        ]
        .run()
        .toList
        .map { case (id, label, kind, a, b, c, d, names, vei, mag, n, prov) =>
          (
            Event.Id.assume(id),
            CatalogRecord.Label(label),
            CatalogRecord.Kind(kind),
            TimeSpan(
              a.map(y => Year(y)),
              b.map(y => Year(y)),
              c.map(y => Year(y)),
              d.map(y => Year(y))
            ),
            names.map(x => EventEntry.Names(x)),
            vei.map(v => CatalogRecord.Vei(v)),
            mag.map(m => CatalogRecord.Magnitude(m)),
            ArticleEventLead.FindingsNear(n),
            CatalogRecord.Provider(prov)
          )
        }
