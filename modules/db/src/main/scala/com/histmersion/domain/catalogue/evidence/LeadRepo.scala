package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventFindingRef, EventSourceRef, EventTier}
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.catalogue.events.EventSql
import com.histmersion.util.database.RangeSql

import com.augustnagro.magnum.*
import com.histmersion.util.database.given_DbCodec_List

/** What the events' leads and cards are computed from: the sources attesting an event, the findings near it, the facts
  * of its lead, the catalogue events an article's region and period hold. Every method runs on the connection it is
  * given.
  */
private[evidence] object LeadRepo:

  /** The sources attesting one event: its own citekey, then the attestations naming it. */

  def attestingSources(id: com.histmersion.domain.catalogue.evidence.LeadRow.Id)(using DbCon): List[EventSourceRef] =
    val own = sql"""SELECT s.citekey, s.title, s.year FROM events e JOIN sources s ON s.citekey = e.citekey
                      WHERE e.id = $id"""
      .query[(String, Option[String], Option[Int])]
      .run()
      .toList
      .map((ck, t, y) => EventSourceRef(ck, t, y, com.histmersion.api.catalogue.EventSourceRef.Via("citekey")))
    val att = sql"""SELECT DISTINCT ON (a.citekey) a.citekey, s.title, s.year, a.quote
                      FROM attestations a JOIN sources s ON s.citekey = a.citekey
                      WHERE a.target_type = 'event' AND a.target_id = $id ORDER BY a.citekey, a.id"""
      .query[(String, Option[String], Option[Int], Option[String])]
      .run()
      .toList
      .map((ck, t, y, q) => EventSourceRef(ck, t, y, com.histmersion.api.catalogue.EventSourceRef.Via("attestation"), q))
    (own ++ att.filterNot(a => own.exists(_.citekey == a.citekey)))

  /** The dated, placed findings within `km` of an event's anchor and `years` of its outer span, nearest in time then
    * space first: their distance and the years between (0 = overlapping).
    */
  def nearFindings(id: com.histmersion.domain.catalogue.evidence.LeadRow.Id, km: com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km, years: com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years, limit: com.histmersion.PageSize)(using DbCon): List[EventFindingRef] =
    sql"""WITH ev AS (
              SELECT ${SqlLiteral(EventSql.AnchorSql)} AS pt, e.outer_span AS span
              FROM events e LEFT JOIN places ep ON ep.id = e.place_id WHERE e.id = $id)
            SELECT f.id, f.citekey, f.claim,
                   ST_Distance(f.geom::geography, ev.pt::geography) / 1000.0,
                   CASE WHEN f.period && ev.span THEN 0
                        ELSE greatest(${SqlLiteral(RangeSql.start("f.period"))} - (${SqlLiteral(
        RangeSql.end("ev.span")
      )}),
                                      ${SqlLiteral(RangeSql.start("ev.span"))} - (${SqlLiteral(
        RangeSql.end("f.period")
      )})) END,
                   ${SqlLiteral(RangeSql.cols("f.period"))}
            FROM ev JOIN findings f
              ON f.geom IS NOT NULL AND ev.pt IS NOT NULL AND ev.span IS NOT NULL
             AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
             AND f.geom && ${SqlLiteral(EventSql.boxAround(com.histmersion.domain.catalogue.events.EventSql.Point("ev.pt"), km))}
             AND ST_DWithin(f.geom::geography, ev.pt::geography, ${km * 1000.0})
             AND f.period && int8range(${SqlLiteral(RangeSql.start("ev.span"))} - $years, ${SqlLiteral(
        RangeSql.end("ev.span")
      )} + $years, '[]')
            ORDER BY 5, 4, f.id LIMIT $limit"""
      .query[NearFindingRow]
      .run()
      .toList
      .map(r => EventFindingRef(r.id, r.citekey, r.claim, r.km, r.yearGap.max(0), r.periodStart, r.periodEnd))

  /** The facts of the leads of the events `where` selects (over `events e`, `ep` its place): tier, size, findings near
    * (within `km`, `years`), proposed and confirmed sources. Catalogue events first, the largest first.
    */
  def leadRows(where: Sql, km: com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km, years: com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years, limit: com.histmersion.PageSize, offset: com.histmersion.Offset)(using DbCon): (com.histmersion.RowCount, List[LeadRow]) =
    val total = (Sql.raw(
      "SELECT count(*)::int FROM events e LEFT JOIN places ep ON ep.id = e.place_id WHERE "
    ) ++ where).frag.query[Int].run().head
    val rows = (Sql.raw(s"""SELECT e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after,
                 ${EventSql.TierSql} AS tier,
                 (SELECT max(c.vei) FROM event_catalogue c WHERE c.event_id = e.id),
                 (SELECT max(c.magnitude) FROM event_catalogue c WHERE c.event_id = e.id),
                 (SELECT count(*)::int FROM findings f
                   WHERE a.pt IS NOT NULL AND e.outer_span IS NOT NULL AND f.geom IS NOT NULL
                     AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
                     AND f.geom && ${EventSql.boxAround(com.histmersion.domain.catalogue.events.EventSql.Point("a.pt"), km)}
                     AND ST_DWithin(f.geom::geography, a.pt::geography, ?)
                     AND f.period && int8range(${RangeSql.start("e.outer_span")} - ?, ${RangeSql.end(
        "e.outer_span"
      )} + ?, '[]')),
                 (SELECT count(*)::int FROM event_link_candidates k WHERE k.event_id = e.id AND k.status = 'proposed'),
                 (SELECT count(*)::int FROM event_link_candidates k WHERE k.event_id = e.id AND k.status = 'confirmed')
               FROM events e LEFT JOIN places ep ON ep.id = e.place_id
               CROSS JOIN LATERAL (SELECT ${EventSql.AnchorSql} AS pt) a WHERE """) ++ where ++
      Sql.raw(
        s" ORDER BY (${EventSql.TierSql}) = '${EventTier.Catalogue}' DESC," +
          " (SELECT coalesce(max(c.vei), 0) + coalesce(max(c.magnitude), 0) FROM event_catalogue c WHERE c.event_id = e.id) DESC," +
          " coalesce(e.not_before, e.begin_by), e.id"
      ) ++
      Sql.of(" LIMIT ? OFFSET ?", Param.I(limit.max(1)), Param.I(offset.max(0))))
    // the three `?` of the findings count come first in the text
    val withParams = Sql(rows.text, List(Param.D(km * 1000.0), Param.L(years), Param.L(years)) ++ rows.params)
    (com.histmersion.RowCount(total), withParams.frag.query[LeadRow].run().toList)

  /** Catalogue events an article's period and region hold: those overlapping `period` within `km` of one of `citekeys`'
    * dated findings of that period, and large enough (`minVei`, `minMagnitude`, or a war) — with how many of those
    * findings are near each.
    */
  def articleCandidates(
      citekeys: List[com.histmersion.domain.library.Citekey],
      period: Option[(com.histmersion.domain.DeepTime.SignedYear, com.histmersion.domain.DeepTime.SignedYear)],
      km: com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km,
      minVei: com.histmersion.domain.catalogue.events.BCatalogueRow.Vei,
      minMagnitude: com.histmersion.domain.catalogue.events.BCatalogueRow.Magnitude,
      limit: com.histmersion.PageSize
  )(using DbCon): List[(com.histmersion.domain.catalogue.events.BEventRow.Id, com.histmersion.domain.library.Citekey, com.histmersion.domain.catalogue.events.BEventRow.Label, TimeSpan, List[com.histmersion.domain.catalogue.events.BEventRow.Label], Option[com.histmersion.domain.catalogue.evidence.LeadRow.Attesting], Option[com.histmersion.domain.catalogue.evidence.LeadRow.Magnitude], com.histmersion.domain.catalogue.evidence.LeadRow.FindingsNear, com.histmersion.domain.catalogue.evidence.LeadRow.Tier)] =
    if citekeys.isEmpty then Nil
    else
      val (p0, p1) = period.getOrElse((Long.MinValue / 4, Long.MaxValue / 4))
      sql"""WITH fs AS (
                SELECT f.geom, f.period FROM findings f
                WHERE f.citekey = ANY($citekeys::text[]) AND f.geom IS NOT NULL
                  AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
                  AND f.period && int8range($p0, $p1, '[]'))
              SELECT e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after, e.names,
                     (SELECT max(c.vei) FROM event_catalogue c WHERE c.event_id = e.id),
                     (SELECT max(c.magnitude) FROM event_catalogue c WHERE c.event_id = e.id),
                     (SELECT count(*)::int FROM fs
                        WHERE ST_DWithin(fs.geom::geography, e.geom::geography, ${km * 1000.0})),
                     coalesce(e.catalogue, '')
              FROM events e
              WHERE e.catalogue IS NOT NULL AND e.geom IS NOT NULL AND e.outer_span && int8range($p0, $p1, '[]')
                AND (e.kind = 'war' OR EXISTS (SELECT 1 FROM event_catalogue c WHERE c.event_id = e.id
                       AND (c.vei >= $minVei OR c.magnitude >= $minMagnitude)))
                AND EXISTS (SELECT 1 FROM fs WHERE ST_DWithin(fs.geom::geography, e.geom::geography, ${km * 1000.0}))
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
          (id, label, kind, TimeSpan(a, b, c, d), names, vei, mag, n, prov)
        }
