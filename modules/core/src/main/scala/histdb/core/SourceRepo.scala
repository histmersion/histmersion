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

object SourceRepo:
  /** Delimiter for building text[] via string_to_array without an array codec. */
  private val Sep = ""

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
               period, summary, source, calibre_id, doi, cited_by_count, influential_citations)
            VALUES
              (${s.citekey}, ${s.title},
               COALESCE(string_to_array(NULLIF($authors, ''), $Sep), '{}'::text[]),
               ${s.year}, ${s.venue}, $status,
               COALESCE(string_to_array(NULLIF($topics, ''), $Sep), '{}'::text[]),
               COALESCE(string_to_array(NULLIF($regions, ''), $Sep), '{}'::text[]),
               COALESCE(string_to_array(NULLIF($tags, ''), $Sep), '{}'::text[]),
               ${periodLit}::int8range, ${s.summary}, ${s.provenance}, ${s.calibreId}, ${s.doi},
               ${s.citedByCount}, ${s.influentialCitations})
            ON CONFLICT (citekey) DO UPDATE SET
              title=EXCLUDED.title, authors=EXCLUDED.authors, year=EXCLUDED.year, venue=EXCLUDED.venue,
              status=EXCLUDED.status, topics=EXCLUDED.topics, regions=EXCLUDED.regions, tags=EXCLUDED.tags,
              period=EXCLUDED.period, summary=EXCLUDED.summary, source=EXCLUDED.source,
              calibre_id=EXCLUDED.calibre_id, doi=EXCLUDED.doi, cited_by_count=EXCLUDED.cited_by_count,
              influential_citations=EXCLUDED.influential_citations, updated_at=now()""".update.run()
