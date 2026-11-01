package com.histmersion.db

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
  BundleSql,
  CarriedKinds,
  ImportAbort,
  ImportOptions,
  MergeRepo,
  MergeRuleSet
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
      merge: MergeSettings,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  ): Either[String, BundleImportResult] =
    BundleImporter.run(ds, ByteSource.file(file), bundleSha256, opts, originals, resolutions, merge, kinds, rules)

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
      backup: () => Either[String, String],
      kinds: CarriedKinds,
      rules: MergeRuleSet
  ): Either[String, MergeApplyResult] =
    BundleImporter.apply(
      ds,
      ByteSource.file(file),
      bundleSha256,
      opts,
      originals,
      resolutions,
      merge,
      planHash,
      backup,
      kinds,
      rules
    )

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

  private def rLit(p: Option[BundleRange]): Option[String] = BundleSql.rangeLiteral(p)

  /** Applies the decided lines, one step per kind's part. One instance per import; it remembers what the later steps
    * need, and gives every kind's steps what they share: the lines and this collection's rows, which lines are written,
    * what the merge decided besides them, the labels records have here now, and the rows written as this collection's
    * own.
    */
  final class Writer(
      /** The bundle's lines, in this collection's uids and labels. */
      val es: BundleEntities,
      decided: List[MergePlanner.Row],
      decisionOf: Map[UUID, Decision],
      /** Whether the line of a uid is written. */
      val writes: UUID => Boolean,
      /** This collection's rows as lines, before the import. */
      val local: BundleEntities,
      skipAliases: Set[String],
      /** What the merge does besides writing the decided lines. */
      val merged: MergePlanner.Apply,
      opts: Options,
      kinds: CarriedKinds
  )(using
      /** The import's transaction, which every step writes in. */
      val transaction: DbCon
  ):

    private def decision(u: UUID): Option[Decision] = decisionOf.get(u)
    private def inserts(u: UUID): Boolean = decision(u).contains(Decision.Insert)

    /** citekey / slug of a uid as it stands NOW (after the writes so far). */
    def citekeyOf(u: UUID): String =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[String].run().head
    private def slugOf(u: UUID): String = sql"SELECT slug FROM articles WHERE uid = $u".query[String].run().head

    /** The citekey of a source here, when its uid names one. */
    def citekeyHere(u: UUID): Option[String] =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[String].run().headOption
    private def goalSlugOf(u: UUID): Option[String] =
      sql"SELECT slug FROM goals WHERE uid = $u".query[String].run().headOption

    /** The label a target has HERE, when its uid names a row here; else the bundle's label. */
    def targetLabel(tt: String, tid: String, tuid: Option[UUID]): String =
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

    /** `work` written as this collection's own: the change clocks move and a delete leaves a tombstone. */
    def asLocal(work: => Unit): Unit =
      importing(false)
      work
      importing(true)

    // what the derived step and pass 2 need
    private val copied = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val bodyChanged = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val sourcesTouched = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val insertedSources = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val assetsBy = es.assets.map(a => a.uid -> a).toMap
    private val versionsBy = es.versions.map(v => v.uid -> v).toMap

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
      merged.renames.foreach { (_, from, to) =>
        importing(false)
        SourceRepo.renameIn(from, to).left.foreach(e => throw ImportAbort(List(s"source $from → $to: ${e.message}")))
        sql"DELETE FROM source_aliases WHERE alias = $from".update.run()
        importing(true)
      }
      val localCk = local.sources.map(s => s.uid -> s.citekey).toMap ++
        merged.renames.map((u, _, to) => u -> to)
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
        citekeyHere(e.dstUid).foreach { dst =>
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
    def followRenames[A](
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

    def articleRenames(): Unit =
      followRenames(es.articles, _.uid, _.slug, local.articles.map(a => a.uid -> a.slug).toMap, "article")(
        ArticleRepo.renameIn
      )

    def goalRenames(): Unit =
      followRenames(es.goals, _.uid, _.slug, local.goals.map(g => g.uid -> g.slug).toMap, "goal")(GoalRepo.renameIn)

    /** The articles: the text a merge replaces kept as a version first, the rows, and an article kept beside one here
      * (`both`).
      */
    def articles(): Unit =
      merged.snapshots.toList.filter(writes).foreach { u =>
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
      merged.copies.foreach { a =>
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
      merged.versions.foreach { v =>
        sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
              SELECT slug, ${v.body}, ${v.summary}, ${v.versionOf}::timestamptz FROM articles WHERE uid = ${v.articleUid}""".update
          .run()
      }

    /** A goal's links (unioned with this collection's) and schedules (this collection's kept, #881). A linked record is
      * found by its uid, so the link follows a rename or a merge; a queue item that is not here is left out. A schedule
      * brought in has no next run until it is saved here: an imported collection never starts agent runs on its own.
      */
    private def goalSets(g: BundleGoal): Unit =
      val slug = sql"SELECT slug FROM goals WHERE uid = ${g.uid}".query[String].run().head
      g.links.getOrElse(Nil).foreach { l =>
        val ref: Option[String] = l.kind match
          case "source"  => l.refUid.flatMap(citekeyHere).orElse(Some(l.ref))
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
      merged.bodyRenames.foreach { (u, renames) =>
        renames.foreach { (from, to) =>
          sql"""UPDATE sources SET ocr_markdown = replace(ocr_markdown, ${"assets/" + from}, ${"assets/" + to})
                WHERE uid = $u""".update.run()
        }
        bodyChanged += u
      }

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

    // ── the derived state, recomputed for what changed (with the import flag OFF: local consequences) ──

    /** The search chunks of a changed body, the finding places of a touched source, a new source's parked edges. */
    def sourcesDerived(): Unit =
      bodyChanged.foreach { u =>
        val (ck, md) =
          sql"SELECT citekey, ocr_markdown FROM sources WHERE uid = $u".query[(String, Option[String])].run().head
        val _ = SourceRepo.replaceChunks(ck, md, opts.chunkChars)
      }
      sourcesTouched.foreach { u =>
        val _ = SourceRepo.placeFindingsIn(citekeyOf(u), opts.toleranceKm)
      }
      insertedSources.foreach(u => SourceRepo.promote(citekeyOf(u)))

    /** The links of the articles written — of every article when a new source can turn an `[[x]]` that named nothing
      * into a source link.
      */
    def articleLinks(): Unit =
      val relink =
        if insertedSources.nonEmpty || merged.renames.nonEmpty then
          sql"SELECT slug, body FROM articles ORDER BY slug".query[(String, String)].run().toList
        else
          (es.articles.filter(a => writes(a.uid)).map(_.uid) ++ copied).flatMap(u =>
            sql"SELECT slug, body FROM articles WHERE uid = $u".query[(String, String)].run().headOption
          )
      relink.foreach((slug, body) => ArticleRepo.writeLinks(slug, body))
