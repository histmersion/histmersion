package com.histmersion
package domain.library.sources
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import io.scalaland.chimney.dsl.*

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.Jsonb
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.exchange.BlobSink
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.ImportAbort
import com.histmersion.util.data.exchange.BundleSql.*
import com.histmersion.util.storage.ContentStore
import com.histmersion.domain.catalogue.places.GeocodeSettings

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import com.histmersion.api.library.Edge
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Source
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.util.ocr.Chunker
import com.histmersion.api.exchange.Bundle.uidEquality
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.util.database.StoredCreator

/** The sources as a bundle carries them (docs/research-merge-import.md §4): each source with its findings, its edges in
  * and out, its parked edges, its old citekeys and its assessment, read out in a deterministic order (by uid; owned
  * sets by their source's uid, then their own key, in the "C" collation); and written in by an import, a source's body
  * and stored original as their blobs arrive.
  */
private[sources] object SourceBundleRepo:
  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def sources(scope: BundleScope)(using DbCon): Vector[BSourceRow] =
    sql"""SELECT s.uid, s.origin, s.citekey, s.title, s.authors, s.doi, s.year, s.venue, s.status,
                       s.topics, s.regions, s.tags, ${rangeCols(sql"s.period")}, s.summary, s.notes, s.source,
                       s.cited_by_count, s.influential_citations, to_char(s.added_date, 'YYYY-MM-DD'),
                       encode(sha256(convert_to(s.ocr_markdown, 'UTF8')), 'hex'),
                       octet_length(convert_to(s.ocr_markdown, 'UTF8'))::bigint,
                       nullif(lower(btrim(s.sha256)), ''), ${Timestamps.iso(sql"s.updated_at")},
                       coalesce((SELECT array_agg(i.value ORDER BY i.value COLLATE "C") FROM source_identifiers i
                                 WHERE i.source_uid = s.uid AND i.scheme = 'isbn'), '{}'::text[]), s.creator::text
                FROM sources s WHERE ${where(scope, sql"s.uid", sql"s.updated_at")} ORDER BY s.uid"""
      .query[BSourceRow]
      .run()

  def findings(scope: BundleScope)(using DbCon): Vector[Bundle.Finding] =
    sql"""SELECT f.uid, f.origin, s.uid, f.id, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom),
                       ${rangeCols(sql"f.period")}, f.period_name, f.tags, f.confidence, f.significance,
                       ${Timestamps.iso(sql"f.updated_at")}, f.creator::text
                FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ${where(
        scope,
        sql"f.uid",
        sql"f.updated_at"
      )} ORDER BY f.uid""".query[BFindingRow].run().map { r =>
      Bundle.Finding(
        r.uid,
        r.origin,
        r.sourceUid,
        Bundle.Finding.Id(r.id.value),
        Bundle.Finding.Claim(r.claim.value),
        r.region.map(x => Bundle.Finding.Region(x.value)),
        r.lon.map(x => Longitude(x.value)),
        r.lat.map(x => Latitude(x.value)),
        range(
          util.data.exchange.Bundle.RangeIsNull(r.pNull.value),
          r.pLo.map(x => util.data.exchange.Bundle.RangeLower(x.value)),
          r.pHi.map(x => util.data.exchange.Bundle.RangeUpper(x.value))
        ),
        r.periodName.map(x => Bundle.Finding.PeriodName(x.value)),
        r.tags.map(t => Bundle.Finding.Tags(t.value)),
        r.confidence.transformInto[Option[Confidence]],
        r.significance.map(x => Bundle.Finding.Significance(x.value)),
        Bundle.Finding.UpdatedAt(r.updatedAt.value),
        creator = StoredCreator.document(r.creator)
      )
    }

  /** The out-edges of the sources in scope — a source carries its whole set. */
  def edges(scope: BundleScope)(using DbCon): Vector[Bundle.Edge] =
    sql"""SELECT a.uid, b.uid, e.kind FROM edges e
                JOIN sources a ON a.citekey = e.src JOIN sources b ON b.citekey = e.dst WHERE ${where(
        scope,
        sql"a.uid",
        sql"a.updated_at"
      )} ORDER BY a.uid, b.uid, e.kind COLLATE "C\""""
      .query[(UUID, UUID, Bundle.Edge.Kind)]
      .run()
      .map(Bundle.Edge.apply)

  def unresolvedEdges(scope: BundleScope)(using DbCon): Vector[Bundle.UnresolvedEdge] =
    sql"""SELECT a.uid, u.dst, u.kind FROM unresolved_edges u
                JOIN sources a ON a.citekey = u.src WHERE ${where(
        scope,
        sql"a.uid",
        sql"a.updated_at"
      )} ORDER BY a.uid, u.dst COLLATE "C", u.kind COLLATE "C\""""
      .query[(UUID, Bundle.UnresolvedEdge.Dst, Bundle.UnresolvedEdge.Kind)]
      .run()
      .map(Bundle.UnresolvedEdge.apply)

  def assessments(scope: BundleScope)(using DbCon): Vector[Bundle.Assessment] =
    sql"""SELECT s.uid, ${Timestamps.iso(sql"a.checked_at")}, a.retracted, a.notices::text, a.cited_by,
                       a.influential, a.verdict
                FROM assessments a JOIN sources s ON s.citekey = a.citekey WHERE ${where(
        scope,
        sql"s.uid",
        sql"a.checked_at"
      )} ORDER BY s.uid""".query[BAssessmentRow].run().map { r =>
      Bundle.Assessment(
        r.sourceUid,
        Bundle.Assessment.CheckedAt(r.checkedAt.value),
        Bundle.Assessment.Retracted(r.retracted.value),
        Jsonb.read[Json](Jsonb.JsonText(r.notices.value)).getOrElse(Json.arr()),
        r.citedBy.map(x => Bundle.Assessment.CitedBy(x.value)),
        r.influential.map(x => Bundle.Assessment.Influential(x.value)),
        Bundle.Assessment.Verdict(r.verdict.value)
      )
    }

  def aliases(scope: BundleScope)(using DbCon): Vector[Bundle.Alias] =
    sql"""SELECT a.alias, s.uid, ${Timestamps.iso(sql"a.created_at")}
                FROM source_aliases a JOIN sources s ON s.citekey = a.citekey WHERE ${where(
        scope,
        sql"s.uid",
        sql"a.created_at"
      )} ORDER BY a.alias COLLATE "C\""""
      .query[(Bundle.Alias.Value, UUID, Bundle.Alias.CreatedAt)]
      .run()
      .map(Bundle.Alias.apply)

  /** A source row as its bundle line; `originals` says whether the stored original goes along. */
  def sourceLine(r: BSourceRow, originals: ContentStore): Bundle.Source =
    Bundle.Source(
      r.uid,
      r.origin,
      Citekey(r.citekey.value),
      Title(r.title.value),
      r.authors.map(a => AuthorName(a.value)),
      r.doi.map(d => Doi(d.value)),
      r.year.map(y => Year(y.value.toLong)),
      r.venue.map(v => Source.Venue(v.value)),
      r.status.transformInto[Source.Status],
      r.topics.map(t => Bundle.Source.Topics(t.value)),
      r.regions.map(t => Bundle.Source.Regions(t.value)),
      r.tags.map(t => Bundle.Source.Tags(t.value)),
      range(
        util.data.exchange.Bundle.RangeIsNull(r.pNull.value),
        r.pLo.map(x => util.data.exchange.Bundle.RangeLower(x.value)),
        r.pHi.map(x => util.data.exchange.Bundle.RangeUpper(x.value))
      ),
      r.summary.map(x => Bundle.Source.Summary(x.value)),
      r.notes.map(x => Bundle.Source.Notes(x.value)),
      r.provenance.map(x => Bundle.Source.Provenance(x.value)),
      r.citedBy.map(x => Bundle.Source.CitedByCount(x.value)),
      r.influential.map(x => Bundle.Source.InfluentialCitations(x.value)),
      Bundle.Source.AddedDate(r.addedDate.value),
      r.bodySha.map(x => Bundle.Source.BodySha256(x.value)),
      r.originalSha.map(x => Bundle.Source.OriginalSha256(x.value)),
      r.originalSha.flatMap(originals.find).map(e => Bundle.Source.OriginalExt(e._2.value)),
      Bundle.Source.UpdatedAt(r.updatedAt.value),
      isbns = Option.when(r.isbns.nonEmpty)(r.isbns.map(i => Bundle.Source.Isbns(i.value))),
      creator = StoredCreator.document(r.creator)
    )

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** The sources whose body an import wrote or changed: their search chunks are rebuilt in the derived step. */
  private val changedBodies = BundleImport.Once(_ => scala.collection.mutable.LinkedHashSet.empty[UUID])
  private def bodiesChanged(w: BundleImport.Writer): scala.collection.mutable.LinkedHashSet[UUID] =
    w.once(changedBodies)

  def sourcesIn(w: BundleImport.Writer, referrers: SourceReferrers)(using Creator.New): Unit =
    given DbCon = w.transaction
    given CreatedAt = w.at
    // `rename-left`: a source here gives its citekey up first, to the bundle's work that has it; the
    // old citekey does not stay an alias (it is the other work's now)
    w.merged.renames.foreach { (_, from, to) =>
      w.asLocal {
        SourceRepo
          .renameIn(
            domain.library.Citekey.assume(from.value),
            domain.library.Citekey.assume(to.value),
            referrers
          )
          .left
          .foreach(e => throw ImportAbort(NonEmptyVector.toVector(e.problems).toList))
        sql"DELETE FROM source_aliases WHERE alias = $from".update.run(): Unit
      }
    }
    val localCk = w.local.sources.map(s => s.uid -> s.citekey).toMap ++
      w.merged.renames.map((u, _, to) => u -> to)
    w.es.sources.filter(s => w.writes(s.uid)).foreach { s =>
      // renamed over there: the whole rename here first (findings, links, aliases follow)
      localCk.get(s.uid).filter(_ != s.citekey).foreach { old =>
        w.asLocal {
          SourceRepo
            .renameIn(
              domain.library.Citekey.assume(old.value),
              domain.library.Citekey.assume(s.citekey.value),
              referrers
            )
            .left
            .foreach(e => throw ImportAbort(NonEmptyVector.toVector(e.problems).toList))
        }
      }
      if w.inserts(s.uid) then
        w.insertSource(s.uid)
        bodiesChanged(w) ++= s.bodySha256.map(_ => s.uid)
        sql"""INSERT INTO sources (uid, origin, citekey, title, authors, doi, year, venue, status, topics,
                regions, tags, period, summary, notes, source, cited_by_count, influential_citations,
                added_date, sha256, updated_at, creator)
              VALUES (${s.uid}, ${s.origin}, ${s.citekey}, ${s.title}, ${s.authors}::text[], ${s.doi},
                ${s.year}, ${s.venue}, ${s.status.wire}, ${s.topics}::text[], ${s.regions}::text[],
                ${s.tags}::text[],
                ${rangeLiteral(s.period)}::int8range,
                ${s.summary}, ${s.notes}, ${s.provenance}, ${s.citedByCount}, ${s.influentialCitations},
                ${s.addedDate}::date, ${s.originalSha256}, ${s.updatedAt}::timestamptz,
                ${StoredCreator.carried(s.creator)}::jsonb)""".update.run()
      else
        val before = w.local.sources.find(_.uid == s.uid)
        if before.flatMap(_.bodySha256) != s.bodySha256 then
          if s.bodySha256.isEmpty then
            Unused(sql"UPDATE sources SET ocr_markdown = NULL WHERE uid = ${s.uid}".update.run(), Unused.Why.Held)
            Unused(sql"DELETE FROM source_chunks WHERE citekey = ${s.citekey}".update.run(), Unused.Why.Idempotent)
          else bodiesChanged(w) += s.uid
        Unused(
          sql"""UPDATE sources SET origin = ${s.origin}, title = ${s.title}, authors = ${s.authors}::text[],
                doi = ${s.doi}, year = ${s.year}, venue = ${s.venue}, status = ${s.status.wire},
                topics = ${s.topics}::text[], regions = ${s.regions}::text[], tags = ${s.tags}::text[],
                period = ${rangeLiteral(s.period)}::int8range,
                summary = ${s.summary}, notes = ${s.notes}, source = ${s.provenance},
                cited_by_count = ${s.citedByCount}, influential_citations = ${s.influentialCitations},
                added_date = ${s.addedDate}::date, sha256 = ${s.originalSha256},
                updated_at = ${s.updatedAt}::timestamptz
              WHERE uid = ${s.uid}""".update.run(),
          Unused.Why.Held
        )
      w.touchSource(s.uid)
      // the ISBNs (doi and sha256 are derived from the row by V032's trigger)
      Unused(
        sql"DELETE FROM source_identifiers WHERE source_uid = ${s.uid} AND scheme = 'isbn'".update.run(),
        Unused.Why.Idempotent
      )
      s.isbns
        .getOrElse(Nil)
        .foreach(i =>
          Unused(
            sql"""INSERT INTO source_identifiers (source_uid, scheme, value) VALUES (${s.uid}, 'isbn', $i)
              ON CONFLICT DO NOTHING""".update.run(),
            Unused.Why.Idempotent
          )
        )
    }

  /** A source's whole edge set travels with it: both sets are emptied for every source written, then its edges. */
  def edgesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.sources.filter(s => w.writes(s.uid)).foreach { s =>
      val ck = s.citekey
      Unused(sql"DELETE FROM edges WHERE src = $ck".update.run(), Unused.Why.Idempotent)
      Unused(sql"DELETE FROM unresolved_edges WHERE src = $ck".update.run(), Unused.Why.Idempotent)
    }
    w.es.edges.filter(e => w.writes(e.srcUid)).foreach { e =>
      // a target deleted here: the edge is left out (reported by the reference check)
      w.citekeyHere(e.dstUid).foreach { dst =>
        val src = w.citekeyOf(e.srcUid)
        Unused(
          sql"INSERT INTO edges (src, dst, kind, creator) VALUES ($src, $dst, ${e.kind}, ${StoredCreator.write(Creator.unrecorded)}::jsonb) ON CONFLICT DO NOTHING".update
            .run(),
          Unused.Why.Idempotent
        )
      }
    }

  def unresolvedEdgesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    given Creator.New = Creator.New.FromBundle
    w.es.unresolvedEdges.filter(e => w.writes(e.srcUid)).foreach { e =>
      Edge.Kind.fromWire(e.kind).foreach { k =>
        Unused(
          SourceRepo.insertEdges(List(Edge(Citekey(w.citekeyOf(e.srcUid).value), Citekey(e.dst.value), k))),
          Unused.Why.Reported
        )
      }
    }

  def aliasesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.aliases.filterNot(a => w.skipsAlias(domain.library.Citekey.assume(a.alias.value))).foreach { a =>
      // never an alias equal to the source's own citekey (a key renamed back here, #827)
      val n = sql"""INSERT INTO source_aliases (alias, citekey, created_at)
            SELECT ${a.alias}, citekey, ${a.createdAt}::timestamptz FROM sources
            WHERE uid = ${a.sourceUid} AND citekey <> ${a.alias}
            ON CONFLICT (alias) DO NOTHING""".update.run()
      if n > 0 then sql"DELETE FROM alias_tombstones WHERE alias = ${a.alias}".update.run(): Unit
    }

  def findingsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.findings.filter(f => w.writes(f.uid)).foreach { f =>
      w.touchSource(f.sourceUid)
      val ck = w.citekeyOf(f.sourceUid)
      val confidence: Option[String] = f.confidence.map(_.wire.value)
      sql"""INSERT INTO findings (uid, origin, id, citekey, claim, region_name, geom, period, period_name,
              tags, confidence, significance, updated_at, creator, created_at)
            VALUES (${f.uid}, ${f.origin}, ${f.id}, $ck, ${f.claim}, ${f.region},
              CASE WHEN ${f.lon}::float8 IS NULL OR ${f.lat}::float8 IS NULL THEN NULL
                   ELSE ST_SetSRID(ST_MakePoint(${f.lon}::float8, ${f.lat}::float8), 4326) END,
              ${rangeLiteral(f.period)}::int8range,
              ${f.periodName}, ${f.tags}::text[], $confidence, ${f.significance}, ${f.updatedAt}::timestamptz,
              ${StoredCreator.carried(f.creator)}::jsonb, ${w.at}::timestamptz)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, id = EXCLUDED.id,
              citekey = EXCLUDED.citekey, claim = EXCLUDED.claim, region_name = EXCLUDED.region_name,
              geom = EXCLUDED.geom, period = EXCLUDED.period, period_name = EXCLUDED.period_name,
              tags = EXCLUDED.tags, confidence = EXCLUDED.confidence, significance = EXCLUDED.significance,
              updated_at = EXCLUDED.updated_at""".update.run()
    }

  /** The newer verdict wins (§4); a source that is not here (deleted, skipped) has none. */
  def assessmentsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.assessments.foreach { a =>
      Unused(
        sql"""INSERT INTO assessments (citekey, checked_at, retracted, notices, cited_by, influential, verdict, creator)
            SELECT citekey, ${a.checkedAt}::timestamptz, ${a.retracted}, ${BundleFormat.print(a.notices)}::jsonb,
                   ${a.citedBy}, ${a.influential}, ${a.verdict}, ${StoredCreator.write(Creator.unrecorded)}::jsonb
            FROM sources WHERE uid = ${a.sourceUid}
            ON CONFLICT (citekey) DO UPDATE SET checked_at = EXCLUDED.checked_at,
              retracted = EXCLUDED.retracted, notices = EXCLUDED.notices, cited_by = EXCLUDED.cited_by,
              influential = EXCLUDED.influential, verdict = EXCLUDED.verdict
            WHERE assessments.checked_at < EXCLUDED.checked_at""".update.run(),
        Unused.Why.Idempotent
      )
    }

  /** What a merge changes in a body it took from the bundle: the figures it renamed. */
  def bodyRenamesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.merged.bodyRenames.foreach { (u, renames) =>
      renames.foreach { (from, to) =>
        Unused(
          sql"""UPDATE sources SET ocr_markdown = replace(ocr_markdown, ${"assets/" + from}, ${"assets/" + to})
              WHERE uid = $u""".update.run(),
          Unused.Why.Idempotent
        )
      }
      bodiesChanged(w) += u
    }

  /** The search chunks of a changed body, the finding places of a touched source, a new source's parked edges. */
  def derivedIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    bodiesChanged(w).foreach { u =>
      val (ck, md) =
        sql"SELECT citekey, ocr_markdown FROM sources WHERE uid = $u"
          .query[(domain.library.Citekey, Option[Chunker.ChunkMarkdown])]
          .run()
          .head
      Unused(SourceRepo.replaceChunks(ck, md, Chunker.ChunkMaxChars(w.opts.chunkChars.value)), Unused.Why.Reported)
    }
    w.touchedSources.foreach { u =>
      Unused(
        SourceRepo.placeFindingsIn(w.citekeyOf(u), GeocodeSettings.ToleranceKm(w.opts.toleranceKm.value)),
        Unused.Why.Reported
      )
    }
    w.insertedSources.foreach(u => SourceRepo.promote(w.citekeyOf(u)))

  /** A source's body into its row; a stored original into `files`. */
  def blobIn(use: BlobUse, sha: Sha256, w: BundleImport.Writer, files: ContentStore): Option[BlobSink] =
    given DbCon = w.transaction
    use match
      case BlobUse.Body(u) =>
        Some(
          BlobSink.Held(bytes =>
            sql"UPDATE sources SET ocr_markdown = ${String(bytes, UTF_8)} WHERE uid = $u".update.run(): Unit
          )
        )
      case BlobUse.Original(_, ext) =>
        Some(
          BlobSink.Streamed(in =>
            files
              .store(sha, ContentStore.Ext(ext.value), in)
              .left
              .toOption
          )
        )
      case _ => None
