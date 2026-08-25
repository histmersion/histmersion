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
  def upsert(ds: DataSource, s: Source, chunkChars: Int,
      toleranceKm: Double = PlaceRepo.DefaultToleranceKm): Unit =
    val _ = put(ds, s, chunkChars, toleranceKm)

  /** Full replace (`put_source`). Returns the fresh `updatedAt` concurrency token.
    * `chunkChars` (config `histdb.limits.chunk-chars`) sizes the FTS body chunks rewritten with it. */
  def put(ds: DataSource, s: Source, chunkChars: Int,
      toleranceKm: Double = PlaceRepo.DefaultToleranceKm): String =
    transact(ds):
      write(s, chunkChars, toleranceKm)
      token(s.citekey).getOrElse("")

  /** Selective merge (`patch_source`): read → [[Write.applyPatch]] → write, all in one transaction, so
    * two concurrent patches cannot interleave. The precondition is checked against the row we just
    * read inside that transaction, which is what makes `ifUpdatedAt` meaningful. */
  def patch(ds: DataSource, citekey: String, p: SourcePatch, chunkChars: Int,
      toleranceKm: Double = PlaceRepo.DefaultToleranceKm): Either[ApiError, String] =
    transact(ds):
      // FOR UPDATE, not a plain read: `ifUpdatedAt` is a compare-and-swap, and two patches that both
      // read the row before either wrote it BOTH saw the token they were given and BOTH succeeded —
      // the second silently discarding the first. READ COMMITTED gives no protection on its own; the
      // row lock does, and it is taken before the token is compared.
      lockRow(citekey)
      load(citekey) match
        case None => Left(Write.notFound(citekey))
        case Some(existing) =>
          Write.precondition(existing, p) match
            case Left(msg) => Left(ApiError(msg, Some(Write.CodePrecondition)))
            case Right(_)  =>
              Write.applyPatch(existing, p) match
                case Left(msg)      => Left(ApiError(msg, Some(Write.CodeInvalid)))
                case Right(updated) =>
                  write(updated, chunkChars, toleranceKm); Right(token(citekey).getOrElse(""))

  /** Take the row lock the patch's compare-and-swap needs. A missing row is not an error here —
    * `load` reports the 404 — so nothing is read out of it. */
  private def lockRow(citekey: String)(using DbCon): Unit =
    val _ = sql"SELECT citekey FROM sources WHERE citekey = $citekey FOR UPDATE".query[String].run()

  /** The write itself, inside the caller's transaction. */
  private def write(s: Source, chunkChars: Int, toleranceKm: Double)(using DbCon): Unit =
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

    // UPSERT the findings; delete only the ids this write no longer carries.
    //
    // It used to be DELETE-everything-then-INSERT, and `finding_places.finding_id` is
    // `REFERENCES findings(id) ON DELETE CASCADE` — so EVERY write, `patch_source {notes}`
    // included, silently threw away the gazetteer assignment of every finding of that source. The
    // atlas then showed the source as "unlocated" until somebody ran `places rebuild`.
    val keep = s.findings.map(_.id).mkString(Sep)
    sql"""DELETE FROM findings
          WHERE citekey = ${s.citekey}
            AND NOT (id = ANY(COALESCE(string_to_array(NULLIF($keep, ''), $Sep), '{}'::text[])))"""
      .update.run()
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
                    ${f.confidence})
            ON CONFLICT (id) DO UPDATE SET
              citekey = EXCLUDED.citekey, claim = EXCLUDED.claim,
              region_name = EXCLUDED.region_name, geom = EXCLUDED.geom, period = EXCLUDED.period,
              period_name = EXCLUDED.period_name, tags = EXCLUDED.tags,
              confidence = EXCLUDED.confidence""".update.run()
    }
    // …and re-materialise this citekey's gazetteer assignment in the SAME transaction, so a moved
    // or newly-added finding is located the moment the write commits.
    val _ = PlaceRepo.materialiseFor(s.citekey, toleranceKm)

    sql"DELETE FROM edges WHERE src = ${s.citekey}".update.run()
    s.edges.foreach { e =>
      sql"""INSERT INTO edges (src, dst, kind) VALUES (${s.citekey}, ${e.dst}, ${e.kind})
            ON CONFLICT DO NOTHING""".update.run()
    }
    val _ = replaceChunks(s.citekey, s.ocrMarkdown, chunkChars) // FTS index follows every body write

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


  /** Delete and rewrite one source's FTS chunks. Returns how many chunks were written.
    * Batched: the corpus is ~21 000 chunks, and one round trip per chunk makes a full reindex take
    * minutes (long enough for the server's request timeout to cut it off). */
  private[db] def replaceChunks(citekey: String, markdown: Option[String], chunkChars: Int)(using DbCon): Int =
    sql"DELETE FROM source_chunks WHERE citekey = $citekey".update.run()
    val chunks = markdown.map(Chunker.chunk(_, chunkChars)).getOrElse(Nil)
    if chunks.nonEmpty then
      val _ = batchUpdate(chunks)(c =>
        sql"""INSERT INTO source_chunks (citekey, ord, heading, body)
              VALUES ($citekey, ${c.ord}, ${c.heading}, ${c.body})""".update)
    chunks.size

  /** Rebuild the chunk index — `POST /api/reindex`. Needed once after V004 (the existing corpus was
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

  /** Resolve wikilink targets to real citekeys, the same way [[resolveEdges]] resolves edge targets:
    * an exact citekey wins, otherwise the UNIQUE `<target>_%` prefix match does. The wiki writes
    * `[[allentoft2015]]` for `allentoft2015_population`, so without this every citation in every
    * compiled article is a dead link.
    *
    * Ambiguous targets are deliberately absent from the result — two candidates means the renderer
    * must not guess, and `lint --rule unresolved-short-citekeys` is where a human picks one.
    * One query for the whole body, whatever its number of links. */
  def resolveAliases(ds: DataSource, targets: List[String]): Map[String, String] =
    val wanted = targets.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Map.empty
    else
      val joined = wanted.mkString(Sep)
      val rows = connect(ds):
        sql"""SELECT t.target, s.citekey
              FROM unnest(string_to_array($joined, $Sep)) AS t(target)
              JOIN sources s
                ON s.citekey = t.target OR s.citekey LIKE t.target || '\\_%' ESCAPE '\\'
           """.query[(String, String)].run()
      rows.groupBy(_._1).flatMap { (target, cands) =>
        val keys = cands.map(_._2).distinct.toList
        if keys.contains(target) then Some(target -> target)
        else keys match
          case one :: Nil => Some(target -> one)
          case _          => None       // ambiguous: a human picks, the renderer does not guess
      }

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
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
  def findingsFor(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Vector[GeoHit] =
    connect(ds)(FilterSql.findingsFeedQuery(filter, scope).frag.query[GeoHit].run())

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
      tags: String,
      /** Comma-joined gazetteer labels / ids this finding was materialised into (plan §1.6a). */
      places: String = "",
      placeIds: String = ""
  ):
    /** INTERNAL shape: `tags`/`places` still `|~|`-joined, so `findingWhy` compares exact values.
      * `FilterSql.wireMatch` converts it for the wire once the matching is done. */
    def toMatch: FindingMatch =
      FindingMatch(id, claim, regionName, lon, lat, periodStart, periodEnd, periodName, tags, Nil, places)
    def assignedPlaceIds: List[String] = FilterSql.parts(placeIds)

  /** THE search (plan §1.0/§1.3/§1.4): the sources matching the filter, each with the findings that
    * made it match, why it matched, and — for a text query — the passage that matched (`snippet` +
    * `matchedHeading`, from the best-scoring body chunk). `total` is count(DISTINCT source) for the
    * whole filter, ignoring limit/offset. `filter.limit` must already be resolved against the
    * configured default, and `snippetWords` is `limits.snippetWords` (the handler does both). */
  def sources(ds: DataSource, filter: Filter, snippetWords: Int,
      scope: PlaceScope = PlaceScope.empty): SearchResult =
    connect(ds):
      val total = FilterSql.countQuery(filter, scope).frag.query[Int].run().headOption.getOrElse(0)
      val rows  = FilterSql.sourcesQuery(filter, snippetWords, scope).frag.query[FilterSql.SourceRow].run()
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
                val why = FilterSql.findingWhy(filter, m, fr.assignedPlaceIds, scope)
                if why.isEmpty then None else Some(FilterSql.wireMatch(m).copy(why = why))
              }
          Hit(r.toHit, matched, FilterSql.sourceWhy(filter, r))
        }
        SearchResult(total, hits.toList)

  /** Facet vocabulary of one dimension within the current filter; counts are DISTINCT sources. */
  def facets(ds: DataSource, field: FacetField, prefix: Option[String], filter: Filter, limit: Int,
      scope: PlaceScope = PlaceScope.empty): Vector[Facet] =
    connect(ds):
      FilterSql.facetsQuery(field, prefix, filter, limit, scope).frag.query[Facet].run()
