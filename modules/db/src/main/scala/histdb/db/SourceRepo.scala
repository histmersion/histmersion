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

object SourceRepo:
  /** Delimiter for building text[] via string_to_array without an array codec (won't occur in data). */
  private val Sep = "|~|"

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Create or replace a source, its findings, its edges AND its FTS body chunks (plan §1.4).
    * `chunkChars` is `histdb.limits.chunk-chars` — passed in, never read from config here, so this
    * stays a pure function of its arguments. Anything else that writes `ocr_markdown` must call
    * [[replaceChunks]] too, or the index goes stale. */
  def upsert(ds: DataSource, s: Source, chunkChars: Int): Unit =
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

      val _ = replaceChunks(s.citekey, s.ocrMarkdown, chunkChars)

  /** Delete and rewrite one source's FTS chunks. Returns how many chunks were written.
    * Batched: the corpus is ~21 000 chunks, and one round trip per chunk makes a full reindex take
    * minutes (long enough for the server's request timeout to cut it off). */
  private def replaceChunks(citekey: String, markdown: Option[String], chunkChars: Int)(using DbCon): Int =
    sql"DELETE FROM source_chunks WHERE citekey = $citekey".update.run()
    val chunks = markdown.map(Chunker.chunk(_, chunkChars)).getOrElse(Nil)
    if chunks.nonEmpty then
      val _ = batchUpdate(chunks)(c =>
        sql"""INSERT INTO source_chunks (citekey, ord, heading, body)
              VALUES ($citekey, ${c.ord}, ${c.heading}, ${c.body})""".update)
    chunks.size

  /** Rebuild the chunk index — `POST /api/reindex`. Needed once after V002 (the existing corpus was
    * imported before chunking existed) and after any bulk edit of `ocr_markdown` that bypassed
    * [[upsert]]. One transaction per source, so an 89 MB corpus never sits in memory.
    *
    * RESUMABLE, because the whole corpus takes minutes (Postgres spends ~2 ms per chunk on the
    * generated tsvector plus the GIN insert — 38 000 chunks ≈ 2.5 min) and the server's request
    * timeout is 20 s: sources are walked in citekey order from `after`, and the call stops once
    * `maxChars` of body text have been re-chunked (always at least one source, so progress is
    * guaranteed). The result carries the cursor and how many sources are left. */
  def reindexChunks(
      ds: DataSource,
      chunkChars: Int,
      after: Option[String] = None,
      maxSources: Option[Int] = None,
      maxChars: Long = Long.MaxValue
  ): ReindexResult =
    val pending = connect(ds):
      sql"""SELECT citekey, coalesce(length(ocr_markdown), 0)
            FROM sources WHERE $after IS NULL OR citekey > $after
            ORDER BY citekey""".query[(String, Int)].run()

    // take sources until the character budget is spent (never fewer than one)
    val batch = pending
      .scanLeft(("", 0L)) { case ((_, spent), (ck, len)) => (ck, spent + len.toLong) }
      .drop(1)
      .takeWhile((_, spent) => spent <= maxChars)
      .map(_._1)
      .take(maxSources.getOrElse(Int.MaxValue))
    val take = if batch.isEmpty then pending.take(1).map(_._1) else batch

    val counts = take.map { ck =>
      transact(ds):
        val md = sql"SELECT ocr_markdown FROM sources WHERE citekey = $ck".query[Option[String]].run()
        replaceChunks(ck, md.headOption.flatten, chunkChars)
    }
    ReindexResult(take.size, counts.sum, take.lastOption, pending.size - take.size)

  /** Rebuild every chunk in one go (tests, offline tools) — no budget, so it can run for minutes. */
  def reindexAll(ds: DataSource, chunkChars: Int): ReindexResult =
    @annotation.tailrec
    def go(after: Option[String], sources: Int, chunks: Int): ReindexResult =
      val r = reindexChunks(ds, chunkChars, after)
      val (s, c) = (sources + r.sources, chunks + r.chunks)
      if r.remaining > 0 && r.lastCitekey.isDefined then go(r.lastCitekey, s, c)
      else ReindexResult(s, c, r.lastCitekey, r.remaining)
    go(None, 0, 0)

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
  /** The `SearchHit` projection, minus the two FTS columns (which differ per branch). */
  private val hitCols: String =
    "s.citekey, s.title, s.year, s.status, " +
      "array_to_string(s.topics, ', '), array_to_string(s.regions, ', '), " +
      "lower(s.period), upper(s.period) - 1, s.summary"

  /** Full-text search (plan §1.4). A source matches on its weighted title/summary vector or on any of
    * its body chunks; hits carry the `ts_headline` snippet of the best-matching chunk and that
    * chunk's heading. No query → the "most cited" listing, unchanged.
    * `snippetWords` is `limits.snippetWords`; `websearch` syntax: `"phrase"`, `-not`, `or`. */
  def search(ds: DataSource, query: Option[String], limit: Int, snippetWords: Int): Vector[SearchHit] =
    connect(ds):
      query match
        case Some(term) =>
          val q = Sql.raw(s"SELECT $hitCols, fts.snippet, fts.heading FROM sources s ") ++
            Search.bestChunkJoin(term, snippetWords) ++
            " WHERE " ++ Search.ftsPredicate(term) ++
            " ORDER BY " ++ Search.rankExpr(term) ++
            Sql(" DESC, s.cited_by_count DESC NULLS LAST LIMIT ?", List(limit))
          q.frag.query[SearchHit].run()
        case None =>
          Sql(s"SELECT $hitCols, NULL::text, NULL::text FROM sources s " +
                "ORDER BY s.cited_by_count DESC NULLS LAST LIMIT ?", List(limit))
            .frag.query[SearchHit].run()

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
