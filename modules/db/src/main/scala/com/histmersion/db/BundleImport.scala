package com.histmersion.db

import com.histmersion.util.database.RangeSql

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.files.BundleEntities.BlobUse

import com.augustnagro.magnum.*
import com.histmersion.domain.automation.worklists.QueueRepo
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.values.BundleKind
import com.histmersion.util.data.exchange.{
  BlobSink,
  BundleFormat,
  BundleImporter,
  BundlePlan,
  CarriedKinds,
  ImportAbort,
  ImportOptions,
  MergeRepo
}
import com.histmersion.util.data.exchange.BundlePlan.Decision
import com.histmersion.util.storage.{ByteSource, ContentStore}

/** What `bundle import` and `merge apply` write for each kind of record (docs/research-merge-import.md §7.3): the
  * [[Writer]] whose steps the carried kinds register, and the entry points for a bundle on disk. The transaction, the
  * lock, the plan's checks, the blob pass and the ledger are [[BundleImporter]]'s.
  *
  * A foreign row is the local row with the same uid, or no row; what happens to it is [[BundlePlan]]'s decision, or the
  * merge plan's. Rows are written in dependency order, with the bundle's own uids, origins and timestamps.
  */
object BundleImport:

  type Options = ImportOptions
  val Options: ImportOptions.type = ImportOptions

  /** The ledger's foreign rev for a tombstone, which has no content to hash. */
  val DeletedRev: String = BundlePlan.DeletedRev

  /** `bundle import` of a bundle on disk ([[BundleImporter.run]]). */
  def run(
      ds: DataSource,
      file: Path,
      bundleSha256: String,
      opts: Options,
      originals: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings
  ): Either[String, BundleImportResult] =
    BundleImporter.run(ds, ByteSource.file(file), bundleSha256, opts, originals, resolutions, merge)

  /** `merge apply` of a bundle on disk ([[BundleImporter.apply]]). */
  def apply(
      ds: DataSource,
      file: Path,
      bundleSha256: String,
      opts: Options,
      originals: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      planHash: String,
      backup: () => Either[String, String]
  ): Either[String, MergeApplyResult] =
    BundleImporter.apply(ds, ByteSource.file(file), bundleSha256, opts, originals, resolutions, merge, planHash, backup)

  /** What a merge leaves to do: a `/review-article` task per merged article, and a `log` entry. The articles to review,
    * and the log entry's id.
    */
  def mergeTasks(
      m: BundleManifest,
      from: String,
      mergeId: UUID,
      ap: MergePlanner.Apply,
      counts: List[BundleKindCounts],
      dump: String
  )(using DbCon): (List[String], Int) =
    val short = mergeId.toString.take(8)
    val reviews = ap.reviews.map { slug =>
      QueueRepo.insert(
        Queue.Kind.Tasks,
        Queue.QueueAdd(
          s"/review-article $slug --fix — merged with $from's (merge $short)",
          topic = Some("merge"),
          reason = Some(s"`merge apply` changed the article; a merged text is not reviewed")
        )
      )
      slug
    }
    val t = ap.tally
    val log = QueueRepo.insert(
      Queue.Kind.Log,
      Queue.QueueAdd(
        s"Merged the bundle of $from (${m.bundleId.toString.take(8)}…) as merge $short: " +
          s"${Plural.count(t.sourcesMerged, "source")} merged, ${counts.find(_.kind == BundleKind.Source).fold(0)(_.inserted)} new, " +
          s"${Plural.count(reviews.size, "article")} to review; backup $dump",
        topic = Some("merge")
      )
    )
    (reviews, log.id)

  private def importing(on: Boolean)(using DbCon): Unit = MergeRepo.importing(on)

  // ── the writes ──────────────────────────────────────────────────────────────────────────────

  /** A bundle range as the `int8range` literal ([[RangeSql.bounds]]), NULL when the bundle has none. */
  private def rLit(p: Option[BundleRange]): Option[String] = p.map(r => RangeSql.bounds(r.start, r.end))

  /** A bundle range where no range means every year (`(,)`) — places, relations, types, periods. */
  private def rAll(p: Option[BundleRange]): String = RangeSql.bounds(p.flatMap(_.start), p.flatMap(_.end))

  /** Applies the decided lines, one step per kind's part. One instance per import; it remembers what the later steps
    * need.
    */
  final class Writer(
      es: BundleEntities,
      decided: List[MergePlanner.Row],
      decisionOf: Map[UUID, Decision],
      writes: UUID => Boolean,
      local: BundleEntities,
      skipAliases: Set[String],
      apply: MergePlanner.Apply,
      opts: Options,
      kinds: CarriedKinds
  )(using DbCon):

    private def decision(u: UUID): Option[Decision] = decisionOf.get(u)
    private def inserts(u: UUID): Boolean = decision(u).contains(Decision.Insert)

    /** citekey / slug / id of a uid as it stands NOW (after the writes so far). */
    private def citekeyOf(u: UUID): String =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[String].run().head
    private def slugOf(u: UUID): String = sql"SELECT slug FROM articles WHERE uid = $u".query[String].run().head
    private def citekeyOpt(u: UUID): Option[String] =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[String].run().headOption
    private def eventIdOf(u: UUID): Option[String] =
      sql"SELECT id FROM events WHERE uid = $u".query[String].run().headOption
    private def goalSlugOf(u: UUID): Option[String] =
      sql"SELECT slug FROM goals WHERE uid = $u".query[String].run().headOption

    /** The label a target has HERE, when its uid names a row here; else the bundle's label. */
    private def targetLabel(tt: String, tid: String, tuid: Option[UUID]): String =
      tuid
        .flatMap { u =>
          val q = tt match
            case "finding" => Some(sql"SELECT id FROM findings WHERE uid = $u")
            case "source"  => Some(sql"SELECT citekey FROM sources WHERE uid = $u")
            case "article" => Some(sql"SELECT slug FROM articles WHERE uid = $u")
            case "event"   => Some(sql"SELECT id FROM events WHERE uid = $u")
            case _         => None
          q.flatMap(_.query[String].run().headOption)
        }
        .getOrElse(tid)

    /** A place the other side renamed whose line is not written here (this collection's version was kept) keeps its id:
      * the bundle's events and child places that name the new id point at it.
      */
    private val placeHere: String => String =
      val ours = local.places.map(p => p.uid -> p.id).toMap
      val kept =
        es.places.filterNot(p => writes(p.uid)).flatMap(p => ours.get(p.uid).filter(_ != p.id).map(p.id -> _)).toMap
      id => kept.getOrElse(id, id)

    // what the derived step and pass 2 need
    private val copied = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val bodyChanged = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val sourcesTouched = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val insertedSources = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val assetsBy = es.assets.map(a => a.uid -> a).toMap
    private val versionsBy = es.versions.map(v => v.uid -> v).toMap

    // the event-catalogue sets look each of their events up once
    private val eventIds = scala.collection.mutable.Map.empty[UUID, Option[String]]
    private def eventId(u: UUID): Option[String] = eventIds.getOrElseUpdate(u, eventIdOf(u))

    /** Tombstones first: a row the other side deleted makes room for one it created in its place. */
    def tombstones(): Unit =
      val tombs = es.tombstones.map(t => t.uid -> t).toMap
      decided
        .filter(d => d.tombstone && (d.decision == Decision.Delete || d.decision == Decision.RecordTombstone))
        .foreach { d =>
          val t = tombs(d.uid)
          if d.decision == Decision.Delete then
            val table = kinds.tableOf(t.kind).value
            Sql.of(s"DELETE FROM $table WHERE uid = ?::uuid", Param.S(t.uid.toString)).frag.update.run()
          sql"""INSERT INTO tombstones (uid, kind, origin, deleted_at)
                VALUES (${t.uid}, ${t.kind.wire}, ${t.origin}, ${t.deletedAt}::timestamptz)
                ON CONFLICT (uid) DO UPDATE SET deleted_at = EXCLUDED.deleted_at""".update.run()
        }

    def sources(): Unit =
      // `rename-left`: a source here gives its citekey up first, to the bundle's work that has it; the
      // old citekey does not stay an alias (it is the other work's now)
      apply.renames.foreach { (_, from, to) =>
        importing(false)
        SourceRepo.renameIn(from, to).left.foreach(e => throw ImportAbort(List(s"source $from → $to: ${e.message}")))
        sql"DELETE FROM source_aliases WHERE alias = $from".update.run()
        importing(true)
      }
      val localCk = local.sources.map(s => s.uid -> s.citekey).toMap ++
        apply.renames.map((u, _, to) => u -> to)
      es.sources.filter(s => writes(s.uid)).foreach { s =>
        // renamed over there: the whole rename here first (findings, links, aliases follow)
        localCk.get(s.uid).filter(_ != s.citekey).foreach { old =>
          importing(false)
          SourceRepo
            .renameIn(old, s.citekey)
            .left
            .foreach(e => throw ImportAbort(List(s"source $old → ${s.citekey}: ${e.message}")))
          importing(true)
        }
        if inserts(s.uid) then
          insertedSources += s.uid
          bodyChanged ++= s.bodySha256.map(_ => s.uid)
          sql"""INSERT INTO sources (uid, origin, citekey, title, authors, doi, year, venue, status, topics,
                  regions, tags, period, summary, notes, source, cited_by_count, influential_citations,
                  added_date, sha256, updated_at)
                VALUES (${s.uid}, ${s.origin}, ${s.citekey}, ${s.title}, ${s.authors}::text[], ${s.doi},
                  ${s.year}, ${s.venue}, ${s.status.wire}, ${s.topics}::text[], ${s.regions}::text[],
                  ${s.tags}::text[],
                  ${rLit(s.period)}::int8range,
                  ${s.summary}, ${s.notes}, ${s.provenance}, ${s.citedByCount}, ${s.influentialCitations},
                  ${s.addedDate}::date, ${s.originalSha256}, ${s.updatedAt}::timestamptz)""".update.run()
        else
          val before = local.sources.find(_.uid == s.uid)
          if before.flatMap(_.bodySha256) != s.bodySha256 then
            if s.bodySha256.isEmpty then
              sql"UPDATE sources SET ocr_markdown = NULL WHERE uid = ${s.uid}".update.run()
              sql"DELETE FROM source_chunks WHERE citekey = ${s.citekey}".update.run()
            else bodyChanged += s.uid
          sql"""UPDATE sources SET origin = ${s.origin}, title = ${s.title}, authors = ${s.authors}::text[],
                  doi = ${s.doi}, year = ${s.year}, venue = ${s.venue}, status = ${s.status.wire},
                  topics = ${s.topics}::text[], regions = ${s.regions}::text[], tags = ${s.tags}::text[],
                  period = ${rLit(s.period)}::int8range,
                  summary = ${s.summary}, notes = ${s.notes}, source = ${s.provenance},
                  cited_by_count = ${s.citedByCount}, influential_citations = ${s.influentialCitations},
                  added_date = ${s.addedDate}::date, sha256 = ${s.originalSha256},
                  updated_at = ${s.updatedAt}::timestamptz
                WHERE uid = ${s.uid}""".update.run()
        sourcesTouched += s.uid
        // the ISBNs (doi and sha256 are derived from the row by V032's trigger)
        sql"DELETE FROM source_identifiers WHERE source_uid = ${s.uid} AND scheme = 'isbn'".update.run()
        s.isbns
          .getOrElse(Nil)
          .foreach(i => sql"""INSERT INTO source_identifiers (source_uid, scheme, value) VALUES (${s.uid}, 'isbn', $i)
                ON CONFLICT DO NOTHING""".update.run())
      }

    /** A source's whole edge set travels with it: both sets are emptied for every source written, then its edges. */
    def edges(): Unit =
      es.sources.filter(s => writes(s.uid)).foreach { s =>
        val ck = s.citekey
        sql"DELETE FROM edges WHERE src = $ck".update.run()
        sql"DELETE FROM unresolved_edges WHERE src = $ck".update.run()
      }
      es.edges.filter(e => writes(e.srcUid)).foreach { e =>
        // a target deleted here: the edge is left out (reported by the reference check)
        citekeyOpt(e.dstUid).foreach { dst =>
          val src = citekeyOf(e.srcUid)
          sql"INSERT INTO edges (src, dst, kind) VALUES ($src, $dst, ${e.kind}) ON CONFLICT DO NOTHING".update.run()
        }
      }

    def unresolvedEdges(): Unit =
      es.unresolvedEdges.filter(e => writes(e.srcUid)).foreach { e =>
        val _ = SourceRepo.insertEdges(List(Edge(citekeyOf(e.srcUid), e.dst, e.kind)))
      }

    def aliases(): Unit =
      es.aliases.filterNot(a => skipAliases(a.alias)).foreach { a =>
        // never an alias equal to the source's own citekey (a key renamed back here, #827)
        val n = sql"""INSERT INTO source_aliases (alias, citekey, created_at)
              SELECT ${a.alias}, citekey, ${a.createdAt}::timestamptz FROM sources
              WHERE uid = ${a.sourceUid} AND citekey <> ${a.alias}
              ON CONFLICT (alias) DO NOTHING""".update.run()
        if n > 0 then sql"DELETE FROM alias_tombstones WHERE alias = ${a.alias}".update.run(): Unit
      }

    def findings(): Unit =
      es.findings.filter(f => writes(f.uid)).foreach { f =>
        sourcesTouched += f.sourceUid
        val ck = citekeyOf(f.sourceUid)
        sql"""INSERT INTO findings (uid, origin, id, citekey, claim, region_name, geom, period, period_name,
                tags, confidence, significance, updated_at)
              VALUES (${f.uid}, ${f.origin}, ${f.id}, $ck, ${f.claim}, ${f.region},
                CASE WHEN ${f.lon}::float8 IS NULL OR ${f.lat}::float8 IS NULL THEN NULL
                     ELSE ST_SetSRID(ST_MakePoint(${f.lon}::float8, ${f.lat}::float8), 4326) END,
                ${rLit(f.period)}::int8range,
                ${f.periodName}, ${f.tags}::text[], ${f.confidence}, ${f.significance}, ${f.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, id = EXCLUDED.id,
                citekey = EXCLUDED.citekey, claim = EXCLUDED.claim, region_name = EXCLUDED.region_name,
                geom = EXCLUDED.geom, period = EXCLUDED.period, period_name = EXCLUDED.period_name,
                tags = EXCLUDED.tags, confidence = EXCLUDED.confidence, significance = EXCLUDED.significance,
                updated_at = EXCLUDED.updated_at""".update.run()
      }

    /** The newer verdict wins (§4); a source that is not here (deleted, skipped) has none. */
    def assessments(): Unit =
      es.assessments.foreach { a =>
        sql"""INSERT INTO assessments (citekey, checked_at, retracted, notices, cited_by, influential, verdict)
              SELECT citekey, ${a.checkedAt}::timestamptz, ${a.retracted}, ${BundleFormat.printer.print(
            a.notices
          )}::jsonb,
                     ${a.citedBy}, ${a.influential}, ${a.verdict}
              FROM sources WHERE uid = ${a.sourceUid}
              ON CONFLICT (citekey) DO UPDATE SET checked_at = EXCLUDED.checked_at,
                retracted = EXCLUDED.retracted, notices = EXCLUDED.notices, cited_by = EXCLUDED.cited_by,
                influential = EXCLUDED.influential, verdict = EXCLUDED.verdict
              WHERE assessments.checked_at < EXCLUDED.checked_at""".update.run()
      }

    /** Rename what the other side renamed (the same uid, a new label), through the ordinary rename path, before the row
      * itself is written (an upsert keeps the label it finds).
      */
    private def followRenames[A](
        lines: List[A],
        uid: A => UUID,
        label: A => String,
        localLabel: Map[UUID, String],
        what: String
    )(rename: (String, String) => Either[ApiError, ?]): Unit =
      lines.filter(a => writes(uid(a))).foreach { a =>
        localLabel.get(uid(a)).filter(_ != label(a)).foreach { old =>
          importing(false)
          rename(old, label(a)).left.foreach(e => throw ImportAbort(List(s"$what $old → ${label(a)}: ${e.message}")))
          importing(true)
        }
      }

    /** A place renamed over there (#888): the rename here first — its names, parents, footprints, events and finding
      * assignments follow by key, and the old id stays an alias — then the row.
      */
    def placeRenames(): Unit =
      followRenames(es.places, _.uid, _.id, local.places.map(p => p.uid -> p.id).toMap, "place")(PlaceRepo.renameIn)

    /** Places of the other collection's own (V052): before the events that point at them. Their sets (names, parents,
      * links, types) are replaced; a parent that is neither here nor in the bundle is left out.
      */
    def places(): Unit =
      val places = es.places.filter(p => writes(p.uid))
      places.foreach { p =>
        val range = rAll(p.period)
        val props = p.properties.map(BundleFormat.printer.print)
        sql"""INSERT INTO places (uid, origin, id, label, kind, geom, period, source, properties, dataset, updated_at)
              VALUES (${p.uid}, ${p.origin}, ${p.id}, ${p.label}, ${p.kind}, ST_GeomFromEWKB(decode(${p.geom}, 'hex')),
                $range::int8range, ${p.source}, $props::jsonb, 'local',
                ${p.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, label = EXCLUDED.label, kind = EXCLUDED.kind,
                geom = EXCLUDED.geom, period = EXCLUDED.period, source = EXCLUDED.source,
                properties = EXCLUDED.properties, dataset = 'local', updated_at = EXCLUDED.updated_at""".update.run()
      }
      places.foreach { p =>
        val id = sql"SELECT id FROM places WHERE uid = ${p.uid}".query[String].run().head
        sql"DELETE FROM place_names WHERE place_id = $id AND import_source = ''".update.run()
        sql"DELETE FROM place_relations WHERE child_id = $id".update.run()
        sql"DELETE FROM place_links WHERE place_id = $id".update.run()
        sql"DELETE FROM place_types WHERE place_id = $id".update.run()
        p.names.foreach { n =>
          sql"""INSERT INTO place_names (place_id, name, lang, period)
                VALUES ($id, ${n.name}, ${n.lang.getOrElse("")},
                  ${rLit(n.period)}::int8range)
                ON CONFLICT DO NOTHING""".update.run()
        }
        p.relations.foreach { r =>
          val range = rAll(r.period)
          sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
                SELECT $id, ${placeHere(r.parentId)}, ${r.kind}, $range::int8range
                WHERE EXISTS (SELECT 1 FROM places WHERE id = ${placeHere(r.parentId)})
                ON CONFLICT DO NOTHING""".update.run()
        }
        p.links.foreach { l =>
          sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
                VALUES ($id, ${l.scheme}, ${l.externalId}, ${l.relation}) ON CONFLICT DO NOTHING""".update.run()
        }
        p.types.foreach { t =>
          val range = rAll(t.period)
          sql"""INSERT INTO place_types (place_id, type_label, period, citekey)
                VALUES ($id, ${t.typeLabel}, $range::int8range, ${t.citekey})
                ON CONFLICT DO NOTHING""".update.run()
        }
      }

    /** Named periods of the other collection's own (V052), then their parents. */
    def periods(): Unit =
      val periods = es.periods.filter(p => writes(p.uid))
      periods.foreach { p =>
        val range = rAll(p.period)
        sql"""INSERT INTO periods (uid, origin, id, label, authority, period, region, dataset, updated_at)
              VALUES (${p.uid}, ${p.origin}, ${p.id}, ${p.label}, ${p.authority},
                $range::int8range, ${p.region}, 'local', ${p.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, label = EXCLUDED.label,
                authority = EXCLUDED.authority, period = EXCLUDED.period, region = EXCLUDED.region,
                dataset = 'local', updated_at = EXCLUDED.updated_at""".update.run()
      }
      periods.foreach { p =>
        sql"""UPDATE periods SET parent_id = (SELECT id FROM periods WHERE id = ${p.parentId}), updated_at = ${p.updatedAt}::timestamptz
              WHERE uid = ${p.uid}""".update.run()
      }

    /** Old place ids (V055), as old slugs: never one that is a place id here, never one another place has. */
    def placeAliases(): Unit =
      es.placeAliases.foreach { a =>
        sql"""INSERT INTO place_aliases (alias, place_id, created_at)
              SELECT ${a.alias}, id, ${a.createdAt}::timestamptz FROM places
              WHERE uid = ${a.placeUid} AND id <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM places WHERE id = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }

    def articleRenames(): Unit =
      followRenames(es.articles, _.uid, _.slug, local.articles.map(a => a.uid -> a.slug).toMap, "article")(
        ArticleRepo.renameIn
      )

    def eventRenames(): Unit =
      followRenames(es.events, _.uid, _.id, local.events.map(e => e.uid -> e.id).toMap, "event")(EventRepo.renameIn)

    def goalRenames(): Unit =
      followRenames(es.goals, _.uid, _.slug, local.goals.map(g => g.uid -> g.slug).toMap, "goal")(GoalRepo.renameIn)

    /** The articles: the text a merge replaces kept as a version first, the rows, and an article kept beside one here
      * (`both`).
      */
    def articles(): Unit =
      apply.snapshots.toList.filter(writes).foreach { u =>
        sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
              SELECT slug, body, summary, updated_at FROM articles WHERE uid = $u""".update.run()
      }
      es.articles.filter(a => writes(a.uid)).foreach { a =>
        sql"""INSERT INTO articles (uid, origin, slug, title, kind, body, summary, tags, regions, period, updated_at)
              VALUES (${a.uid}, ${a.origin}, ${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
                ${a.tags}::text[], ${a.regions}::text[],
                ${rLit(a.period)}::int8range,
                ${a.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, title = EXCLUDED.title,
                kind = EXCLUDED.kind, body = EXCLUDED.body, summary = EXCLUDED.summary, tags = EXCLUDED.tags,
                regions = EXCLUDED.regions, period = EXCLUDED.period, updated_at = EXCLUDED.updated_at""".update.run()
      }
      // an article kept beside one here (`both`), and the bundle's texts the merge replaced or
      // rewrote, recorded as versions: the next merge finds them in this collection's history
      apply.copies.foreach { a =>
        sql"""INSERT INTO articles (uid, slug, title, kind, body, summary, tags, regions, period)
              VALUES (${a.uid}, ${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary}, ${a.tags}::text[],
                ${a.regions}::text[],
                ${rLit(a.period)}::int8range)
              ON CONFLICT (uid) DO NOTHING""".update.run()
        copied += a.uid
      }

    /** Old slugs, as old citekeys: never one that is a slug here, never one another article has. */
    def articleAliases(): Unit =
      es.articleAliases.foreach { a =>
        sql"""INSERT INTO article_aliases (alias, slug, created_at)
              SELECT ${a.alias}, slug, ${a.createdAt}::timestamptz FROM articles
              WHERE uid = ${a.articleUid} AND slug <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }

    /** The versions a merge records beside the article texts it wrote. */
    def mergedVersions(): Unit =
      apply.versions.foreach { v =>
        sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
              SELECT slug, ${v.body}, ${v.summary}, ${v.versionOf}::timestamptz FROM articles WHERE uid = ${v.articleUid}""".update
          .run()
      }

    /** Events: the rows, then their parents (a parent may come later in uid order), then the sets. */
    def events(): Unit =
      val events = es.events.filter(e => writes(e.uid))
      events.foreach { e =>
        val names = e.names.getOrElse(Nil)
        sql"""INSERT INTO events (uid, origin, id, label, kind, not_before, begin_by, end_from, not_after,
                historicity, geom, place_id, description, citekey, updated_at, scale,
                catalogue, names, wikidata, central, plus_minus, dating_method)
              VALUES (${e.uid}, ${e.origin}, ${e.id}, ${e.label}, ${e.kind}, ${e.notBefore}, ${e.beginBy},
                ${e.endFrom}, ${e.notAfter}, ${e.historicity}, ST_GeomFromEWKB(decode(${e.geom}, 'hex')),
                ${e.placeId.map(placeHere)}, ${e.description}, ${e.citekey}, ${e.updatedAt}::timestamptz,
                ${e.scale.flatMap(com.histmersion.api.EventScale.parse(_).flatten)},
                ${e.catalogue}, $names::text[], ${e.wikidata}, ${e.central}, ${e.plusMinus}, ${e.datingMethod})
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, label = EXCLUDED.label,
                kind = EXCLUDED.kind, not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by,
                end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
                historicity = EXCLUDED.historicity, geom = EXCLUDED.geom, place_id = EXCLUDED.place_id,
                description = EXCLUDED.description, citekey = EXCLUDED.citekey,
                scale = EXCLUDED.scale, catalogue = EXCLUDED.catalogue, names = EXCLUDED.names,
                wikidata = EXCLUDED.wikidata, central = EXCLUDED.central, plus_minus = EXCLUDED.plus_minus,
                dating_method = EXCLUDED.dating_method, updated_at = EXCLUDED.updated_at""".update.run()
      }
      events.foreach { e =>
        val parent = e.parentUid.flatMap(eventIdOf)
        sql"UPDATE events SET parent_id = $parent WHERE uid = ${e.uid}".update.run()
        val id = e.id
        sql"DELETE FROM event_datings WHERE event_id = $id".update.run()
        sql"DELETE FROM event_edges WHERE src_id = $id".update.run()
        sql"DELETE FROM event_participants WHERE event_id = $id".update.run()
        e.datings.foreach { d =>
          val ck = d.sourceUid.flatMap(citekeyOpt).getOrElse(d.citekey)
          sql"""INSERT INTO event_datings (event_id, citekey, not_before, begin_by, end_from, not_after, method, note)
                VALUES ($id, $ck, ${d.notBefore}, ${d.beginBy}, ${d.endFrom}, ${d.notAfter}, ${d.method}, ${d.note})
                ON CONFLICT DO NOTHING""".update.run()
        }
        e.edges.foreach { g =>
          val ck = g.sourceUid.flatMap(citekeyOpt).getOrElse(g.citekey)
          eventIdOf(g.dstUid).foreach { dst =>
            sql"""INSERT INTO event_edges (src_id, dst_id, kind, citekey, note)
                  VALUES ($id, $dst, ${g.kind}, $ck, ${g.note}) ON CONFLICT DO NOTHING""".update.run()
          }
        }
        e.participants.foreach { p =>
          val ck = p.sourceUid.flatMap(citekeyOpt).orElse(p.citekey)
          sql"""INSERT INTO event_participants (event_id, actor, role, citekey)
                VALUES ($id, ${p.actor}, ${p.role}, $ck) ON CONFLICT DO NOTHING""".update.run()
        }
      }

    /** Old event ids (V053): after every event is in. */
    def eventAliases(): Unit =
      es.eventAliases.foreach { a =>
        sql"""INSERT INTO event_aliases (alias, event_id, created_at)
              SELECT ${a.alias}, id, ${a.createdAt}::timestamptz FROM events
              WHERE uid = ${a.eventUid} AND id <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM events WHERE id = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }

    /** A goal's links (unioned with this collection's) and schedules (this collection's kept, #881). A linked record is
      * found by its uid, so the link follows a rename or a merge; a queue item that is not here is left out. A schedule
      * brought in has no next run until it is saved here: an imported collection never starts agent runs on its own.
      */
    private def goalSets(g: BundleGoal): Unit =
      val slug = sql"SELECT slug FROM goals WHERE uid = ${g.uid}".query[String].run().head
      g.links.getOrElse(Nil).foreach { l =>
        val ref: Option[String] = l.kind match
          case "source"  => l.refUid.flatMap(citekeyOpt).orElse(Some(l.ref))
          case "article" =>
            l.refUid
              .flatMap(u => sql"SELECT slug FROM articles WHERE uid = $u".query[String].run().headOption)
              .orElse(Some(l.ref))
          case "queue-item" =>
            l.refUid.flatMap(u => sql"SELECT id::text FROM queue_items WHERE uid = $u".query[String].run().headOption)
          case _ => Some(l.ref)
        ref.foreach { r =>
          sql"""INSERT INTO goal_links (goal_slug, kind, ref, note, created_at)
                VALUES ($slug, ${l.kind}, $r, ${l.note}, ${l.createdAt}::timestamptz) ON CONFLICT DO NOTHING""".update
            .run()
        }
      }
      g.schedules.getOrElse(Nil).foreach { x =>
        val usd = x.budgetUsd.map(BigDecimal(_))
        sql"""INSERT INTO goal_schedules (goal_slug, kind, cron, enabled, budget_usd, last_note)
              VALUES ($slug, ${x.kind}, ${x.cron}, ${x.enabled}, $usd,
                'brought in by a merge: save it once here to schedule it')
              ON CONFLICT DO NOTHING""".update.run()
      }

    /** Goals: the rows, their parents, then — after every goal is in (a link can name another goal's record, a schedule
      * its own goal) — their sets.
      */
    def goals(): Unit =
      val goals = es.goals.filter(g => writes(g.uid))
      goals.foreach { g =>
        val plan = g.plan.map(BundleFormat.printer.print)
        val usd = g.budgetUsd.map(BigDecimal(_))
        sql"""INSERT INTO goals (uid, origin, slug, title, status, notes, tags, regions, period, created_at, updated_at,
                plan, plan_approved_at, budget_usd)
              VALUES (${g.uid}, ${g.origin}, ${g.slug}, ${g.title}, ${g.status}, ${g.notes}, ${g.tags}::text[],
                ${g.regions}::text[],
                ${rLit(g.period)}::int8range,
                ${g.createdAt}::timestamptz, ${g.updatedAt}::timestamptz, $plan::jsonb, ${g.planApprovedAt}::timestamptz, $usd)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, title = EXCLUDED.title,
                status = EXCLUDED.status, notes = EXCLUDED.notes, tags = EXCLUDED.tags,
                regions = EXCLUDED.regions, period = EXCLUDED.period, created_at = EXCLUDED.created_at,
                updated_at = EXCLUDED.updated_at, plan = EXCLUDED.plan, plan_approved_at = EXCLUDED.plan_approved_at,
                budget_usd = EXCLUDED.budget_usd""".update.run()
      }
      goals.foreach { g =>
        val parent = g.parentUid.flatMap(goalSlugOf)
        sql"UPDATE goals SET parent_slug = $parent, updated_at = ${g.updatedAt}::timestamptz WHERE uid = ${g.uid}".update
          .run()
      }
      goals.foreach(goalSets)

    def goalAliases(): Unit =
      es.goalAliases.foreach { a =>
        sql"""INSERT INTO goal_aliases (alias, goal_slug, created_at)
              SELECT ${a.alias}, slug, ${a.createdAt}::timestamptz FROM goals
              WHERE uid = ${a.goalUid} AND slug <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM goals WHERE slug = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }

    def queueItems(): Unit =
      es.queue.filter(q => writes(q.uid)).foreach { q =>
        sql"""INSERT INTO queue_items (uid, origin, list, text, topic, status, reason, created_at, done_at, updated_at)
              VALUES (${q.uid}, ${q.origin}, ${q.list}, ${q.text}, ${q.topic}, ${q.status}, ${q.reason},
                ${q.createdAt}::timestamptz, ${q.doneAt}::timestamptz, ${q.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, list = EXCLUDED.list,
                text = EXCLUDED.text, topic = EXCLUDED.topic, status = EXCLUDED.status,
                reason = EXCLUDED.reason, created_at = EXCLUDED.created_at, done_at = EXCLUDED.done_at,
                updated_at = EXCLUDED.updated_at""".update.run()
      }

    /** Asset metadata whose bytes are already here (same sha): no blob needed. */
    def assetMetadata(): Unit =
      val localSha = local.assets.map(a => a.uid -> a.sha256).toMap
      es.assets.filter(a => writes(a.uid) && localSha.get(a.uid).contains(a.sha256)).foreach(writeAsset(_, None))

    // ── the event-catalogue sets (#1647) ──
    // After every event, source and place is in: each line whose event (and source) is here, merged by its own rule
    // (BundleEventSets) — the newer catalogue record, the newer candidate evidence with a decision kept over a
    // proposal, the newer check, correction and site type. Like an assessment, independent of the event line's
    // decision: these are what the importers and the person found, not the event's content. Idempotent: a line the
    // collection already has, or has newer, writes nothing.

    def eventCatalogue(): Unit =
      import BundleEventSets.newer
      if es.eventCatalogue.nonEmpty then
        val here = Sql
          .raw(s"SELECT provider, provider_id, ${BundleRepo.ts("retrieved_at")} FROM event_catalogue")
          .frag
          .query[(String, String, String)]
          .run()
          .map((p, i, at) => (p, i) -> at)
          .toMap
        es.eventCatalogue.foreach { c =>
          eventId(c.eventUid).filter(_ => newer(here.get((c.provider, c.providerId)), Some(c.retrievedAt))).foreach {
            id =>
              val record = BundleFormat.printer.print(c.record)
              sql"""INSERT INTO event_catalogue (provider, provider_id, event_id, url, label, kind, not_before, begin_by,
                    end_from, not_after, lat, lon, vei, magnitude, deaths, historicity, record, retrieved_at)
                  VALUES (${c.provider}, ${c.providerId}, $id, ${c.url}, ${c.label}, ${c.kind}, ${c.notBefore},
                    ${c.beginBy}, ${c.endFrom}, ${c.notAfter}, ${c.lat}, ${c.lon}, ${c.vei}, ${c.magnitude}, ${c.deaths},
                    ${c.historicity}, $record::jsonb, ${c.retrievedAt}::timestamptz)
                  ON CONFLICT (provider, provider_id) DO UPDATE SET event_id = EXCLUDED.event_id, url = EXCLUDED.url,
                    label = EXCLUDED.label, kind = EXCLUDED.kind, not_before = EXCLUDED.not_before,
                    begin_by = EXCLUDED.begin_by, end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
                    lat = EXCLUDED.lat, lon = EXCLUDED.lon, vei = EXCLUDED.vei, magnitude = EXCLUDED.magnitude,
                    deaths = EXCLUDED.deaths, historicity = EXCLUDED.historicity, record = EXCLUDED.record,
                    retrieved_at = EXCLUDED.retrieved_at""".update.run()
          }
        }

    def eventCandidates(): Unit =
      import BundleEventSets.newer
      if es.eventCandidates.nonEmpty then
        val here = Sql
          .raw(s"SELECT event_id, citekey, status, ${BundleRepo.ts("computed_at")} FROM event_link_candidates")
          .frag
          .query[(String, String, String, String)]
          .run()
          .map((e, c, st, at) => (e, c) -> (st, at))
          .toMap
        es.eventCandidates.foreach { c =>
          for id <- eventId(c.eventUid); ck <- citekeyOpt(c.sourceUid) do
            val passages = BundleFormat.printer.print(c.passages)
            here.get((id, ck)) match
              case None =>
                sql"""INSERT INTO event_link_candidates (event_id, citekey, score, status, findings, passages, reasons, computed_at)
                      VALUES ($id, $ck, ${c.score}, ${c.status}, ${c.findings}::text[], $passages::jsonb,
                        ${c.reasons}::text[], ${c.computedAt}::timestamptz)""".update.run(): Unit
              case Some((status, at)) =>
                val keep = BundleEventSets.candidateStatus(status, c.status)
                if newer(Some(at), Some(c.computedAt)) then
                  sql"""UPDATE event_link_candidates SET score = ${c.score}, status = $keep, findings = ${c.findings}::text[],
                          passages = $passages::jsonb, reasons = ${c.reasons}::text[], computed_at = ${c.computedAt}::timestamptz
                        WHERE event_id = $id AND citekey = $ck""".update.run(): Unit
                else if keep != status then
                  sql"UPDATE event_link_candidates SET status = $keep WHERE event_id = $id AND citekey = $ck".update
                    .run(): Unit
        }

    def eventChecks(): Unit =
      import BundleEventSets.newer
      if es.eventChecks.nonEmpty then
        val here = Sql
          .raw(s"SELECT event_id, ${BundleRepo.ts("checked_at")} FROM event_link_checks")
          .frag
          .query[(String, String)]
          .run()
          .toMap
        es.eventChecks.foreach { k =>
          eventId(k.eventUid).filter(id => newer(here.get(id), Some(k.checkedAt))).foreach { id =>
            sql"""INSERT INTO event_link_checks (event_id, checked_at) VALUES ($id, ${k.checkedAt}::timestamptz)
                  ON CONFLICT (event_id) DO UPDATE SET checked_at = EXCLUDED.checked_at""".update.run()
          }
        }

    def corrections(): Unit =
      import BundleEventSets.newer
      if es.corrections.nonEmpty then
        val clock = s"greatest(${BundleRepo.ts("updated_at")}, coalesce(${BundleRepo.ts("checked_at")}, ''))"
        val here = Sql
          .raw(s"SELECT target_kind, target_id, field, $clock FROM corrections")
          .frag
          .query[(String, String, String, String)]
          .run()
          .map((k, t, f, at) => (k, t, f) -> at)
          .toMap
        es.corrections.foreach { c =>
          // an event's correction follows the event here (by uid); a place's names a gazetteer id
          val target =
            if c.targetKind == "event" then c.targetUid.fold(Option.when(eventExists(c.targetId))(c.targetId))(eventId)
            else Some(placeHere(c.targetId))
          val at = (c.updatedAt :: c.checkedAt.toList).max
          target.filter(t => newer(here.get((c.targetKind, t, c.field)), Some(at))).foreach { t =>
            sql"""INSERT INTO corrections (target_kind, target_id, field, value, upstream, upstream_value, upstream_now,
                    status, created_at, updated_at, checked_at)
                  VALUES (${c.targetKind}, $t, ${c.field}, ${c.value}::jsonb, ${c.upstream}, ${c.upstreamValue}::jsonb,
                    ${c.upstreamNow}::jsonb, ${c.status}, ${c.createdAt}::timestamptz, ${c.updatedAt}::timestamptz,
                    ${c.checkedAt}::timestamptz)
                  ON CONFLICT (target_kind, target_id, field) DO UPDATE SET value = EXCLUDED.value,
                    upstream = EXCLUDED.upstream, upstream_value = EXCLUDED.upstream_value,
                    upstream_now = EXCLUDED.upstream_now, status = EXCLUDED.status, created_at = EXCLUDED.created_at,
                    updated_at = EXCLUDED.updated_at, checked_at = EXCLUDED.checked_at""".update.run()
          }
        }

    def siteTypes(): Unit =
      import BundleEventSets.newer
      if es.siteTypes.nonEmpty then
        val here = Sql
          .raw(
            s"SELECT id, ${BundleRepo.ts("site_type_checked_at")} FROM places WHERE site_type_checked_at IS NOT NULL"
          )
          .frag
          .query[(String, String)]
          .run()
          .toMap
        es.siteTypes.foreach { t =>
          val id = placeHere(t.placeId)
          if newer(here.get(id), t.checkedAt) then
            sql"""UPDATE places SET site_type = ${t.siteType}, site_type_checked_at = ${t.checkedAt}::timestamptz
                  WHERE id = $id""".update.run(): Unit
        }

    private def eventExists(id: String): Boolean = sql"SELECT 1 FROM events WHERE id = $id".query[Int].run().nonEmpty

    private def writeAsset(a: BundleAsset, bytes: Option[Array[Byte]]): Unit =
      sourcesTouched += a.sourceUid
      val ck = citekeyOf(a.sourceUid)
      bytes match
        case Some(b) =>
          sql"""INSERT INTO assets (uid, origin, citekey, name, mime, bytes, sha256, width, height, kind, caption,
                  license, geom, period, updated_at)
                VALUES (${a.uid}, ${a.origin}, $ck, ${a.name}, ${a.mime}, $b, ${a.sha256}, ${a.width}, ${a.height},
                  ${a.kind}, ${a.caption}, ${a.license}, ST_GeomFromEWKB(decode(${a.geom}, 'hex')),
                  ${rLit(a.period)}::int8range,
                  ${a.updatedAt}::timestamptz)
                ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, name = EXCLUDED.name,
                  mime = EXCLUDED.mime, bytes = EXCLUDED.bytes, sha256 = EXCLUDED.sha256, width = EXCLUDED.width,
                  height = EXCLUDED.height, kind = EXCLUDED.kind, caption = EXCLUDED.caption,
                  license = EXCLUDED.license, geom = EXCLUDED.geom, period = EXCLUDED.period,
                  updated_at = EXCLUDED.updated_at""".update.run()
        case None =>
          sql"""UPDATE assets SET origin = ${a.origin}, name = ${a.name}, mime = ${a.mime}, width = ${a.width},
                  height = ${a.height}, kind = ${a.kind}, caption = ${a.caption}, license = ${a.license},
                  geom = ST_GeomFromEWKB(decode(${a.geom}, 'hex')),
                  period = ${rLit(a.period)}::int8range,
                  updated_at = ${a.updatedAt}::timestamptz
                WHERE uid = ${a.uid}""".update.run()

    // ── pass 2: the blobs, each use by its kind ──

    /** Where a blob use of these lines goes: a body, a version's text and an asset's bytes into their rows, a stored
      * original into `files`.
      */
    def blobSink(use: BlobUse, sha: String, files: ContentStore): BlobSink = use match
      case BlobUse.Body(u) =>
        BlobSink.Held(bytes =>
          sql"UPDATE sources SET ocr_markdown = ${String(bytes, UTF_8)} WHERE uid = $u".update.run(): Unit
        )
      case BlobUse.Original(_, ext) =>
        BlobSink.Streamed(in => files.store(sha, ext, in).left.toOption.map(m => s"original $sha: $m"))
      case BlobUse.VersionBody(u) =>
        BlobSink.Held { bytes =>
          val v = versionsBy(u)
          sql"""INSERT INTO article_versions (uid, origin, slug, body, summary, updated_at, created_at)
                VALUES (${v.uid}, ${v.origin}, ${slugOf(v.articleUid)}, ${String(bytes, UTF_8)},
                  ${v.summary}, ${v.versionOf}::timestamptz, ${v.updatedAt}::timestamptz)
                ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, slug = EXCLUDED.slug,
                  body = EXCLUDED.body, summary = EXCLUDED.summary,
                  updated_at = EXCLUDED.updated_at, created_at = EXCLUDED.created_at""".update.run(): Unit
        }
      case BlobUse.AssetBytes(u) => BlobSink.Held(bytes => writeAsset(assetsBy(u), Some(bytes)))

    // ── after the blobs ──

    /** What a merge changes in a body it took from the bundle: the figures it renamed. */
    def bodyRenames(): Unit =
      apply.bodyRenames.foreach { (u, renames) =>
        renames.foreach { (from, to) =>
          sql"""UPDATE sources SET ocr_markdown = replace(ocr_markdown, ${"assets/" + from}, ${"assets/" + to})
                WHERE uid = $u""".update.run()
        }
        bodyChanged += u
      }

    /** This collection's own attestations a merge moved into the body it kept, or dropped. */
    def attestationFixes(): Unit =
      if apply.attestations.nonEmpty then
        importing(false) // this collection's own rows: their clocks move and a drop leaves a tombstone
        apply.attestations.foreach { f =>
          if f.drop then sql"DELETE FROM attestations WHERE uid = ${f.uid}".update.run()
          else sql"""UPDATE attestations SET char_start = ${f.anchor.map(_.start)}, char_end = ${f.anchor.map(_.end)},
                    chunk_ord = ${f.chunkOrd} WHERE uid = ${f.uid}""".update.run()
        }
        importing(true)

    def assetLinks(): Unit =
      es.assetLinks.filter(l => writes(l.uid)).foreach { l =>
        val (ck, name) =
          sql"""SELECT citekey, name FROM assets WHERE uid = ${l.assetUid}""".query[(String, String)].run().head
        val target = targetLabel(l.targetType, l.targetId, l.targetUid)
        sql"""INSERT INTO asset_links (uid, origin, citekey, asset_name, relation, target_type, target_id, xywh, notes, updated_at)
              VALUES (${l.uid}, ${l.origin}, $ck, $name, ${l.relation}, ${l.targetType}, $target, ${l.xywh},
                ${l.notes}, ${l.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, citekey = EXCLUDED.citekey,
                asset_name = EXCLUDED.asset_name, relation = EXCLUDED.relation,
                target_type = EXCLUDED.target_type, target_id = EXCLUDED.target_id, xywh = EXCLUDED.xywh,
                notes = EXCLUDED.notes, updated_at = EXCLUDED.updated_at""".update.run()
      }

    def attestations(): Unit =
      es.attestations.filter(t => writes(t.uid)).foreach { t =>
        val ck = citekeyOf(t.sourceUid)
        val target = targetLabel(t.targetType, t.targetId, t.targetUid)
        sql"""INSERT INTO attestations (uid, origin, citekey, target_type, target_id, quote, prefix, suffix,
                char_start, char_end, chunk_ord, page, canvas, xywh, cts_urn, note, created_at, updated_at)
              VALUES (${t.uid}, ${t.origin}, $ck, ${t.targetType}, $target, ${t.quote}, ${t.prefix}, ${t.suffix},
                ${t.charStart}, ${t.charEnd}, ${t.chunkOrd}, ${t.page}, ${t.canvas}, ${t.xywh}, ${t.ctsUrn},
                ${t.note}, ${t.createdAt}::timestamptz, ${t.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, citekey = EXCLUDED.citekey,
                target_type = EXCLUDED.target_type, target_id = EXCLUDED.target_id, quote = EXCLUDED.quote,
                prefix = EXCLUDED.prefix, suffix = EXCLUDED.suffix, char_start = EXCLUDED.char_start,
                char_end = EXCLUDED.char_end, chunk_ord = EXCLUDED.chunk_ord, page = EXCLUDED.page,
                canvas = EXCLUDED.canvas, xywh = EXCLUDED.xywh, cts_urn = EXCLUDED.cts_urn,
                note = EXCLUDED.note, created_at = EXCLUDED.created_at,
                updated_at = EXCLUDED.updated_at""".update.run()
      }

    // ── the derived state, recomputed for what changed (with the import flag OFF: local consequences) ──

    /** The search chunks of a changed body, the finding places of a touched source, a new source's parked edges. */
    def sourcesDerived(): Unit =
      bodyChanged.foreach { u =>
        val (ck, md) =
          sql"SELECT citekey, ocr_markdown FROM sources WHERE uid = $u".query[(String, Option[String])].run().head
        val _ = SourceRepo.replaceChunks(ck, md, opts.chunkChars)
      }
      sourcesTouched.foreach { u =>
        val _ = PlaceRepo.materialiseFor(citekeyOf(u), opts.toleranceKm)
      }
      insertedSources.foreach(u => SourceRepo.promote(citekeyOf(u)))

    /** The links of the articles written — of every article when a new source can turn an `[[x]]` that named nothing
      * into a source link.
      */
    def articleLinks(): Unit =
      val relink =
        if insertedSources.nonEmpty || apply.renames.nonEmpty then
          sql"SELECT slug, body FROM articles ORDER BY slug".query[(String, String)].run().toList
        else
          (es.articles.filter(a => writes(a.uid)).map(_.uid) ++ copied).flatMap(u =>
            sql"SELECT slug, body FROM articles WHERE uid = $u".query[(String, String)].run().headOption
          )
      relink.foreach((slug, body) => ArticleRepo.writeLinks(slug, body))
