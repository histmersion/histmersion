package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Magnum codecs for the shared wire DTOs (defined in core, DB-agnostic).
  * Types whose fields changed from String to List[String] or Option[Confidence] use internal row
  * types with conversions; the rest derive directly. */
given DbCodec[SourceRepo.GeoFeedRow] = DbCodec.derived  // confidence reads via DbCodec[Confidence]
given DbCodec[NeighborRow]       = DbCodec.derived
given DbCodec[ContradictionRow]  = DbCodec.derived
given DbCodec[Facet]             = DbCodec.derived
given DbCodec[TocEntry]              = DbCodec.derived
given DbCodec[FilterSql.SourceRow]   = DbCodec.derived
given DbCodec[FilterSql.SnippetRow]  = DbCodec.derived
given DbCodec[SourceRepo.FindingHitRow] = DbCodec.derived

/** Flat projections used only by the write paths (Magnum maps a SELECT's columns positionally).
  * `text[]` columns decode straight into `List[String]` through [[Codecs]]. */
private[db] final case class FullSourceRow(
    citekey: String, title: String, authors: List[String], doi: Option[String], year: Option[Int],
    venue: Option[String], status: SourceStatus, topics: List[String], regions: List[String],
    tags: List[String], periodStart: Option[Long], periodEnd: Option[Long], summary: Option[String],
    citedByCount: Option[Int], influentialCitations: Option[Int], provenance: Option[String],
    calibreId: Option[String], ocrMarkdown: Option[String], notes: Option[String], updatedAt: String
)
private[db] final case class FindingIdRow(
    id: String, claim: String, regionName: Option[String], lon: Option[Double], lat: Option[Double],
    periodStart: Option[Long], periodEnd: Option[Long], periodName: Option[String], tags: List[String],
    confidence: Option[Confidence], significance: Option[String]
)
private[db] final case class EdgeRow(src: String, dst: String, kind: String)
private[db] final case class CitekeyMillis(citekey: String, millis: Long)

given DbCodec[FullSourceRow]    = DbCodec.derived
given DbCodec[FindingIdRow]     = DbCodec.derived
given DbCodec[EdgeRow]          = DbCodec.derived
given DbCodec[CitekeyMillis]    = DbCodec.derived
given DbCodec[SourceDetail]     = DbCodec.derived
given DbCodec[FindingRow]       = DbCodec.derived
given DbCodec[SourceRef]        = DbCodec.derived

