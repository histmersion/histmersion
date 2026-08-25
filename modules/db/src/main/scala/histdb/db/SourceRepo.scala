package histdb.db

import histdb.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Magnum codecs for the shared wire DTOs (defined in core, DB-agnostic). */
given DbCodec[SearchHit]     = DbCodec.derived
given DbCodec[SourceDetail]  = DbCodec.derived
given DbCodec[FindingRow]    = DbCodec.derived
given DbCodec[GeoHit]        = DbCodec.derived
given DbCodec[NeighborRow]   = DbCodec.derived
given DbCodec[Facet]         = DbCodec.derived
given DbCodec[FilterSql.SourceRow] = DbCodec.derived
given DbCodec[SourceRepo.FindingHitRow] = DbCodec.derived

/** Flat projections used only by the write paths (Magnum maps a SELECT's columns positionally). */
private[db] final case class FullSourceRow(
    citekey: String, title: String, authors: String, doi: Option[String], year: Option[Int],
    venue: Option[String], status: String, topics: String, regions: String, tags: String,
    periodStart: Option[Long], periodEnd: Option[Long], summary: Option[String],
    citedByCount: Option[Int], influentialCitations: Option[Int], provenance: Option[String],
    calibreId: Option[String], ocrMarkdown: Option[String], notes: Option[String], updatedAt: String
)
private[db] final case class FindingIdRow(
    id: String, claim: String, regionName: Option[String], lon: Option[Double], lat: Option[Double],
    periodStart: Option[Long], periodEnd: Option[Long], periodName: Option[String], tags: String,
    confidence: Option[String]
)
private[db] final case class EdgeRow(src: String, dst: String, kind: String)
private[db] final case class CitekeyMillis(citekey: String, millis: Long)

given DbCodec[FullSourceRow] = DbCodec.derived
given DbCodec[FindingIdRow]  = DbCodec.derived
given DbCodec[EdgeRow]       = DbCodec.derived
given DbCodec[CitekeyMillis] = DbCodec.derived

