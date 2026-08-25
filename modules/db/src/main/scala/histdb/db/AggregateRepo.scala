package histdb.db

import histdb.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

given DbCodec[PeriodRow] = DbCodec.derived

/** Period bounds of everything matching a filter — the raw input of [[histdb.core.Aggregate]].
  *
  * The DB only SELECTS periods; **bucketing happens in Scala** (pure, unit-tested) so the strip
  * (§1.7) and the wave-2 "by time" tree (§1.6) cannot drift apart, and so no SQL has to know about
  * adaptive bucket sizes. */
object AggregateRepo:

  /** Delimiter-free filter clause; mirrors [[SourceRepo.geoWhen]] exactly (region substring, bbox,
    * period overlap) so the histogram and the map feed agree on what "matches".
    *
    * TODO(integration): stream 1.1 introduces `Filter` + `db/FilterSql.scala`; replace the WHERE
    * clauses below with the shared fragment (this whole function is the only thing to swap — the
    * pure bucketing above it and the handler around it stay put). */
  def periods(
      ds: DataSource,
      unit: String,
      region: Option[String],
      bbox: Option[(Double, Double, Double, Double)],
      periodStart: Option[Long],
      periodEnd: Option[Long]
  ): Vector[PeriodRow] =
    val regionLike                       = region.map(r => s"%$r%")
    val (minLon, minLat, maxLon, maxLat) = bbox match
      case Some((a, b, c, d)) => (Some(a), Some(b), Some(c), Some(d))
      case None               => (None, None, None, None)
    connect(ds):
      if unit == "findings" then
        // one row per finding
        sql"""SELECT lower(f.period), upper(f.period) - 1
              FROM findings f JOIN sources s ON s.citekey = f.citekey
              WHERE ($region IS NULL OR f.region_name ILIKE $regionLike)
                AND ($minLon IS NULL
                     OR (f.geom IS NOT NULL
                         AND f.geom && ST_MakeEnvelope($minLon, $minLat, $maxLon, $maxLat, 4326)))
                AND ($periodStart IS NULL OR f.period && int8range($periodStart, $periodEnd, '[]'))
           """.query[PeriodRow].run()
      else
        // one row per SOURCE (the unit of every result, §1.0). A source matches when its own period
        // or any of its findings' periods overlaps the window, and the place filters (which are
        // finding-level, as in geoWhen) are satisfied by at least one finding. Its bucketed period is
        // its own `period`, falling back to the envelope of its matching findings when undated.
        sql"""SELECT COALESCE(lower(s.period), fp.min_start),
                     COALESCE(upper(s.period) - 1, fp.max_end)
              FROM sources s
              LEFT JOIN LATERAL (
                SELECT min(lower(f.period))     AS min_start,
                       max(upper(f.period) - 1) AS max_end,
                       count(*)                 AS matched,
                       count(*) FILTER (
                         WHERE f.period && int8range($periodStart, $periodEnd, '[]')) AS in_window
                FROM findings f
                WHERE f.citekey = s.citekey
                  AND ($region IS NULL OR f.region_name ILIKE $regionLike)
                  AND ($minLon IS NULL
                       OR (f.geom IS NOT NULL
                           AND f.geom && ST_MakeEnvelope($minLon, $minLat, $maxLon, $maxLat, 4326)))
              ) fp ON true
              WHERE (($region IS NULL AND $minLon IS NULL) OR fp.matched > 0)
                AND ($periodStart IS NULL
                     OR s.period && int8range($periodStart, $periodEnd, '[]')
                     OR fp.in_window > 0)
           """.query[PeriodRow].run()