object SourceRepo:
  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Full replace of a source and everything under it, in ONE transaction. Kept as the original
    * `Unit` signature; [[put]] is the same write returning the new `updated_at` token. */
  def upsert(ds: DataSource, s: Source, chunkChars: Int,
      toleranceKm: Double = PlaceRepo.DefaultToleranceKm): Unit =
    val _ = put(ds, s, chunkChars, toleranceKm)

  /** Full replace (`put_source`). Returns the fresh `updatedAt` concurrency token.
    * `chunkChars` (config `histmersion.limits.chunk-chars`) sizes the FTS body chunks rewritten with it. */
  def put(ds: DataSource, s: Source, chunkChars: Int,
      toleranceKm: Double = PlaceRepo.DefaultToleranceKm): String =
    transact(ds):
      write(s, chunkChars, toleranceKm)
      token(s.citekey).getOrElse("")

  /** Selective merge (`patch_source`): read → [[Write.applyPatch]] → write, all in one transaction, so
    * two concurrent patches cannot interleave. The precondition is checked against the row we just
    * read inside that transaction, which is what makes `ifUpdatedAt` meaningful. */
  def patch(ds: DataSource, requested: String, p: SourcePatch, chunkChars: Int,
      toleranceKm: Double = PlaceRepo.DefaultToleranceKm): Either[ApiError, String] =
    transact(ds):
      // an OLD citekey (V027) patches the source it was renamed to — see [[canonical]]
      val citekey = canonical(requested).getOrElse(requested)
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
              Write.applyPatch(existing, Write.srcAliased(p, requested, citekey)) match
                case Left(msg)      => Left(ApiError(msg, Some(Write.CodeInvalid)))
                case Right(updated) =>
                  write(updated, chunkChars, toleranceKm); Right(token(citekey).getOrElse(""))

  /** Take the row lock the patch's compare-and-swap needs. A missing row is not an error here —
    * `load` reports the 404 — so nothing is read out of it. */
  private def lockRow(citekey: String)(using DbCon): Unit =
    val _ = sql"SELECT citekey FROM sources WHERE citekey = $citekey FOR UPDATE".query[String].run()

  /** The write itself, inside the caller's transaction. */
  private def write(s: Source, chunkChars: Int, toleranceKm: Double)(using DbCon): Unit =
    // Creating a source under an OLD citekey would make the alias and a new row answer to the same
    // name. The handlers refuse it with a readable 400 first; this is the net under every other
    // writer (a seed of a stale `<old>.md`, an ingest job queued before the rename).
    aliasTarget(s.citekey).foreach { now =>
      throw IllegalStateException(s"'${s.citekey}' was renamed to '$now' — write to '$now' instead")
    }
    val status    = s.status.toString.toLowerCase
    val periodLit = PeriodSql.literal(s.period)
    sql"""INSERT INTO sources
            (citekey, title, authors, year, venue, status, topics, regions, tags,
             period, summary, ocr_markdown, notes, source, calibre_id, doi,
             cited_by_count, influential_citations)
          VALUES
            (${s.citekey}, ${s.title}, ${s.authors}::text[], ${s.year}, ${s.venue}, $status,
             ${s.topics}::text[], ${s.regions}::text[], ${s.tags}::text[],
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
    // web UI then showed the source as "unlocated" until somebody ran `places rebuild`.
    val keep = s.findings.map(_.id)
    sql"""DELETE FROM findings
          WHERE citekey = ${s.citekey} AND NOT (id = ANY($keep::text[]))"""
      .update.run()
    s.findings.foreach { f =>
      val fperiod = PeriodSql.literal(f.period)
      val lon     = f.geo.map(_.lon)
      val lat     = f.geo.map(_.lat)
      sql"""INSERT INTO findings (id, citekey, claim, region_name, geom, period, period_name, tags, confidence, significance)
            VALUES (${f.id}, ${s.citekey}, ${f.claim}, ${f.region},
                    ST_SetSRID(ST_MakePoint($lon, $lat), 4326),
                    ${fperiod}::int8range, ${f.periodName},
                    ${f.tags}::text[],
                    ${f.confidence}, ${f.significance})
            ON CONFLICT (id) DO UPDATE SET
              citekey = EXCLUDED.citekey, claim = EXCLUDED.claim,
              region_name = EXCLUDED.region_name, geom = EXCLUDED.geom, period = EXCLUDED.period,
              period_name = EXCLUDED.period_name, tags = EXCLUDED.tags,
              confidence = EXCLUDED.confidence, significance = EXCLUDED.significance""".update.run()
    }
    // …and re-materialise this citekey's gazetteer assignment in the SAME transaction, so a moved
    // or newly-added finding is located the moment the write commits.
    val _ = PlaceRepo.materialiseFor(s.citekey, toleranceKm)

    sql"DELETE FROM edges WHERE src = ${s.citekey}".update.run()
    sql"DELETE FROM unresolved_edges WHERE src = ${s.citekey}".update.run()
    val _ = insertEdges(s.edges.map(_.copy(src = s.citekey)))
    promote(s.citekey)
    val _ = replaceChunks(s.citekey, s.ocrMarkdown, chunkChars) // FTS index follows every body write

  /** Store edges, routed by their target (V026): an edge to a stored source goes into `edges`, one
    * to anything else — a work not ingested yet, a short form, a DOI — is parked in
    * `unresolved_edges` until [[promote]] or [[resolveEdges]] finds its source. The routing lives
    * HERE only; every writer (`put`/`patch`, `refs --apply`, `followups --apply`) comes through it.
    * Returns how many rows were new. */
  private[db] def insertEdges(edges: List[Edge])(using DbCon): Int =
    val es = edges.distinct
    if es.isEmpty then 0
    else
      // an edge to an OLD citekey (V027) is an edge to the source it was renamed to
      val known = canonicalAll(es.map(_.dst).distinct)
      es.map { e =>
        if known.contains(e.dst) then
          sql"""INSERT INTO edges (src, dst, kind) VALUES (${e.src}, ${known(e.dst)}, ${e.kind})
                ON CONFLICT DO NOTHING""".update.run()
        else
          sql"""INSERT INTO unresolved_edges (src, dst, kind) VALUES (${e.src}, ${e.dst}, ${e.kind})
                ON CONFLICT DO NOTHING""".update.run()
      }.sum

  /** A source just arrived: every parked edge that named it exactly becomes a real edge. */
  private[db] def promote(citekey: String)(using DbCon): Unit =
    sql"""INSERT INTO edges (src, dst, kind)
          SELECT src, dst, kind FROM unresolved_edges WHERE dst = $citekey
          ON CONFLICT DO NOTHING""".update.run()
    sql"DELETE FROM unresolved_edges WHERE dst = $citekey".update.run()

  /** The opaque last-write token: `updated_at` rendered UTC-normalised so it is stable to compare and
    * safe to echo back through JSON, whatever the session's TimeZone setting. */
  private def token(citekey: String)(using DbCon): Option[String] =
    sql"""SELECT to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
          FROM sources WHERE citekey = $citekey""".query[String].run().headOption

  /** Read a whole source back as the DOMAIN model (findings + edges included) — the input side of a
    * patch, and what `export` renders. */
  def load(ds: DataSource, citekey: String): Option[Source] = connect(ds)(load(citekey))

  private def load(citekey: String)(using DbCon): Option[Source] =
    sql"""SELECT citekey, title, authors, doi, year, venue, status, topics, regions, tags,
                 ${SqlLiteral(PeriodSql.cols("period"))}, summary, cited_by_count, influential_citations,
                 source, calibre_id, ocr_markdown, notes,
                 to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
          FROM sources WHERE citekey = $citekey""".query[FullSourceRow].run().headOption.map { r =>
      val fs = sql"""SELECT id, claim, region_name, ST_X(geom), ST_Y(geom),
                            ${SqlLiteral(PeriodSql.cols("period"))}, period_name,
                            tags, confidence, significance
                     FROM findings WHERE citekey = $citekey ORDER BY id""".query[FindingIdRow].run()
      // the DOMAIN edge list is both tables: where an edge is stored is a storage concern, and a
      // patch or an export must not lose the parked ones
      val es = sql"""SELECT src, dst, kind FROM edges WHERE src = $citekey
                     UNION ALL
                     SELECT src, dst, kind FROM unresolved_edges WHERE src = $citekey
                     ORDER BY kind, dst""".query[EdgeRow].run()
      Source(
        citekey = r.citekey, title = r.title, authors = r.authors, doi = r.doi, year = r.year,
        venue = r.venue, status = r.status, topics = r.topics, regions = r.regions, tags = r.tags,
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
            f.periodName, f.tags, f.confidence, f.significance)
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

  /** Promote parked edges (`unresolved_edges`, V026) whose target now resolves to a stored source —
    * exactly, or by the UNIQUE `<target>_%` prefix (`heggarty2023` → `heggarty2023_language`), the
    * rule [[resolveAliases]] applies to wikilinks. Run after a full import and by `edges/resolve`.
    * Ambiguous targets stay parked for a human (`lint --rule unresolved-short-citekeys`). Returns
    * how many edges were promoted. */
  def resolveEdges(ds: DataSource): Int =
    transact(ds):
      val parked  = sql"SELECT src, dst, kind FROM unresolved_edges".query[EdgeRow].run().toList
      val aliases = resolveAliases(parked.map(_.dst))
      val moves   = parked.flatMap(e => aliases.get(e.dst.trim.toLowerCase).filter(_ != e.src).map(e -> _))
      moves.foreach { (e, ck) =>
        sql"""INSERT INTO edges (src, dst, kind) VALUES (${e.src}, $ck, ${e.kind})
              ON CONFLICT DO NOTHING""".update.run()
        sql"DELETE FROM unresolved_edges WHERE src = ${e.src} AND dst = ${e.dst} AND kind = ${e.kind}"
          .update.run()
      }
      moves.size

  /** Resolve wikilink targets to real citekeys, the same way [[resolveEdges]] resolves edge targets:
    * an exact citekey wins, otherwise the UNIQUE `<target>_%` prefix match does. The wiki writes
    * `[[allentoft2015]]` for `allentoft2015_population`, so without this every citation in every
    * compiled article is a dead link.
    *
    * Ambiguous targets are deliberately absent from the result — two candidates means the renderer
    * must not guess, and `lint --rule unresolved-short-citekeys` is where a human picks one.
    * One query for the whole body, whatever its number of links. */
  def resolveAliases(ds: DataSource, targets: List[String]): Map[String, String] =
    connect(ds)(resolveAliases(targets))

  private[db] def resolveAliases(targets: List[String])(using DbCon): Map[String, String] =
    val wanted = targets.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Map.empty
    else
      // precedence: the citekey itself (0), an OLD citekey renamed away (1, V027), a short form (2)
      val rows =
        sql"""SELECT t.target, s.citekey, CASE WHEN s.citekey = t.target THEN 0 ELSE 2 END
              FROM unnest($wanted::text[]) AS t(target)
              JOIN sources s
                ON s.citekey = t.target OR s.citekey LIKE t.target || '\\_%' ESCAPE '\\'
              UNION ALL
              SELECT t.target, a.citekey, 1
              FROM unnest($wanted::text[]) AS t(target)
              JOIN source_aliases a ON a.alias = t.target
           """.query[(String, String, Int)].run()
      rows.groupBy(_._1).flatMap { (target, cands) =>
        val best = cands.map(_._3).min
        cands.filter(_._3 == best).map(_._2).distinct.toList match
          case one :: Nil => Some(target -> one)
          case _          => None       // ambiguous: a human picks, the renderer does not guess
      }

  // ── renaming a citekey (V027) ──────────────────────────────────────────────────────────────

  /** THE resolution of a citekey somebody asked for: the citekey itself when a source has it, else
    * the source an OLD citekey was renamed to, else `None`. Every read and write that takes a citekey
    * goes through this (the server's `Handlers.canonical`, and [[patch]]), so an old id keeps working
    * everywhere at once. Exact only: the short-form prefix rule belongs to wikilinks
    * ([[resolveAliases]]), not to ids in URLs. */
  def canonical(ds: DataSource, id: String): Option[String] = connect(ds)(canonical(id))

  private[db] def canonical(id: String)(using DbCon): Option[String] =
    canonicalAll(List(id)).get(id)

  /** [[canonical]] for many ids in one query; an id that names nothing is absent. */
  private[db] def canonicalAll(ids: List[String])(using DbCon): Map[String, String] =
    if ids.isEmpty then Map.empty
    else
      sql"""SELECT citekey, citekey, 0 FROM sources WHERE citekey = ANY($ids::text[])
            UNION ALL
            SELECT alias, citekey, 1 FROM source_aliases WHERE alias = ANY($ids::text[])"""
        .query[(String, String, Int)].run().toList
        .groupBy(_._1).map((id, rows) => id -> rows.minBy(_._3)._2)

  /** The source `ck` was renamed to, when `ck` is an old citekey. */
  def aliasOf(ds: DataSource, ck: String): Option[String] = connect(ds)(aliasTarget(ck))

  private def aliasTarget(ck: String)(using DbCon): Option[String] =
    sql"SELECT citekey FROM source_aliases WHERE alias = $ck".query[String].run().headOption

  /** The OLD citekeys that now name `ck` (V027) — an edge written on `ck` may give one as its `src`. */
  def aliasesOf(ds: DataSource, ck: String): Set[String] =
    connect(ds)(sql"SELECT alias FROM source_aliases WHERE citekey = $ck".query[String].run().toSet)

  /** The Calibre book id recorded for a source, if any. */
  def calibreId(ds: DataSource, ck: String): Option[String] =
    connect(ds)(sql"SELECT calibre_id FROM sources WHERE citekey = $ck".query[Option[String]].run().headOption.flatten)

  /** Every old citekey and the source it names now, by alias. */
  def aliases(ds: DataSource): List[SourceAlias] =
    connect(ds)(sql"""SELECT alias, citekey FROM source_aliases ORDER BY alias COLLATE "C""""
      .query[(String, String)].run().toList.map(SourceAlias(_, _)))

  /** Rename a source's citekey, in ONE transaction — all of it happens or none of it does.
    *
    *  - Refused: an invalid or non-link-shaped new key ([[Rename.validate]]), `old` unknown (or
    *    itself an old key — rename what it points to), `new` already a source, `new` an old key of
    *    ANOTHER source, or `new#<n>` finding ids already taken. Renaming back to a key this source
    *    had before is allowed and drops that alias, leaving an `alias_tombstones` row (V050).
    *  - The key moves with one UPDATE; V027's ON UPDATE CASCADE carries every foreign key (findings,
    *    assessments, attestations, chunks, assets and their links, both edge tables, aliases).
    *  - Finding ids `old#n` → `new#n` (cascading to `finding_places`), and the finding ids held
    *    without a key (attestation and asset-link targets).
    *  - Every column that stores a citekey WITHOUT a foreign key: article link targets, asset links
    *    to the source, events and their datings/edges/participants, jobs, place types and
    *    footprints, a place's `source`, attestation targets that embed the key.
    *  - `[[old]]` — and the short forms that resolved to it, `[[beckman2025]]` for
    *    `beckman2025_ahhiyawa` — in every article body/summary, through the article write path (so
    *    each rewrite is a recorded version), and in every source's notes/summary. Labels and
    *    anchors survive ([[ArticleLinks.retarget]]).
    *  - Parked edges that named `new` (or a short form now unique to it) become real edges.
    *  - `old` becomes an alias of `new`, so it keeps resolving. */
  def rename(ds: DataSource, oldCk: String, newRaw: String): Either[ApiError, RenameCounts] =
    Rename.validate(oldCk, newRaw).flatMap(validated => transact(ds)(renameIn(oldCk, validated.value)))

  /** [[rename]] inside the caller's transaction, `newCk` already validated — also what a bundle
    * import does when a source was renamed on the other side (`BundleImport`). */
  private[db] def renameIn(oldCk: String, newCk: String)(using DbCon): Either[ApiError, RenameCounts] =
    lockRow(oldCk)
    def taken(ck: String) = sql"SELECT citekey FROM sources WHERE citekey = $ck".query[String].run().nonEmpty
    val findingIdsTaken =
      sql"SELECT id FROM findings WHERE left(id, ${newCk.length + 1}) = ${newCk + "#"} LIMIT 1"
        .query[String].run().headOption
    if !taken(oldCk) then
      Left(aliasTarget(oldCk) match
        case Some(now) => ApiError(s"'$oldCk' is an old citekey of '$now' — rename '$now' instead",
                            Some(Write.CodeInvalid))
        case None      => Write.notFound(oldCk))
    else if taken(newCk) then
      Left(ApiError(s"'$newCk' is already the citekey of another source", Some(Write.CodeInvalid)))
    else aliasTarget(newCk) match
      case Some(other) if other != oldCk =>
        Left(ApiError(s"'$newCk' is an old citekey of '$other' and still resolves to it — pick another",
          Some(Write.CodeInvalid)))
      case back =>
        findingIdsTaken match
          case Some(id) =>
            Left(ApiError(s"finding id '$id' already exists — '$newCk' would collide with it",
              Some(Write.CodeInvalid)))
          case None =>
            if back.isDefined then
              // renaming back: the alias goes, and its tombstone stays so an import does not bring it
              // back (#827) — `newCk` is this source's citekey again
              sql"DELETE FROM source_aliases WHERE alias = $newCk".update.run()
              sql"""INSERT INTO alias_tombstones (alias, source_uid)
                    SELECT $newCk, uid FROM sources WHERE citekey = $oldCk
                    ON CONFLICT (alias) DO UPDATE SET source_uid = EXCLUDED.source_uid, deleted_at = now()"""
                .update.run()
            Right(renameNow(oldCk, newCk))

  private def renameNow(oldCk: String, newCk: String)(using DbCon): RenameCounts =
    // the short forms that pointed at the old key — decided BEFORE it moves
    val forms  = Rename.shortForms(oldCk)
    val shorts = resolveAliases(forms).collect { case (f, ck) if ck == oldCk => f }.toSet

    // the key itself: ON UPDATE CASCADE (V027) carries every foreign key
    sql"UPDATE sources SET citekey = $newCk, updated_at = now() WHERE citekey = $oldCk".update.run()

    // finding ids, and the finding ids that live in columns without a key
    val (oldHash, cut) = (oldCk + "#", oldCk.length + 1)
    val findings =
      sql"""UPDATE findings SET id = $newCk || substr(id, $cut)
            WHERE citekey = $newCk AND left(id, $cut) = $oldHash""".update.run()

    val columns = List(
      "attestations.target_id (finding)" ->
        sql"""UPDATE attestations SET target_id = $newCk || substr(target_id, $cut)
              WHERE target_type = 'finding' AND left(target_id, $cut) = $oldHash""".update.run(),
      "attestations.target_id (edge, event_dating)" -> retargetAttestations(oldCk, newCk),
      "asset_links.target_id (finding)" ->
        sql"""UPDATE asset_links SET target_id = $newCk || substr(target_id, $cut)
              WHERE target_type = 'finding' AND left(target_id, $cut) = $oldHash""".update.run(),
      "asset_links.target_id (source)" ->
        sql"""UPDATE asset_links SET target_id = $newCk
              WHERE target_type = 'source' AND target_id = $oldCk""".update.run(),
      "article_links.target" ->
        sql"""UPDATE article_links SET target = $newCk
              WHERE target_kind = 'source' AND target = $oldCk""".update.run(),
      "events.citekey" ->
        sql"UPDATE events SET citekey = $newCk WHERE citekey = $oldCk".update.run(),
      // (event_id, citekey) is the key: a dating already recorded under the new name wins
      "event_datings.citekey" ->
        sql"""UPDATE event_datings d SET citekey = $newCk WHERE d.citekey = $oldCk
              AND NOT EXISTS (SELECT 1 FROM event_datings o WHERE o.event_id = d.event_id AND o.citekey = $newCk)"""
          .update.run(),
      "event_edges.citekey" ->
        sql"UPDATE event_edges SET citekey = $newCk WHERE citekey = $oldCk".update.run(),
      "event_participants.citekey" ->
        sql"UPDATE event_participants SET citekey = $newCk WHERE citekey = $oldCk".update.run(),
      "jobs.citekey" ->
        sql"UPDATE jobs SET citekey = $newCk WHERE citekey = $oldCk".update.run(),
      "place_types.citekey" ->
        sql"UPDATE place_types SET citekey = $newCk WHERE citekey = $oldCk".update.run(),
      "place_footprints.citekey" ->
        sql"UPDATE place_footprints SET citekey = $newCk WHERE citekey = $oldCk".update.run(),
      "places.source" ->
        sql"UPDATE places SET source = $newCk WHERE source = $oldCk".update.run()
    ).collect { case (t, n) if n > 0 => RenameCount(t, n) }

    // parked edges that were waiting for the new name, exactly or by a short form now unique to it
    val waiting = sql"""SELECT DISTINCT dst FROM unresolved_edges
                        WHERE dst = $newCk OR left($newCk, length(dst) + 1) = dst || '_'"""
      .query[String].run().toList
    val resolvedNow = resolveAliases(waiting)
    val promoted = waiting.filter(d => resolvedNow.get(d).contains(newCk)).map(promoteAs(_, newCk)).sum

    // the text: `[[old]]` and its short forms, in articles (versioned) and in source notes/summaries
    val to: String => Option[String] = t => Option.when(t == oldCk || shorts(t))(newCk)
    val needle   = oldCk.takeWhile(_ != '_')   // a prefix of the key and of every short form
    val articles = ArticleRepo.retargetLinks(needle, to)
    val sources  = retargetSourceText(needle, to)

    sql"""INSERT INTO source_aliases (alias, citekey) VALUES ($oldCk, $newCk)
          ON CONFLICT (alias) DO UPDATE SET citekey = EXCLUDED.citekey""".update.run()
    // an alias that exists again is no longer deleted
    sql"DELETE FROM alias_tombstones WHERE alias = $oldCk".update.run()

    RenameCounts(findings, articles.map(_._1), sources.map(_._1),
      articles.map(_._2).sum + sources.map(_._2).sum, columns, promoted)

  /** Promote the parked edges whose target is `dst` as edges to `ck` (never a self-edge). */
  private def promoteAs(dst: String, ck: String)(using DbCon): Int =
    val n = sql"""INSERT INTO edges (src, dst, kind)
                  SELECT src, $ck, kind FROM unresolved_edges WHERE dst = $dst AND src <> $ck
                  ON CONFLICT DO NOTHING""".update.run()
    sql"DELETE FROM unresolved_edges WHERE dst = $dst AND src <> $ck".update.run()
    n

  /** Attestation targets that EMBED a citekey (an edge, a per-source dating): the key is replaced
    * as a whole token only ([[Rename.replaceToken]]). */
  private def retargetAttestations(oldCk: String, newCk: String)(using DbCon): Int =
    sql"""SELECT id, target_id FROM attestations
          WHERE target_type IN ('edge', 'event_dating') AND strpos(target_id, $oldCk) > 0"""
      .query[(Long, String)].run().toList
      .map((id, t) => (id, Rename.replaceToken(t, oldCk, newCk), t))
      .collect { case (id, next, was) if next != was =>
        sql"UPDATE attestations SET target_id = $next WHERE id = $id".update.run() }
      .sum

  /** Rewrite links in every source's notes and summary; returns (citekey, links moved) per source
    * that changed. A plain UPDATE, not [[write]]: nothing but the text changes, so the findings,
    * chunks and edges need no rewrite. */
  private def retargetSourceText(needle: String, to: String => Option[String])(using DbCon): List[(String, Int)] =
    sql"""SELECT citekey, notes, summary FROM sources
          WHERE strpos(lower(coalesce(notes, '') || ' ' || coalesce(summary, '')), $needle) > 0
          ORDER BY citekey"""
      .query[(String, Option[String], Option[String])].run().toList
      .flatMap { (ck, notes, summary) =>
        val n = notes.map(ArticleLinks.retarget(_, to))
        val s = summary.map(ArticleLinks.retarget(_, to))
        val moved = n.fold(0)(_._2) + s.fold(0)(_._2)
        Option.when(moved > 0) {
          sql"""UPDATE sources SET notes = ${n.map(_._1)}, summary = ${s.map(_._1)}, updated_at = now()
                WHERE citekey = $ck""".update.run()
          ck -> moved
        }
      }

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  /** The citation-link view of the given stored sources (unknown citekeys are simply absent) — what a
    * page needs to render a reference with its DOI, byline and status. One query, in citekey order. */
  def refs(ds: DataSource, citekeys: List[String]): List[SourceRef] =
    val wanted = citekeys.distinct
    if wanted.isEmpty then Nil
    else
      connect(ds):
        sql"""SELECT citekey, doi, authors, year, title, status FROM sources
              WHERE citekey = ANY($wanted::text[]) ORDER BY citekey""".query[SourceRef].run().toList

  def get(ds: DataSource, citekey: String): Option[(SourceDetail, Vector[FindingRow])] =
    connect(ds):
      val src = sql"""SELECT citekey, title, authors, year, venue, status, topics, regions, tags,
                             ${SqlLiteral(PeriodSql.cols("period"))},
                             summary, doi, ocr_markdown, notes,
                             to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                             source, uid, origin
                      FROM sources WHERE citekey = $citekey""".query[SourceDetail].run().headOption
      src.map { r =>
        val fs = sql"""SELECT claim, region_name, ST_X(geom), ST_Y(geom),
                              ${SqlLiteral(PeriodSql.cols("period"))}, period_name,
                              tags, confidence, significance, uid
                       FROM findings WHERE citekey = $citekey ORDER BY id""".query[FindingRow].run()
        (r, fs)
      }

  /** Every edge touching `citekey`, oriented relative to it, with the far end CLASSIFIED — the one
    * place [[NeighborTarget]] is decided. `edges` holds source→source only (V026), so those rows are
    * `source`; a parked edge (`unresolved_edges`) is an `article` when a compiled article has that
    * slug, else `unresolved`. Incoming edges are always from stored sources. `kinds` narrows by edge
    * kind; empty means all. */
  def neighbors(ds: DataSource, citekey: String, kinds: List[String] = Nil): Vector[NeighborRow] =
    val all = kinds.isEmpty
    connect(ds):
      sql"""SELECT 'out' AS direction, e.kind, e.dst AS citekey, s.title, s.status, 'source' AS target
            FROM edges e JOIN sources s ON s.citekey = e.dst
            WHERE e.src = $citekey AND ($all OR e.kind = ANY($kinds::text[]))
            UNION ALL
            SELECT 'out', u.kind, u.dst, a.title, NULL,
                   CASE WHEN a.slug IS NULL THEN 'unresolved' ELSE 'article' END
            FROM unresolved_edges u LEFT JOIN articles a ON a.slug = u.dst
            WHERE u.src = $citekey AND ($all OR u.kind = ANY($kinds::text[]))
            UNION ALL
            SELECT 'in', e.kind, e.src, s.title, s.status, 'source'
            FROM edges e JOIN sources s ON s.citekey = e.src
            WHERE e.dst = $citekey AND ($all OR e.kind = ANY($kinds::text[]))
            ORDER BY 1, 2, 3""".query[NeighborRow].run()

  /** The citation graph around `citekey`, breadth-first to `maxDepth` (capped at 4) in both
    * directions. Every link joins two stored sources (V026's foreign keys), and a link is kept only
    * when BOTH ends are nodes of the result — the depth cut-off never leaves a dangling line. */
  def graph(ds: DataSource, citekey: String, maxDepth: Int): SourceGraph =
    val depth = maxDepth.max(0).min(4)
    connect(ds):
      @annotation.tailrec
      def walk(d: Int, frontier: List[String], nodes: Vector[GraphNode], links: Set[GraphLink]): (Vector[GraphNode], Set[GraphLink]) =
        if frontier.isEmpty then (nodes, links)
        else
          val found = sql"""SELECT citekey, title, year, status FROM sources
                            WHERE citekey = ANY($frontier::text[]) ORDER BY citekey"""
            .query[(String, Option[String], Option[Int], Option[SourceStatus])].run()
            .map((ck, t, y, st) => GraphNode(ck, t, y, st, d))
          val grown = nodes ++ found
          if d >= depth then (grown, links)
          else
            val touching = sql"""SELECT src, dst, kind FROM edges
                                 WHERE src = ANY($frontier::text[]) OR dst = ANY($frontier::text[])"""
              .query[EdgeRow].run().map(e => GraphLink(e.src, e.dst, e.kind))
            val seen = grown.iterator.map(_.id).toSet
            val next = touching.iterator.flatMap(l => List(l.source, l.target)).filterNot(seen).toList.distinct
            walk(d + 1, next, grown, links ++ touching)
      val (nodes, links) = walk(0, List(citekey), Vector.empty, Set.empty)
      val ids = nodes.iterator.map(_.id).toSet
      SourceGraph(citekey, depth,
        nodes.toList,
        links.toList.filter(l => ids(l.source) && ids(l.target)).sortBy(l => (l.source, l.target, l.kind)))

  def contradictions(ds: DataSource, citekey: Option[String], since: Option[Long]): ContradictionsResult =
    connect(ds):
      citekey match
        case Some(ck) => contradictionsFor(ck)
        case None     => contradictionsAll(since)

  private def contradictionsFor(ck: String)(using DbCon): ContradictionsResult =
    val rows = sql"""SELECT s.citekey, s.title, s.year, coalesce(s.status, 'unassessed'),
                            e.direction, e.kind, o.citekey, o.title, o.year, coalesce(o.status, 'unassessed')
                     FROM (
                       SELECT 'out' AS direction, e.kind, e.dst AS other_ck
                       FROM edges e WHERE e.src = $ck AND e.kind IN ('challenges','refutes','updates','superseded_by')
                       UNION ALL
                       SELECT 'in' AS direction, e.kind, e.src AS other_ck
                       FROM edges e WHERE e.dst = $ck AND e.kind IN ('challenges','refutes','updates','superseded_by')
                     ) e
                     JOIN sources s ON s.citekey = $ck
                     LEFT JOIN sources o ON o.citekey = e.other_ck
                     ORDER BY e.direction, e.kind, o.citekey"""
      .query[ContradictionRow].run()
    ContradictionsResult(rows.toList, rows.size)

  private def contradictionsAll(since: Option[Long])(using DbCon): ContradictionsResult =
    val byEdge =
      since match
        case Some(y) =>
          sql"""SELECT s.citekey, s.title, s.year, coalesce(s.status, 'unassessed'),
                       e.direction, e.kind, o.citekey, o.title, o.year, coalesce(o.status, 'unassessed')
                FROM (
                  SELECT src AS citekey, 'out' AS direction, kind, dst AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                  UNION ALL
                  SELECT dst AS citekey, 'in' AS direction, kind, src AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                ) e
                JOIN sources s ON s.citekey = e.citekey
                LEFT JOIN sources o ON o.citekey = e.other_ck
                WHERE o.year IS NULL OR o.year >= $y
                ORDER BY s.citekey, e.direction, e.kind, o.citekey"""
            .query[ContradictionRow].run()
        case None =>
          sql"""SELECT s.citekey, s.title, s.year, coalesce(s.status, 'unassessed'),
                       e.direction, e.kind, o.citekey, o.title, o.year, coalesce(o.status, 'unassessed')
                FROM (
                  SELECT src AS citekey, 'out' AS direction, kind, dst AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                  UNION ALL
                  SELECT dst AS citekey, 'in' AS direction, kind, src AS other_ck
                  FROM edges WHERE kind IN ('challenges','refutes','updates','superseded_by')
                ) e
                JOIN sources s ON s.citekey = e.citekey
                LEFT JOIN sources o ON o.citekey = e.other_ck
                ORDER BY s.citekey, e.direction, e.kind, o.citekey"""
            .query[ContradictionRow].run()
    val byStatus = sql"""SELECT s.citekey, s.title, s.year, s.status,
                                NULL::text, NULL::text, NULL::text, NULL::text, NULL::int, NULL::text
                         FROM sources s
                         WHERE lower(s.status) IN ('contested','superseded','debunked','retracted')
                           AND NOT EXISTS (
                             SELECT 1 FROM edges e
                             WHERE (e.src = s.citekey OR e.dst = s.citekey)
                               AND e.kind IN ('challenges','refutes','updates','superseded_by'))"""
      .query[ContradictionRow].run()
    val all = (byEdge ++ byStatus).toList
    ContradictionsResult(all, all.size)

  /** One map-feed row: a [[GeoHit]] and what [[SourceKinds.of]] reads to say what its source is. */
  final case class GeoFeedRow(citekey: String, title: String, claim: String, regionName: Option[String],
      lon: Option[Double], lat: Option[Double], periodStart: Option[Long], periodEnd: Option[Long],
      confidence: Option[Confidence], significance: Option[String], doi: Option[String],
      venue: Option[String], provenance: Option[String], tags: List[String], topics: List[String], hasCover: Boolean):
    def toHit: GeoHit =
      GeoHit(citekey, title, claim, regionName, lon, lat, periodStart, periodEnd, confidence, significance, doi,
        Some(SourceKinds.of(doi, venue, provenance, tags, topics, hasCover)))

  /** `GET /api/findings` — the MAP FEED, through the ONE filter contract (plan §1.0): the findings
    * that THEMSELVES satisfy the finding-level criteria (period overlap, viewport incl. the
    * antimeridian split, place on `region_name`, tags over `findings.tags ∪ sources.tags`) of the
    * sources that satisfy the source-level ones (`q`, authors, status, publication years, topics).
    * `filter.limit` must already be resolved against the configured default (the handler does that). */
  def findingsFor(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Vector[GeoHit] =
    connect(ds)(FilterSql.findingsFeedQuery(filter, scope).frag.query[GeoFeedRow].run().map(_.toHit))

  /** How many sources the filter selects — `SearchResult.total` without building a page. */
  def count(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Int =
    connect(ds)(FilterSql.countQuery(filter, scope).frag.query[Int].run().headOption.getOrElse(0))

  /** How many findings the filter selects (uncapped — the map feed above stops at its limit). */
  def findingsCount(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Int =
    connect(ds)(FilterSql.findingsCountQuery(filter, scope).frag.query[Int].run().headOption.getOrElse(0))

  /** How many sources, findings, articles and events the filter selects ([[FilterSql.selectionCountsQuery]]). */
  def selectionCounts(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): PlaceCounts =
    connect(ds)(FilterSql.selectionCountsQuery(filter, scope).frag.query[(Int, Int, Int, Int)].run().head match
      case (s, f, a, e) => PlaceCounts(s, f, a, e))

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
      tags: List[String],
      /** The gazetteer labels / ids this finding was materialised into (plan §1.6a). */
      places: List[String] = Nil,
      placeIds: List[String] = Nil
  ):
    def toMatch: FindingMatch =
      FindingMatch(id, claim, regionName, lon, lat, periodStart, periodEnd, periodName, tags, Nil, places)
    def assignedPlaceIds: List[String] = placeIds

  /** THE search (plan §1.0/§1.3/§1.4): the sources matching the filter, each with the findings that
    * made it match, why it matched, and — for a text query — the passage that matched (`snippet` +
    * `matchedHeading`, from the best-scoring body chunk). `total` is count(DISTINCT source) for the
    * whole filter, ignoring limit/offset. `filter.limit` must already be resolved against the
    * configured default, and `snippetWords` is `limits.snippetWords` (the handler does both).
    *
    * Two-phase FTS: `sourcesQuery` uses a CTE to rank sources (one scan of `source_chunks`), then
    * `snippetsQuery` computes `ts_headline` only for the page's citekeys — typically 10–25 instead
    * of every matching source in the corpus. */
  def sources(ds: DataSource, filter: Filter, snippetWords: Int,
      scope: PlaceScope = PlaceScope.empty): SearchResult =
    connect(ds):
      val total = FilterSql.countQuery(filter, scope).frag.query[Int].run().headOption.getOrElse(0)
      val rows  = FilterSql.sourcesQuery(filter, snippetWords, scope).frag.query[FilterSql.SourceRow].run()
      if rows.isEmpty then SearchResult.of(total, Nil, filter.offset.max(0))
      else
        val citekeys = rows.map(_.citekey).toList
        // phase 2: snippets for the page only (the expensive ts_headline runs for N page rows, not all)
        val snippets: Map[String, FilterSql.SnippetRow] =
          FilterSql.text(filter) match
            case Some(q) =>
              FilterSql.snippetsQuery(q, snippetWords, citekeys).frag
                .query[FilterSql.SnippetRow].run().map(s => s.citekey -> s).toMap
            case None => Map.empty
        // one round-trip for the page's findings; which ones count as evidence is decided purely
        val byCk = FilterSql.findingsQuery(citekeys).frag
          .query[FindingHitRow].run().groupBy(_.citekey)
        val hits = rows.map { r =>
          // merge the snippet into the row before building the hit
          val withSnippet = snippets.get(r.citekey) match
            case Some(sr) => r.copy(snippet = Some(sr.snippet), matchedHeading = sr.heading)
            case None     => r
          val matched =
            if !filter.hasFindingCriteria then Nil
            else
              byCk.getOrElse(r.citekey, Vector.empty).toList.flatMap { fr =>
                val m   = fr.toMatch
                val why = FilterSql.findingWhy(filter, m, fr.assignedPlaceIds, scope)
                if why.isEmpty then None else Some(m.copy(why = why))
              }
          Hit(withSnippet.toHit, matched, FilterSql.sourceWhy(filter, withSnippet))
        }
        SearchResult.of(total, hits.toList, filter.offset.max(0))

  /** Facet vocabulary of one dimension within the current filter; counts are DISTINCT sources. */
  def facets(ds: DataSource, field: FacetField, prefix: Option[String], filter: Filter, limit: Int,
      scope: PlaceScope = PlaceScope.empty): Vector[Facet] =
    connect(ds):
      FilterSql.facetsQuery(field, prefix, filter, limit, scope).frag.query[Facet].run()

  // ── get_source sectioning (plan §6 A1/A7/A18) ────────────────────────────────────────────────

  /** Table of contents: distinct headings from source_chunks with first ord and total chars. */
  def toc(ds: DataSource, citekey: String): List[TocEntry] =
    connect(ds):
      sql"""SELECT heading, min(ord), sum(length(body))::int
            FROM source_chunks
            WHERE citekey = $citekey AND heading IS NOT NULL
            GROUP BY heading
            ORDER BY min(ord)""".query[TocEntry].run().toList

  /** Full body text: chunks concatenated in ord order, or raw ocr_markdown when unchunked. */
  def bodyText(ds: DataSource, citekey: String): String =
    connect(ds):
      val chunks = sql"""SELECT body FROM source_chunks
                         WHERE citekey = $citekey ORDER BY ord""".query[String].run()
      if chunks.nonEmpty then chunks.mkString("\n\n")
      else
        sql"SELECT coalesce(ocr_markdown, '') FROM sources WHERE citekey = $citekey"
          .query[String].run().headOption.getOrElse("")

  /** The body as `passages` searches it (#954): the chunks in `ord` order with their headings, or —
    * for a source stored without chunks — the raw `ocr_markdown` as one headingless piece. Joined by
    * [[com.histmersion.domain.PassageFinder.body]] this is exactly [[bodyText]]. */
  def bodyChunks(ds: DataSource, citekey: String): List[Chunk] =
    connect(ds):
      val chunks = sql"""SELECT ord, heading, body FROM source_chunks
                         WHERE citekey = $citekey ORDER BY ord"""
        .query[(Int, Option[String], String)].run().toList.map(Chunk(_, _, _))
      if chunks.nonEmpty then chunks
      else
        sql"SELECT coalesce(ocr_markdown, '') FROM sources WHERE citekey = $citekey"
          .query[String].run().headOption.filter(_.nonEmpty).map(Chunk(0, None, _)).toList

  /** Body text under a specific heading: chunks whose heading matches case-insensitively (prefix). */
  def bodyUnderHeading(ds: DataSource, citekey: String, heading: String): String =
    connect(ds):
      val lower = heading.trim.toLowerCase
      sql"""SELECT body FROM source_chunks
            WHERE citekey = $citekey AND lower(heading) LIKE ${lower + "%"}
            ORDER BY ord""".query[String].run().mkString("\n\n")