object SourceRepo:
  /** Delimiter for building text[] via string_to_array without an array codec (won't occur in data). */
  private val Sep = "|~|"

  /** Delimiter for splitting a text[] back into a List on the way out (same reasoning). */
  private def splitArr(s: String): List[String] =
    if s.isEmpty then Nil else s.split(java.util.regex.Pattern.quote(Sep), -1).toList

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Full replace of a source and everything under it, in ONE transaction. Kept as the original
    * `Unit` signature; [[put]] is the same write returning the new `updated_at` token. */
  def upsert(ds: DataSource, s: Source): Unit = { val _ = put(ds, s) }

  /** Full replace (`put_source`). Returns the fresh `updatedAt` concurrency token. */
  def put(ds: DataSource, s: Source): String =
    transact(ds):
      write(s)
      token(s.citekey).getOrElse("")

  /** Selective merge (`patch_source`): read → [[Write.applyPatch]] → write, all in one transaction, so
    * two concurrent patches cannot interleave. The precondition is checked against the row we just
    * read inside that transaction, which is what makes `ifUpdatedAt` meaningful. */
  def patch(ds: DataSource, citekey: String, p: SourcePatch): Either[ApiError, String] =
    transact(ds):
      load(citekey) match
        case None => Left(Write.notFound(citekey))
        case Some(existing) =>
          Write.precondition(existing, p) match
            case Left(msg) => Left(ApiError(msg, Some(Write.CodePrecondition)))
            case Right(_)  =>
              Write.applyPatch(existing, p) match
                case Left(msg)      => Left(ApiError(msg, Some(Write.CodeInvalid)))
                case Right(updated) => write(updated); Right(token(citekey).getOrElse(""))

  /** The write itself, inside the caller's transaction. */
  private def write(s: Source)(using DbCon): Unit =
    val authors   = s.authors.mkString(Sep)
    val topics    = s.topics.mkString(Sep)
    val regions   = s.regions.mkString(Sep)
    val tags      = s.tags.mkString(Sep)
    val status    = s.status.toString.toLowerCase
    val periodLit = s.period.map(p => s"[${p.start},${p.end}]")
    sql"""INSERT INTO sources
            (citekey, title, authors, year, venue, status, topics, regions, tags,
             period, summary, ocr_markdown, notes, source, calibre_id, doi,
             cited_by_count, influential_citations)
          VALUES
            (${s.citekey}, ${s.title},
             COALESCE(string_to_array(NULLIF($authors, ''), $Sep), '{}'::text[]),
             ${s.year}, ${s.venue}, $status,
             COALESCE(string_to_array(NULLIF($topics, ''), $Sep), '{}'::text[]),
             COALESCE(string_to_array(NULLIF($regions, ''), $Sep), '{}'::text[]),
             COALESCE(string_to_array(NULLIF($tags, ''), $Sep), '{}'::text[]),
             ${periodLit}::int8range, ${s.summary}, ${s.ocrMarkdown}, ${s.notes},
             ${s.provenance}, ${s.calibreId}, ${s.doi},
             ${s.citedByCount}, ${s.influentialCitations})
          ON CONFLICT (citekey) DO UPDATE SET
            title=EXCLUDED.title, authors=EXCLUDED.authors, year=EXCLUDED.year, venue=EXCLUDED.venue,
            status=EXCLUDED.status, topics=EXCLUDED.topics, regions=EXCLUDED.regions, tags=EXCLUDED.tags,
            period=EXCLUDED.period, summary=EXCLUDED.summary, ocr_markdown=EXCLUDED.ocr_markdown,
            notes=EXCLUDED.notes,
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
            VALUES (${f.id}, ${s.citekey}, ${f.claim}, ${f.region},
                    ST_SetSRID(ST_MakePoint($lon, $lat), 4326),
                    ${fperiod}::int8range, ${f.periodName},
                    COALESCE(string_to_array(NULLIF($ftags, ''), $Sep), '{}'::text[]),
                    ${f.confidence})""".update.run()
    }

    sql"DELETE FROM edges WHERE src = ${s.citekey}".update.run()
    s.edges.foreach { e =>
      sql"""INSERT INTO edges (src, dst, kind) VALUES (${s.citekey}, ${e.dst}, ${e.kind})
            ON CONFLICT DO NOTHING""".update.run()
    }

  /** The opaque last-write token: `updated_at` rendered UTC-normalised so it is stable to compare and
    * safe to echo back through JSON, whatever the session's TimeZone setting. */
  private def token(citekey: String)(using DbCon): Option[String] =
    sql"""SELECT to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
          FROM sources WHERE citekey = $citekey""".query[String].run().headOption

  /** Read a whole source back as the DOMAIN model (findings + edges included) — the input side of a
    * patch, and what `export` renders. */
  def load(ds: DataSource, citekey: String): Option[Source] = connect(ds)(load(citekey))

  private def load(citekey: String)(using DbCon): Option[Source] =
    sql"""SELECT citekey, title, array_to_string(authors, $Sep), doi, year, venue, status,
                 array_to_string(topics, $Sep), array_to_string(regions, $Sep), array_to_string(tags, $Sep),
                 lower(period), upper(period) - 1, summary, cited_by_count, influential_citations,
                 source, calibre_id, ocr_markdown, notes,
                 to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
          FROM sources WHERE citekey = $citekey""".query[FullSourceRow].run().headOption.map { r =>
      val fs = sql"""SELECT id, claim, region_name, ST_X(geom), ST_Y(geom),
                            lower(period), upper(period) - 1, period_name,
                            array_to_string(tags, $Sep), confidence
                     FROM findings WHERE citekey = $citekey ORDER BY id""".query[FindingIdRow].run()
      val es = sql"SELECT src, dst, kind FROM edges WHERE src = $citekey ORDER BY kind, dst"
        .query[EdgeRow].run()
      Source(
        citekey = r.citekey, title = r.title, authors = splitArr(r.authors), doi = r.doi, year = r.year,
        venue = r.venue, status = SourceStatus.fromString(r.status), topics = splitArr(r.topics),
        regions = splitArr(r.regions), tags = splitArr(r.tags),
        period = (r.periodStart, r.periodEnd) match
          case (Some(a), Some(b)) => Some(Period(a, b))
          case _                  => None,
        summary = r.summary, citedByCount = r.citedByCount,
        influentialCitations = r.influentialCitations, provenance = r.provenance, calibreId = r.calibreId,
        ocrMarkdown = r.ocrMarkdown,
        findings = fs.toList.map { f =>
          val geo = (f.lon, f.lat) match
            case (Some(x), Some(y)) => Some(GeoPoint(x, y))
            case _                  => None
          Finding(f.id, r.citekey, f.claim, f.regionName, geo,
            (f.periodStart, f.periodEnd) match
              case (Some(a), Some(b)) => Some(Period(a, b))
              case _                  => None,
            f.periodName, splitArr(f.tags), f.confidence)
        },
        edges = es.toList.map(e => Edge(e.src, e.dst, e.kind)),
        notes = r.notes,
        updatedAt = Some(r.updatedAt)
      )
    }

  def citekeys(ds: DataSource): Vector[String] =
    connect(ds)(sql"""SELECT citekey FROM sources ORDER BY citekey COLLATE "C"""".query[String].run())

  /** `citekey -> updated_at` in epoch millis, for the seed guard (one query for the whole corpus). */
  def updatedAtMillis(ds: DataSource): Map[String, Long] =
    connect(ds):
      sql"SELECT citekey, (extract(epoch from updated_at) * 1000)::bigint FROM sources"
        .query[CitekeyMillis].run().map(r => r.citekey -> r.millis).toMap

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
                             summary, doi, ocr_markdown, notes,
                             to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
                      FROM sources WHERE citekey = $citekey""".query[SourceDetail].run().headOption
      src.map { s =>
        val fs = sql"""SELECT claim, region_name, ST_X(geom), ST_Y(geom),
                              lower(period), upper(period) - 1, period_name,
                              array_to_string(tags, ', '), confidence
                       FROM findings WHERE citekey = $citekey ORDER BY id""".query[FindingRow].run()
        (s, fs)
      }

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

  /** `GET /api/findings` — the MAP FEED, through the ONE filter contract (plan §1.0): the findings
    * that THEMSELVES satisfy the finding-level criteria (period overlap, viewport incl. the
    * antimeridian split, place on `region_name`, tags over `findings.tags ∪ sources.tags`) of the
    * sources that satisfy the source-level ones (`q`, authors, status, publication years, topics).
    * `filter.limit` must already be resolved against the configured default (the handler does that). */
  def findingsFor(ds: DataSource, filter: Filter): Vector[GeoHit] =
    connect(ds)(FilterSql.findingsFeedQuery(filter).frag.query[GeoHit].run())

  // ── the filtered source search (plan §1.0/§1.3) ────────────────────────────────────────────
  /** One row of `FilterSql.findingsQuery`. */
  final case class FindingHitRow(
      citekey: String,
      id: String,
      claim: String,
      regionName: Option[String],
      lon: Option[Double],
      lat: Option[Double],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      periodName: Option[String],
      tags: String
  ):
    def toMatch: FindingMatch =
      FindingMatch(id, claim, regionName, lon, lat, periodStart, periodEnd, periodName, tags, Nil)

  /** Sources matching the filter, each with the findings that made it match and why. `total` is
    * count(DISTINCT source) for the whole filter, ignoring limit/offset. `filter.limit` must
    * already be resolved against the configured default (the handler does that). */
  def sources(ds: DataSource, filter: Filter): SearchResult =
    connect(ds):
      val total = FilterSql.countQuery(filter).frag.query[Int].run().headOption.getOrElse(0)
      val rows  = FilterSql.sourcesQuery(filter).frag.query[FilterSql.SourceRow].run()
      if rows.isEmpty then SearchResult(total, Nil)
      else
        // one round-trip for the page's findings; which ones count as evidence is decided purely
        val byCk = FilterSql.findingsQuery(rows.map(_.citekey).toList).frag
          .query[FindingHitRow].run().groupBy(_.citekey)
        val hits = rows.map { r =>
          val matched =
            if !filter.hasFindingCriteria then Nil
            else
              byCk.getOrElse(r.citekey, Vector.empty).toList.flatMap { fr =>
                val m   = fr.toMatch
                val why = FilterSql.findingWhy(filter, m)
                if why.isEmpty then None else Some(m.copy(why = why))
              }
          Hit(r.toHit, matched, FilterSql.sourceWhy(filter, r))
        }
        SearchResult(total, hits.toList)

  /** Facet vocabulary of one dimension within the current filter; counts are DISTINCT sources. */
  def facets(ds: DataSource, field: FacetField, prefix: Option[String], filter: Filter, limit: Int)
      : Vector[Facet] =
    connect(ds):
      FilterSql.facetsQuery(field, prefix, filter, limit).frag.query[Facet].run()
