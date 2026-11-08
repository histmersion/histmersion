package com.histmersion
package domain.library.sources

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.catalogue.places.FindingPlacement
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.domain.library.sources.referrers.SourceRename

import com.histmersion.util.database.RangeSql
import com.histmersion.util.ocr.Chunk
import com.histmersion.util.ocr.Chunker
import com.histmersion.api.text.WikiLinks

import com.histmersion.api.{GeoPoint, Write}
import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import scala.util.chaining.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{CalibreGap, Edge, Finding, Rename, Source, TocEntry}
import com.histmersion.api.Api
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Confidence
import com.histmersion.domain.Licence
import com.histmersion.domain.library.search.FilterSql

/** The library's sources: a record with its findings and the edges it makes, written and read whole or by part, its old
  * citekeys, its rename, its full-text chunks and body. Every method runs on the connection or in the transaction its
  * caller opened.
  */
private[sources] object SourceRepo:
  type BodyChars = BodyChars.T
  object BodyChars extends RefinedType[Int, Pure]
  type Count = Count.T
  object Count extends RefinedType[Long, Pure]
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]
  type Name = Name.T
  object Name extends RefinedType[String, Pure]

  private def apiCitekey(ck: Citekey): com.histmersion.api.library.Citekey = com.histmersion.api.library.Citekey(ck.value)

  private def apiToDomain(ck: com.histmersion.api.library.Citekey): Citekey = Citekey.applyUnsafe(ck.value)

  private def apiCitekeyOf(t: WikiLinks.Occurrence.Target): com.histmersion.api.library.Citekey = com.histmersion.api.library.Citekey(t.value)

  private def domainCitekey(ck: com.histmersion.api.library.Citekey): Option[Citekey] = Citekey.either(ck.value).toOption

  import com.histmersion.api.library.SourceColumns.given

  private given DbCodec[TocEntry] = DbCodec.derived
  private given DbCodec[Source.Detail] = DbCodec.derived
  private given DbCodec[Finding.Row] = DbCodec.derived
  private given DbCodec[Source.Ref] = DbCodec.derived

  /** The sources whose chunks contain `name` as a phrase (the FTS index), most chunks first, at most `limit`. */
  def naming(name: Name, limit: Limit)(using DbCon): List[Citekey] =
    sql"""SELECT citekey FROM source_chunks WHERE tsv @@ phraseto_tsquery('english', $name)
            GROUP BY citekey ORDER BY count(*) DESC, citekey LIMIT $limit""".query[Citekey].run().toList

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** The findings of `citekey` placed again (`finding_places`), inside the caller's transaction: what a write that
    * touched the source's findings leaves to do.
    */
  private[histmersion] def placeFindingsIn(citekey: Citekey, toleranceKm: FindingPlacement.Implementation.ToleranceKm)(using DbCon): RowCount =
    FindingPlacement(toleranceKm).placeSource(citekey)

  /** How many sources and findings are stored (what About counts). */
  def counts()(using DbCon): (Count, Count) =
    (
      Count(sql"SELECT count(*)::int FROM sources".query[Int].run().head.toLong),
      Count(sql"SELECT count(*)::int FROM findings".query[Int].run().head.toLong)
    )

  /** [[put]] inside the caller's transaction. */
  def putIn(s: Source, chunkChars: Chunker.ChunkMaxChars, toleranceKm: FindingPlacement.Implementation.ToleranceKm)(using DbCon): Source.UpdatedAt =
    write(s, chunkChars, toleranceKm)
    Source.UpdatedAt(domainCitekey(s.citekey).flatMap(token(_)).getOrElse(""))

  /** [[patch]] inside the caller's transaction. */
  def patchIn(requested: Citekey, p: Source.Patch, chunkChars: Chunker.ChunkMaxChars, toleranceKm: FindingPlacement.Implementation.ToleranceKm)(using
      DbCon
  ): Either[Api.Error, Source.UpdatedAt] =
    // an OLD citekey (V027) patches the source it was renamed to — see [[canonical]]
    val citekey = canonical(requested).getOrElse(requested)
    // FOR UPDATE, not a plain read: `ifUpdatedAt` is a compare-and-swap, and two patches that both
    // read the row before either wrote it BOTH saw the token they were given and BOTH succeeded —
    // the second silently discarding the first. READ COMMITTED gives no protection on its own; the
    // row lock does, and it is taken before the token is compared.
    lockRow(citekey)
    load(citekey) match
      case None           => Left(Write.notFound(apiCitekey(citekey)))
      case Some(existing) =>
        Source.Write.precondition(existing, p) match
          case Left(msg) => Left(Api.Error(Api.Error.Message(msg.value), Some(Api.Error.Code(Write.CodePrecondition))))
          case Right(_)  =>
            Source.Write.applyPatch(existing, Source.Write.srcAliased(p, apiCitekey(requested), apiCitekey(citekey))) match
              case Left(msg)      => Left(Api.Error(Api.Error.Message(msg.value), Some(Api.Error.Code(Write.CodeInvalid))))
              case Right(updated) =>
                write(updated, chunkChars, toleranceKm); Right(Source.UpdatedAt(token(citekey).getOrElse("")))

  /** Take the row lock the patch's compare-and-swap needs. A missing row is not an error here — `load` reports the 404
    * — so nothing is read out of it.
    */
  private def lockRow(citekey: Citekey)(using DbCon): Unit =
    val _ = sql"SELECT citekey FROM sources WHERE citekey = $citekey FOR UPDATE".query[String].run()

  /** The write itself, inside the caller's transaction. */
  private def write(s: Source, chunkChars: Chunker.ChunkMaxChars, toleranceKm: FindingPlacement.Implementation.ToleranceKm)(using DbCon): Unit =
    // Creating a source under an OLD citekey would make the alias and a new row answer to the same
    // name. The handlers refuse it with a readable 400 first; this is the net under every other
    // writer (a seed of a stale `<old>.md`, an ingest job queued before the rename).
    val ck = apiToDomain(s.citekey)
    aliasTarget(ck).foreach { now =>
      throw IllegalStateException(s"'${s.citekey}' was renamed to '${now.value}' — write to '${now.value}' instead")
    }
    val status = s.status.toString.toLowerCase
    val periodLit = RangeSql.literal(s.period)
    sql"""INSERT INTO sources
            (citekey, title, authors, year, venue, status, topics, regions, tags,
             period, summary, ocr_markdown, notes, source, calibre_id, doi,
             cited_by_count, influential_citations)
          VALUES
            (${s.citekey}, ${s.title}, ${s.authors}::text[], ${s.year}, ${s.venue}, $status,
             ${s.topics}::text[], ${s.regions}::text[], ${s.tags}::text[],
             $periodLit::int8range, ${s.summary}, ${s.ocrMarkdown}, ${s.notes},
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
          WHERE citekey = ${s.citekey} AND NOT (id = ANY($keep::text[]))""".update.run()
    s.findings.foreach { f =>
      val fperiod = RangeSql.literal(f.period)
      val lon = f.geo.map(_.lon)
      val lat = f.geo.map(_.lat)
      sql"""INSERT INTO findings (id, citekey, claim, region_name, geom, period, period_name, tags, confidence, significance)
            VALUES (${f.id}, ${s.citekey}, ${f.claim}, ${f.region},
                    ST_SetSRID(ST_MakePoint($lon, $lat), 4326),
                    $fperiod::int8range, ${f.periodName},
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
    val _ = FindingPlacement(toleranceKm).placeSource(ck)

    sql"DELETE FROM edges WHERE src = ${s.citekey}".update.run()
    sql"DELETE FROM unresolved_edges WHERE src = ${s.citekey}".update.run()
    val _ = insertEdges(s.edges.map(_.modify(_.src).setTo(s.citekey)))
    promote(ck)
    val _ = replaceChunks(ck, s.ocrMarkdown.map(o => Chunker.ChunkMarkdown(o.value)), chunkChars) // FTS index follows every body write

  /** Store edges, routed by their target (V026): an edge to a stored source goes into `edges`, one to anything else — a
    * work not ingested yet, a short form, a DOI — is parked in `unresolved_edges` until [[promote]] or [[resolveEdges]]
    * finds its source. The routing lives HERE only; every writer (`put`/`patch`, `refs --apply`, `followups --apply`)
    * comes through it. Returns how many rows were new.
    */
  private[histmersion] def insertEdges(edges: List[Edge])(using DbCon): RowCount =
    val es = edges.distinct
    if es.isEmpty then RowCount(0)
    else
      // an edge to an OLD citekey (V027) is an edge to the source it was renamed to
      val known = canonicalAll(es.flatMap(e => domainCitekey(e.dst)).distinct).map((key, value) => apiCitekey(key) -> value)
      es.map { e =>
        if known.contains(e.dst) then
          sql"""INSERT INTO edges (src, dst, kind) VALUES (${e.src}, ${known(e.dst)}, ${Edge.Kind.wireOf(e.kind)})
                ON CONFLICT DO NOTHING""".update.run()
        else sql"""INSERT INTO unresolved_edges (src, dst, kind) VALUES (${e.src}, ${e.dst}, ${Edge.Kind.wireOf(e.kind)})
                ON CONFLICT DO NOTHING""".update.run()
      }.sum
        .pipe(RowCount(_))

  /** A source just arrived: every parked edge that named it exactly becomes a real edge. */
  private[histmersion] def promote(citekey: Citekey)(using DbCon): Unit =
    sql"""INSERT INTO edges (src, dst, kind)
          SELECT src, dst, kind FROM unresolved_edges WHERE dst = $citekey
          ON CONFLICT DO NOTHING""".update.run()
    sql"DELETE FROM unresolved_edges WHERE dst = $citekey".update.run()

  /** The opaque last-write token: `updated_at` rendered UTC-normalised so it is stable to compare and safe to echo back
    * through JSON, whatever the session's TimeZone setting.
    */
  private def token(citekey: Citekey)(using DbCon): Option[String] =
    sql"""SELECT to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
          FROM sources WHERE citekey = $citekey""".query[String].run().headOption

  def load(citekey: Citekey)(using DbCon): Option[Source] =
    sql"""SELECT citekey, title, authors, doi, year, venue, status, topics, regions, tags,
                 ${SqlLiteral(RangeSql.cols("period"))}, summary, cited_by_count, influential_citations,
                 source, calibre_id, ocr_markdown, notes,
                 to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
          FROM sources WHERE citekey = $citekey""".query[FullSourceRow].run().headOption.map { r =>
      val fs = sql"""SELECT id, claim, region_name, ST_X(geom), ST_Y(geom),
                            ${SqlLiteral(RangeSql.cols("period"))}, period_name,
                            tags, confidence, significance
                     FROM findings WHERE citekey = $citekey ORDER BY id""".query[FindingIdRow].run()
      // the DOMAIN edge list is both tables: where an edge is stored is a storage concern, and a
      // patch or an export must not lose the parked ones
      val es = sql"""SELECT src, dst, kind FROM edges WHERE src = $citekey
                     UNION ALL
                     SELECT src, dst, kind FROM unresolved_edges WHERE src = $citekey
                     ORDER BY kind, dst""".query[EdgeRow].run()
      Source(
        citekey = apiCitekey(r.citekey),
        title = com.histmersion.api.library.Title(r.title.value),
        authors = r.authors.map(a => AuthorName(a.value)),
        doi = r.doi.map(d => com.histmersion.api.library.Doi(d.value)),
        year = r.year.map(y => Year(y.value.toLong)),
        venue = r.venue.map(v => Source.Venue(v.value)),
        status = r.status.transformInto[Source.Status],
        topics = r.topics.map(t => Source.Topics(t.value)),
        regions = r.regions.map(t => Source.Regions(t.value)),
        tags = r.tags.map(t => Source.Tags(t.value)),
        period = (r.periodStart, r.periodEnd) match
          case (Some(a), Some(b)) =>
            Some(Period(Year(a.value.toLong), Year(b.value.toLong)))
          case _ => None,
        summary = r.summary.map(v => Source.Summary(v.value)),
        citedByCount = r.citedByCount.map(v => Source.CitedByCount(v.value)),
        influentialCitations = r.influentialCitations.map(v => Source.InfluentialCitations(v.value)),
        provenance = r.provenance.map(v => Source.Provenance(v.value)),
        calibreId = r.calibreId.map(v => Source.CalibreId(v.value)),
        ocrMarkdown = r.ocrMarkdown.map(v => Source.OcrMarkdown(v.value)),
        findings = fs.toList.map { f =>
          val geo = (f.lon, f.lat) match
            case (Some(x), Some(y)) =>
              Some(GeoPoint(Longitude(x.value), Latitude(y.value)))
            case _ => None
          Finding(
            Finding.Id(f.id.value),
            apiCitekey(r.citekey),
            Finding.Claim(f.claim.value),
            f.regionName.map(v => Finding.Region(v.value)),
            geo,
            (f.periodStart, f.periodEnd) match
              case (Some(a), Some(b)) =>
                Some(Period(Year(a.value.toLong), Year(b.value.toLong)))
              case _ => None,
            f.periodName.map(v => Finding.PeriodName(v.value)),
            f.tags.map(v => Finding.Tags(v.value)),
            f.confidence.transformInto[Option[Confidence]],
            f.significance.map(v => Finding.Significance(v.value))
          )
        },
        edges = es.toList.map(e =>
          Edge(
            com.histmersion.api.library.Citekey(e.src.value),
            com.histmersion.api.library.Citekey(e.dst.value),
            Edge.Kind.fromWire(Edge.Kind.Wire(e.kind.value)).getOrElse(Edge.Kind.References)
          )
        ),
        notes = r.notes.map(v => Source.Notes(v.value)),
        updatedAt = Some(Source.UpdatedAt(r.updatedAt.value))
      )
    }

  def citekeys()(using DbCon): Vector[Citekey] =
    sql"""SELECT citekey FROM sources ORDER BY citekey COLLATE "C"""".query[Citekey].run()

  def updatedAtMillis()(using DbCon): Map[Citekey, CitekeyMillis.Millis] =
    sql"SELECT citekey, (extract(epoch from updated_at) * 1000)::bigint FROM sources"
      .query[CitekeyMillis]
      .run()
      .map(r => r.citekey -> r.millis)
      .toMap

  /** Delete and rewrite one source's FTS chunks. Returns how many chunks were written. Batched: the corpus is ~21 000
    * chunks, and one round trip per chunk makes a full reindex take minutes (long enough for the server's request
    * timeout to cut it off).
    */
  private[histmersion] def replaceChunks(citekey: Citekey, markdown: Option[Chunker.ChunkMarkdown], chunkChars: Chunker.ChunkMaxChars)(using DbCon): RowCount =
    sql"DELETE FROM source_chunks WHERE citekey = $citekey".update.run()
    val chunks = markdown.map(Chunker.chunk(_, chunkChars)).getOrElse(Nil)
    if chunks.nonEmpty then
      val _ = batchUpdate(chunks)(c => sql"""INSERT INTO source_chunks (citekey, ord, heading, body)
              VALUES ($citekey, ${c.ord}, ${c.heading}, ${c.body})""".update)
    RowCount(chunks.size)

  /** The sources after `after`, in citekey order, each with its body's length: what a re-chunk of the corpus walks. */
  def bodiesAfter(after: Option[Citekey])(using DbCon): Vector[(Citekey, BodyChars)] =
    sql"""SELECT citekey, coalesce(length(ocr_markdown), 0)
          FROM sources WHERE $after IS NULL OR citekey > $after
          ORDER BY citekey""".query[(Citekey, BodyChars)].run()

  /** One source's full-text chunks rebuilt from its stored body; how many chunks it now has. */
  def rechunk(citekey: Citekey, chunkChars: Chunker.ChunkMaxChars)(using DbCon): RowCount =
    val md = sql"SELECT ocr_markdown FROM sources WHERE citekey = $citekey".query[Option[String]].run()
    replaceChunks(citekey, md.headOption.flatten.map(Chunker.ChunkMarkdown(_)), chunkChars)

  def resolveEdges()(using DbCon): RowCount =
    val parked = sql"SELECT src, dst, kind FROM unresolved_edges".query[EdgeRow].run().toList
    val aliases = resolveAliases(parked.map(e => WikiLinks.Occurrence.Target(e.dst.value)))
    val moves =
      parked.flatMap(e =>
        aliases.get(WikiLinks.Occurrence.Target(e.dst.value.trim.toLowerCase)).filter(_.value != e.src.value).map(e -> _)
      )
    moves.foreach { (e, ck) =>
      sql"""INSERT INTO edges (src, dst, kind) VALUES (${e.src}, $ck, ${e.kind})
              ON CONFLICT DO NOTHING""".update.run()
      sql"DELETE FROM unresolved_edges WHERE src = ${e.src} AND dst = ${e.dst} AND kind = ${e.kind}".update.run()
    }
    RowCount(moves.size)

  /** Resolve wikilink targets to real citekeys, the same way [[resolveEdges]] resolves edge targets: an exact citekey
    * wins, otherwise the UNIQUE `<target>_%` prefix match does. The wiki writes `[[allentoft2015]]` for
    * `allentoft2015_population`, so without this every citation in every compiled article is a dead link.
    *
    * Ambiguous targets are deliberately absent from the result — two candidates means the renderer must not guess, and
    * `lint --rule unresolved-short-citekeys` is where a human picks one. One query for the whole body, whatever its
    * number of links.
    */
  def resolveAliases(targets: List[WikiLinks.Occurrence.Target])(using DbCon): Map[WikiLinks.Occurrence.Target, Citekey] =
    val wanted = targets.map(_.value.trim.toLowerCase).filter(_.nonEmpty).distinct
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
           """.query[(String, Citekey, Int)].run()
      rows.groupBy(_._1).flatMap { (target, cands) =>
        val best = cands.map(_._3).min
        cands.filter(_._3 == best).map(_._2).distinct.toList match
          case one :: Nil => Some(WikiLinks.Occurrence.Target(target) -> one)
          case _          => None // ambiguous: a human picks, the renderer does not guess
      }

  // ── renaming a citekey (V027) ──────────────────────────────────────────────────────────────

  /** THE resolution of a citekey somebody asked for: the citekey itself when a source has it, else the source an OLD
    * citekey was renamed to, else `None`. Every read and write that takes a citekey goes through this (the server's
    * `SourcesControllers.canonical`, and [[patch]]), so an old id keeps working everywhere at once. Exact only: the
    * short-form prefix rule belongs to wikilinks ([[resolveAliases]]), not to ids in URLs.
    */
  def canonical(id: Citekey)(using DbCon): Option[Citekey] =
    canonicalAll(List(id)).get(id)

  /** [[canonical]] for many ids in one query; an id that names nothing is absent. */
  private[histmersion] def canonicalAll(ids: List[Citekey])(using DbCon): Map[Citekey, Citekey] =
    if ids.isEmpty then Map.empty
    else
      sql"""SELECT citekey, citekey, 0 FROM sources WHERE citekey = ANY($ids::text[])
            UNION ALL
            SELECT alias, citekey, 1 FROM source_aliases WHERE alias = ANY($ids::text[])"""
        .query[(Citekey, Citekey, Int)]
        .run()
        .toList
        .groupBy(_._1)
        .map((id, rows) => id -> rows.minBy(_._3)._2)

  /** The source `ck` was renamed to, when `ck` is an old citekey. */
  def aliasOf(ck: Citekey)(using DbCon): Option[Citekey] = aliasTarget(ck)

  private def aliasTarget(ck: Citekey)(using DbCon): Option[Citekey] =
    sql"SELECT citekey FROM source_aliases WHERE alias = $ck".query[Citekey].run().headOption

  /** The OLD citekeys that now name `ck` (V027) — an edge written on `ck` may give one as its `src`. */
  def aliasesOf(ck: Citekey)(using DbCon): Set[Citekey] =
    (sql"SELECT alias FROM source_aliases WHERE citekey = $ck".query[Citekey].run().toSet)

  /** The Calibre book id recorded for a source, if any. */
  def calibreId(ck: Citekey)(using DbCon): Option[FullSourceRow.CalibreId] =
    (sql"SELECT calibre_id FROM sources WHERE citekey = $ck".query[Option[FullSourceRow.CalibreId]].run().headOption.flatten)

  /** Every old citekey and the source it names now, by alias. */
  def aliases(using DbCon): List[Source.Alias] =
    (
      sql"""SELECT alias, citekey FROM source_aliases ORDER BY alias COLLATE "C""""
        .query[(String, String)]
        .run()
        .toList
        .map((alias, citekey) => Source.Alias(Source.Alias.Value(alias), com.histmersion.api.library.Citekey(citekey)))
    )

  /** Rename a source's citekey, in the caller's transaction — all of it happens or none of it does.
    *
    *   - Refused: an invalid or non-link-shaped new key ([[Citekeys.renameTo]]), `old` unknown (or itself an old key —
    *     rename what it points to), `new` already a source, `new` an old key of ANOTHER source, or `new#<n>` finding
    *     ids already taken. Renaming back to a key this source had before is allowed and drops that alias, leaving an
    *     `alias_tombstones` row (V050).
    *   - The key moves with one UPDATE; V027's ON UPDATE CASCADE carries every foreign key (findings, assessments,
    *     attestations, chunks, assets and their links, both edge tables, aliases).
    *   - Finding ids `old#n` → `new#n` (cascading to `finding_places`).
    *   - What other domains store of the source WITHOUT a foreign key — attestation and asset-link targets, article
    *     link targets and every `[[old]]` (and the short forms that resolved to it) in article texts, events and their
    *     datings/edges/participants, jobs, place types, footprints and a place's `source` — follows through the
    *     referrers, each in its owner's place.
    *   - `[[old]]` and its short forms in every source's notes/summary, labels and anchors kept
    *     ([[WikiLinks.retarget]]).
    *   - Parked edges that named `new` (or a short form now unique to it) become real edges.
    *   - `old` becomes an alias of `new`, so it keeps resolving.
    */
  def rename(oldCk: Citekey, newRaw: Citekeys.RenameToNewRaw, referrers: SourceReferrers)(using DbCon): Either[Api.Error, Rename.Counts] =
    Citekeys
      .renameTo(oldCk, newRaw)
      .left
      .map(problem => Write.invalid(Write.InvalidMessage(problem.value)))
      .flatMap(validated => renameIn(oldCk, validated, referrers))

  /** [[rename]] inside the caller's transaction, `newCk` already validated — also what a bundle import does when a
    * source was renamed on the other side (`BundleImport`).
    */
  private[histmersion] def renameIn(oldCk: Citekey, newCk: Citekey, referrers: SourceReferrers)(using
      DbCon
  ): Either[Api.Error, Rename.Counts] =
    lockRow(oldCk)
    def taken(ck: Citekey) = sql"SELECT citekey FROM sources WHERE citekey = $ck".query[String].run().nonEmpty
    val findingIdsTaken =
      sql"SELECT id FROM findings WHERE left(id, ${newCk.value.length + 1}) = ${newCk.value + "#"} LIMIT 1"
        .query[String]
        .run()
        .headOption
    if !taken(oldCk) then
      Left(aliasTarget(oldCk) match
        case Some(now) =>
          Api.Error(
            Api.Error.Message(s"'${oldCk.value}' is an old citekey of '${now.value}' — rename '${now.value}' instead"),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        case None => Write.notFound(apiCitekey(oldCk)))
    else if taken(newCk) then
      Left(
        Api.Error(
          Api.Error.Message(s"'${newCk.value}' is already the citekey of another source"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      aliasTarget(newCk) match
        case Some(other) if other != oldCk =>
          Left(
            Api.Error(
              Api.Error.Message(s"'${newCk.value}' is an old citekey of '${other.value}' and still resolves to it — pick another"),
              Some(Api.Error.Code(Write.CodeInvalid))
            )
          )
        case back =>
          findingIdsTaken match
            case Some(id) =>
              Left(
                Api.Error(
                  Api.Error.Message(s"finding id '$id' already exists — '${newCk.value}' would collide with it"),
                  Some(Api.Error.Code(Write.CodeInvalid))
                )
              )
            case None =>
              if back.isDefined then
                // renaming back: the alias goes, and its tombstone stays so an import does not bring it
                // back (#827) — `newCk` is this source's citekey again
                sql"DELETE FROM source_aliases WHERE alias = $newCk".update.run()
                sql"""INSERT INTO alias_tombstones (alias, source_uid)
                    SELECT $newCk, uid FROM sources WHERE citekey = $oldCk
                    ON CONFLICT (alias) DO UPDATE SET source_uid = EXCLUDED.source_uid, deleted_at = now()""".update
                  .run()
              Right(renameNow(oldCk, newCk, referrers))

  private def renameNow(oldCk: Citekey, newCk: Citekey, referrers: SourceReferrers)(using DbCon): Rename.Counts =
    // the short forms that pointed at the old key — decided BEFORE it moves
    val forms = Rename.shortForms(apiCitekey(oldCk))
    val shorts = resolveAliases(forms.map(f => WikiLinks.Occurrence.Target(f.value))).collect { case (f, ck) if ck == oldCk => f }.toSet

    // the key itself: ON UPDATE CASCADE (V027) carries every foreign key
    sql"UPDATE sources SET citekey = $newCk, updated_at = now() WHERE citekey = $oldCk".update.run()

    // finding ids; what other domains store of the source follows it (the referrers, in their order)
    val (oldHash, cut) = (oldCk.value + "#", oldCk.value.length + 1)
    val findings =
      sql"""UPDATE findings SET id = $newCk || substr(id, $cut)
            WHERE citekey = $newCk AND left(id, $cut) = $oldHash""".update.run()
    val rename = SourceRename(oldCk, newCk, shorts.map(f => Citekey.assume(f.value)))
    val referred = referrers.renamed(rename)

    // parked edges that were waiting for the new name, exactly or by a short form now unique to it
    val waiting = sql"""SELECT DISTINCT dst FROM unresolved_edges
                        WHERE dst = $newCk OR left($newCk, length(dst) + 1) = dst || '_'"""
      .query[String]
      .run()
      .toList
      .map(WikiLinks.Occurrence.Target(_))
    val resolvedNow = resolveAliases(waiting)
    val promoted = waiting.filter(d => resolvedNow.get(d).contains(newCk)).map(promoteAs(_, newCk).value).sum

    // the text: `[[old]]` and its short forms in every source's notes/summary (the articles are a referrer's)
    val sources = retargetSourceText(rename.needle, rename.target)

    sql"""INSERT INTO source_aliases (alias, citekey) VALUES ($oldCk, $newCk)
          ON CONFLICT (alias) DO UPDATE SET citekey = EXCLUDED.citekey""".update.run()
    // an alias that exists again is no longer deleted
    sql"DELETE FROM alias_tombstones WHERE alias = $oldCk".update.run()

    Rename.Counts(
      Rename.Counts.Findings(findings),
      referred.texts.map(t => Rename.Counts.Articles(t._1.value)),
      sources.map(t => Rename.Counts.Sources(t._1.value)),
      Rename.Counts.Links(referred.texts.map(_._2.value).sum + sources.map(_._2.value).sum),
      referred.columns,
      Rename.Counts.EdgesPromoted(promoted)
    )

  /** Promote the parked edges whose target is `dst` as edges to `ck` (never a self-edge). */
  private def promoteAs(dst: WikiLinks.Occurrence.Target, ck: Citekey)(using DbCon): RowCount =
    val n = sql"""INSERT INTO edges (src, dst, kind)
                  SELECT src, $ck, kind FROM unresolved_edges WHERE dst = $dst AND src <> $ck
                  ON CONFLICT DO NOTHING""".update.run()
    sql"DELETE FROM unresolved_edges WHERE dst = $dst AND src <> $ck".update.run()
    RowCount(n)

  /** Rewrite links in every source's notes and summary; returns (citekey, links moved) per source that changed. A plain
    * UPDATE, not [[write]]: nothing but the text changes, so the findings, chunks and edges need no rewrite.
    */
  private def retargetSourceText(needle: SourceRename.Needle, to: Citekey => Option[Citekey])(using DbCon): List[(Citekey, RowCount)] =
    sql"""SELECT citekey, notes, summary FROM sources
          WHERE strpos(lower(coalesce(notes, '') || ' ' || coalesce(summary, '')), $needle) > 0
          ORDER BY citekey"""
      .query[(Citekey, Option[String], Option[String])]
      .run()
      .toList
      .flatMap { (ck, notes, summary) =>
        val target: WikiLinks.Occurrence.Target => Option[WikiLinks.Occurrence.Target] = t =>
          domainCitekey(apiCitekeyOf(t)).flatMap(to).map(c => WikiLinks.Occurrence.Target(c.value))
        val n = notes.map(v => WikiLinks.retarget(WikiLinks.Body(v), target))
        val s = summary.map(v => WikiLinks.retarget(WikiLinks.Body(v), target))
        val moved = n.fold(0)(_._2.value) + s.fold(0)(_._2.value)
        Option.when(moved > 0) {
          sql"""UPDATE sources SET notes = ${n.map(_._1)}, summary = ${s.map(_._1)}, updated_at = now()
                WHERE citekey = $ck""".update.run()
          ck -> RowCount(moved)
        }
      }

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  /** The citation-link view of the given stored sources (unknown citekeys are simply absent) — what a page needs to
    * render a reference with its DOI, byline and status. One query, in citekey order.
    */
  def refs(citekeys: List[Citekey])(using DbCon): List[Source.Ref] =
    val wanted = citekeys.distinct
    if wanted.isEmpty then Nil
    else sql"""SELECT citekey, doi, authors, year, title, status FROM sources
              WHERE citekey = ANY($wanted::text[]) ORDER BY citekey""".query[Source.Ref].run().toList

  def get(citekey: Citekey)(using DbCon): Option[(Source.Detail, Vector[Finding.Row])] =
    val src = sql"""SELECT citekey, title, authors, year, venue, status, topics, regions, tags,
                             ${SqlLiteral(RangeSql.cols("period"))},
                             summary, doi, ocr_markdown, notes,
                             to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                             source, uid, origin
                      FROM sources WHERE citekey = $citekey""".query[Source.Detail].run().headOption
    src.map { r =>
      val fs = sql"""SELECT claim, region_name, ST_X(geom), ST_Y(geom),
                              ${SqlLiteral(RangeSql.cols("period"))}, period_name,
                              tags, confidence, significance, uid
                       FROM findings WHERE citekey = $citekey ORDER BY id""".query[Finding.Row].run()
      (r, fs)
    }

  // ── get_source sectioning (plan §6 A1/A7/A18) ────────────────────────────────────────────────

  /** Table of contents: distinct headings from source_chunks with first ord and total chars. */
  def toc(citekey: Citekey)(using DbCon): List[TocEntry] =
    sql"""SELECT heading, min(ord), sum(length(body))::int
            FROM source_chunks
            WHERE citekey = $citekey AND heading IS NOT NULL
            GROUP BY heading
            ORDER BY min(ord)""".query[TocEntry].run().toList

  /** Full body text: chunks concatenated in ord order, or raw ocr_markdown when unchunked. */
  /** A source's converted text as stored — where its figures are linked from. */
  def markdown(citekey: Citekey)(using DbCon): FullSourceRow.OcrMarkdown =
    FullSourceRow.OcrMarkdown(
      sql"SELECT coalesce(ocr_markdown, '') FROM sources WHERE citekey = $citekey"
        .query[String]
        .run()
        .headOption
        .getOrElse("")
    )

  // ── the uploaded ORIGINAL's digest, on `sources.sha256` (V001 already has the column) ─────────

  def citekeyOfSha256(sha: Sha256)(using DbCon): Option[Citekey] =
    sql"SELECT citekey FROM sources WHERE sha256 = $sha ORDER BY citekey LIMIT 1".query[Citekey].run().headOption

  def setSha256(citekey: Citekey, sha: Sha256)(using DbCon): Unit =
    val _ = sql"UPDATE sources SET sha256 = $sha WHERE citekey = $citekey".update.run()

  def sha256Of(citekey: Citekey)(using DbCon): Option[Sha256] =
    sql"SELECT sha256 FROM sources WHERE citekey = $citekey".query[Option[Sha256]].run().headOption.flatten

  /** The content hash (`sources.sha256`, the stored original's name) of each of `citekeys` that has one, in one query.
    */
  def sha256s(citekeys: List[Citekey])(using DbCon): Map[Citekey, Sha256] =
    if citekeys.isEmpty then Map.empty
    else
      sql"""SELECT citekey, sha256 FROM sources WHERE citekey = ANY($citekeys::text[]) AND sha256 IS NOT NULL"""
        .query[(Citekey, Sha256)]
        .run()
        .toMap

  // ── the identifiers acquisition resolves a work's references and its own copy by ────────────

  /** `lower(doi) -> citekey` for the whole corpus, in one query. */
  def doiIndex()(using DbCon): Map[com.histmersion.domain.library.Doi, Citekey] =
    sql"SELECT lower(doi), citekey FROM sources WHERE doi IS NOT NULL AND doi <> ''"
      .query[DoiKey]
      .run()
      .map(r => r.doi -> r.citekey)
      .toMap

  final private case class DoiKey(doi: com.histmersion.domain.library.Doi, citekey: Citekey) derives DbCodec

  /** The citekey of the source holding `doi` (the first by citekey), if one does. */
  def citekeyOfDoi(doi: com.histmersion.domain.library.Doi)(using DbCon): Option[Citekey] =
    val d = doi.value.trim.toLowerCase
    sql"SELECT citekey FROM sources WHERE lower(doi) = $d ORDER BY citekey LIMIT 1".query[Citekey].run().headOption

  def exists(citekey: Citekey)(using DbCon): Boolean =
    sql"SELECT count(*)::int FROM sources WHERE citekey = $citekey".query[Int].run().headOption.exists(_ > 0)

  // ── the licence a source is published under, as stored (classified on read by the service) ──

  def licenceOf(citekey: Citekey)(using DbCon): Option[Licence.Id] =
    sql"SELECT license FROM sources WHERE citekey = $citekey".query[Option[Licence.Id]].run().headOption.flatten

  /** The licence a fetched copy came with, recorded as the source's when it has none — one set by hand or an earlier
    * copy is kept. `true` when it was written.
    */
  def recordLicence(citekey: Citekey, licence: Licence.Id)(using DbCon): Boolean =
    sql"""UPDATE sources SET license = $licence, updated_at = now()
          WHERE citekey = $citekey AND (license IS NULL OR btrim(license) = '')""".update.run() > 0

  // ── Calibre: the sources not in it yet, and the id written back after a push ────────────────

  final private case class GapRow(
      citekey: Citekey,
      title: GapRow.Title,
      authors: List[GapRow.Authors],
      doi: Option[GapRow.Doi],
      year: Option[com.histmersion.Year],
      venue: Option[GapRow.Venue],
      summary: Option[GapRow.Summary],
      topics: List[GapRow.Topics],
      provenance: Option[GapRow.Provenance],
      calibreId: Option[GapRow.CalibreId],
      isbn: Option[GapRow.Isbn]
  ) derives DbCodec:
    def gap: CalibreGap =
      CalibreGap(
        apiCitekey(citekey),
        CalibreGap.Title(title.value),
        authors.map(a => CalibreGap.Authors(a.value)),
        doi.map(d => com.histmersion.api.library.Doi(d.value)),
        year.map(y => Year(y.value.toLong)),
        venue.map(v => Source.Venue(v.value)),
        summary.map(v => CalibreGap.Summary(v.value)),
        topics.map(t => CalibreGap.Topics(t.value)),
        provenance.map(v => CalibreGap.Provenance(v.value)),
        calibreId.map(v => Source.CalibreId(v.value)),
        isbn.map(v => CalibreGap.Isbn(v.value))
      )

  object GapRow:
    type Doi = Doi.T
    object Doi extends RefinedType[String, Pure]
    type Title = com.histmersion.domain.Title
    val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
    type Authors = Authors.T
    object Authors extends RefinedType[String, Pure]
    type Venue = Venue.T
    object Venue extends RefinedType[String, Pure]
    type Summary = Summary.T
    object Summary extends RefinedType[String, Pure]
    type Topics = Topics.T
    object Topics extends RefinedType[String, Pure]
    type Provenance = Provenance.T
    object Provenance extends RefinedType[String, Pure]
    type CalibreId = CalibreId.T
    object CalibreId extends RefinedType[String, Pure]
    type Isbn = Isbn.T
    object Isbn extends RefinedType[String, Pure]

  /** Sources that are NOT in Calibre yet: provenance is something other than `calibre` (or missing) and no `calibre_id`
    * has been written back, in citekey order, at most `limit`.
    */
  def calibreGaps(limit: Option[Limit])(using DbCon): List[CalibreGap] =
    val cap = limit.map(_.value).filter(_ > 0).getOrElse(Int.MaxValue)
    sql"""SELECT citekey, title, authors, doi, year, venue, summary, topics, source, NULLIF(calibre_id, ''),
            (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = sources.uid AND i.scheme = 'isbn')
          FROM sources
          WHERE (calibre_id IS NULL OR calibre_id = '')
            AND (source IS NULL OR source <> 'calibre')
          ORDER BY citekey
          LIMIT $cap""".query[GapRow].run().toList.map(_.gap)

  /** The push metadata for ONE source, whether or not it currently counts as a gap — `calibre push <citekey>` needs the
    * authors as a LIST, which the comma-joined `SourceDetail.authors` cannot give back unambiguously.
    */
  def calibrePushMeta(citekey: Citekey)(using DbCon): Option[CalibreGap] =
    sql"""SELECT citekey, title, authors, doi, year, venue, summary, topics, source, NULLIF(calibre_id, ''),
            (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = sources.uid AND i.scheme = 'isbn')
          FROM sources WHERE citekey = $citekey""".query[GapRow].run().headOption.map(_.gap)

  /** Write the Calibre id back after a push; how many sources it named (0 or 1). */
  def setCalibreId(citekey: Citekey, calibreId: FullSourceRow.CalibreId)(using DbCon): RowCount =
    sql"UPDATE sources SET calibre_id = $calibreId, updated_at = now() WHERE citekey = $citekey".update.run().pipe(RowCount(_))

  def bodyText(citekey: Citekey)(using DbCon): FullSourceRow.OcrMarkdown =
    val chunks = sql"""SELECT body FROM source_chunks
                         WHERE citekey = $citekey ORDER BY ord""".query[String].run()
    FullSourceRow.OcrMarkdown(
      if chunks.nonEmpty then chunks.mkString("\n\n")
      else
        sql"SELECT coalesce(ocr_markdown, '') FROM sources WHERE citekey = $citekey"
          .query[String]
          .run()
          .headOption
          .getOrElse("")
    )

  /** The body as `passages` searches it (#954): the chunks in `ord` order with their headings, or — for a source stored
    * without chunks — the raw `ocr_markdown` as one headingless piece. Joined by
    * [[com.histmersion.domain.PassageFinder.body]] this is exactly [[bodyText]].
    */
  def bodyChunks(citekey: Citekey)(using DbCon): List[Chunk] =
    val chunks = sql"""SELECT ord, heading, body FROM source_chunks
                         WHERE citekey = $citekey ORDER BY ord"""
      .query[(Int, Option[String], String)]
      .run()
      .toList
      .map((ord, heading, body) => Chunk(Chunk.Ord(ord), heading.map(Chunk.Heading(_)), Chunk.Body(body)))
    if chunks.nonEmpty then chunks
    else
      sql"SELECT coalesce(ocr_markdown, '') FROM sources WHERE citekey = $citekey"
        .query[String]
        .run()
        .headOption
        .filter(_.nonEmpty)
        .map(text => Chunk(Chunk.Ord(0), None, Chunk.Body(text)))
        .toList

  /** Body text under a specific heading: chunks whose heading matches case-insensitively (prefix). */
  def bodyUnderHeading(citekey: Citekey, heading: FilterSql.SnippetRow.Heading)(using DbCon): FullSourceRow.OcrMarkdown =
    val lower = heading.value.trim.toLowerCase
    sql"""SELECT body FROM source_chunks
            WHERE citekey = $citekey AND lower(heading) LIKE ${lower + "%"}
            ORDER BY ord""".query[String].run().mkString("\n\n").pipe(FullSourceRow.OcrMarkdown(_))
