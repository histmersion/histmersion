package histdb.core

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** A row of the `search` result set (arrays flattened to comma-joined strings; period split to bounds). */
final case class SearchHit(
    citekey: String,
    title: String,
    year: Option[Int],
    status: String,
    topics: String,
    regions: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String]
) derives DbCodec

/** Full source record for `get_source`. */
final case class SourceDetail(
    citekey: String,
    title: String,
    authors: String,
    year: Option[Int],
    venue: Option[String],
    status: String,
    topics: String,
    regions: String,
    tags: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    doi: Option[String],
    ocrMarkdown: Option[String]
) derives DbCodec

/** One geo-located, time-scoped finding row. */
final case class FindingRow(
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodName: Option[String],
    tags: String,
    confidence: Option[String]
) derives DbCodec

/** A finding plus its source, for `geo_when`. */
final case class GeoHit(
    citekey: String,
    title: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    confidence: Option[String]
) derives DbCodec

object SourceRepo:
  /** Delimiter for building text[] via string_to_array without an array codec (won't occur in data). */
  private val Sep = "|~|"

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  def upsert(ds: DataSource, s: Source): Unit =
    val authors   = s.authors.mkString(Sep)
    val topics    = s.topics.mkString(Sep)
    val regions   = s.regions.mkString(Sep)
    val tags      = s.tags.mkString(Sep)
    val status    = s.status.toString.toLowerCase
    val periodLit = s.period.map(p => s"[${p.start},${p.end}]")
    transact(ds):
      sql"""INSERT INTO sources
              (citekey, title, authors, year, venue, status, topics, regions, tags,
               period, summary, ocr_markdown, source, calibre_id, doi, cited_by_count, influential_citations)
            VALUES
              (${s.citekey}, ${s.title},
               COALESCE(string_to_array(NULLIF($authors, ''), $Sep), '{}'::text[]),
               ${s.year}, ${s.venue}, $status,
               COALESCE(string_to_array(NULLIF($topics, ''), $Sep), '{}'::text[]),
               COALESCE(string_to_array(NULLIF($regions, ''), $Sep), '{}'::text[]),
               COALESCE(string_to_array(NULLIF($tags, ''), $Sep), '{}'::text[]),
               ${periodLit}::int8range, ${s.summary}, ${s.ocrMarkdown},
               ${s.provenance}, ${s.calibreId}, ${s.doi},
               ${s.citedByCount}, ${s.influentialCitations})
            ON CONFLICT (citekey) DO UPDATE SET
              title=EXCLUDED.title, authors=EXCLUDED.authors, year=EXCLUDED.year, venue=EXCLUDED.venue,
              status=EXCLUDED.status, topics=EXCLUDED.topics, regions=EXCLUDED.regions, tags=EXCLUDED.tags,
              period=EXCLUDED.period, summary=EXCLUDED.summary, ocr_markdown=EXCLUDED.ocr_markdown,
              source=EXCLUDED.source, calibre_id=EXCLUDED.calibre_id, doi=EXCLUDED.doi,
              cited_by_count=EXCLUDED.cited_by_count,
              influential_citations=EXCLUDED.influential_citations, updated_at=now()""".update.run()

      sql"DELETE FROM findings WHERE citekey = ${s.citekey}".update.run()
      s.findings.foreach { f =>
        val ftags   = f.tags.mkString(Sep)
        val fperiod = f.period.map(p => s"[${p.start},${p.end}]")
        val lon     = f.geo.map(_.lon)
        val lat     = f.geo.map(_.lat)
        sql"""INSERT INTO findings (id, citekey, claim, region_name, geom, period, period_name, tags, confidence)
              VALUES (${f.id}, ${f.citekey}, ${f.claim}, ${f.region},
                      ST_SetSRID(ST_MakePoint($lon, $lat), 4326),
                      ${fperiod}::int8range, ${f.periodName},
                      COALESCE(string_to_array(NULLIF($ftags, ''), $Sep), '{}'::text[]),
                      ${f.confidence})""".update.run()
      }

      sql"DELETE FROM edges WHERE src = ${s.citekey}".update.run()
      s.edges.foreach { e =>
        sql"""INSERT INTO edges (src, dst, kind) VALUES (${e.src}, ${e.dst}, ${e.kind})
              ON CONFLICT DO NOTHING""".update.run()
      }

  /** Unify edge targets: rewrite a short tag target (e.g. `heggarty2023`) to the full citekey
    * (`heggarty2023_language`) when exactly one source has that prefix and the target isn't already a
    * citekey. Run once after a full import. Returns rows rewritten. */
  def resolveEdges(ds: DataSource): Int =
    transact(ds):
      sql"""UPDATE edges e SET dst = m.citekey
            FROM sources m
            WHERE NOT EXISTS (SELECT 1 FROM sources x WHERE x.citekey = e.dst)
              AND m.citekey LIKE e.dst || '\\_%' ESCAPE '\\'
              AND (SELECT count(*) FROM sources y WHERE y.citekey LIKE e.dst || '\\_%' ESCAPE '\\') = 1
              AND NOT EXISTS (
                SELECT 1 FROM edges e2 WHERE e2.src = e.src AND e2.kind = e.kind AND e2.dst = m.citekey)
         """.update.run()

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  private val hitCols =
    "citekey, title, year, status, array_to_string(topics, ', '), array_to_string(regions, ', '), " +
      "lower(period), upper(period) - 1, summary"

  def search(ds: DataSource, query: Option[String], limit: Int): Vector[SearchHit] =
    connect(ds):
      query match
        case Some(term) =>
          val t = s"%$term%"
          sql"""SELECT citekey, title, year, status,
                       array_to_string(topics, ', '), array_to_string(regions, ', '),
                       lower(period), upper(period) - 1, summary
                FROM sources
                WHERE title ILIKE $t OR summary ILIKE $t OR ocr_markdown ILIKE $t
                ORDER BY cited_by_count DESC NULLS LAST
                LIMIT $limit""".query[SearchHit].run()
        case None =>
          sql"""SELECT citekey, title, year, status,
                       array_to_string(topics, ', '), array_to_string(regions, ', '),
                       lower(period), upper(period) - 1, summary
                FROM sources
                ORDER BY cited_by_count DESC NULLS LAST
                LIMIT $limit""".query[SearchHit].run()

  def get(ds: DataSource, citekey: String): Option[(SourceDetail, Vector[FindingRow])] =
    connect(ds):
      val src = sql"""SELECT citekey, title, array_to_string(authors, ', '), year, venue, status,
                             array_to_string(topics, ', '), array_to_string(regions, ', '),
                             array_to_string(tags, ', '), lower(period), upper(period) - 1,
                             summary, doi, ocr_markdown
                      FROM sources WHERE citekey = $citekey""".query[SourceDetail].run().headOption
      src.map { s =>
        val fs = sql"""SELECT claim, region_name, ST_X(geom), ST_Y(geom),
                              lower(period), upper(period) - 1, period_name,
                              array_to_string(tags, ', '), confidence
                       FROM findings WHERE citekey = $citekey ORDER BY id""".query[FindingRow].run()
        (s, fs)
      }

  /** One reference edge touching a source, oriented relative to it (out = this cites/relates dst). */
  final case class NeighborRow(direction: String, kind: String, citekey: String, title: Option[String])
      derives DbCodec

  def neighbors(ds: DataSource, citekey: String): Vector[NeighborRow] =
    connect(ds):
      sql"""SELECT 'out' AS direction, e.kind, e.dst AS citekey, s.title
            FROM edges e LEFT JOIN sources s ON s.citekey = e.dst
            WHERE e.src = $citekey
            UNION ALL
            SELECT 'in' AS direction, e.kind, e.src AS citekey, s.title
            FROM edges e LEFT JOIN sources s ON s.citekey = e.src
            WHERE e.dst = $citekey
            ORDER BY direction, kind, citekey""".query[NeighborRow].run()

  /** Findings whose (place, period) intersect the selection. All filters are optional. */
  def geoWhen(
      ds: DataSource,
      region: Option[String],
      bbox: Option[(Double, Double, Double, Double)],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      limit: Int
  ): Vector[GeoHit] =
    val regionLike                        = region.map(r => s"%$r%")
    val (minLon, minLat, maxLon, maxLat)  = bbox match
      case Some((a, b, c, d)) => (Some(a), Some(b), Some(c), Some(d))
      case None               => (None, None, None, None)
    connect(ds):
      sql"""SELECT f.citekey, s.title, f.claim, f.region_name,
                   ST_X(f.geom), ST_Y(f.geom), lower(f.period), upper(f.period) - 1, f.confidence
            FROM findings f JOIN sources s ON s.citekey = f.citekey
            WHERE ($region IS NULL OR f.region_name ILIKE $regionLike)
              AND ($periodStart IS NULL OR f.period && int8range($periodStart, $periodEnd, '[]'))
              AND ($minLon IS NULL
                   OR (f.geom IS NOT NULL
                       AND f.geom && ST_MakeEnvelope($minLon, $minLat, $maxLon, $maxLat, 4326)))
            ORDER BY lower(f.period) NULLS LAST
            LIMIT $limit""".query[GeoHit].run()
