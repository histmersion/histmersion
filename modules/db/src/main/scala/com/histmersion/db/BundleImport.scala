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

  /** What an import's steps compute once, the first time one asks ([[Writer.once]]): an index of the bundle's lines a
    * kind's steps share.
    */
  final class Once[A](val make: Writer => A)

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
      /** The import's options: the chunk size, the point tolerance, the referrers a rename runs. */
      val opts: Options,
      kinds: CarriedKinds
  )(using
      /** The import's transaction, which every step writes in. */
      val transaction: DbCon
  ):

    private def decision(u: UUID): Option[Decision] = decisionOf.get(u)

    /** Whether the line of `u` is a new row here. */
    def inserts(u: UUID): Boolean = decision(u).contains(Decision.Insert)

    /** Whether a source alias of the bundle is left out (an alias the merge gave to another work). */
    def skipsAlias(alias: String): Boolean = skipAliases(alias)

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
    private val sourcesTouched = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val sourcesInserted = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val versionsBy = es.versions.map(v => v.uid -> v).toMap
    private val computed = scala.collection.mutable.Map.empty[Once[?], Any]

    /** A source a kind's step wrote under: its finding places are placed again in the derived step. */
    def touchSource(u: UUID): Unit = sourcesTouched += u

    /** The sources a step wrote under, in the order they were first touched. */
    def touchedSources: List[UUID] = sourcesTouched.toList

    /** A source a step inserted: an article's `[[x]]` may name it now (every article is linked again). */
    def insertSource(u: UUID): Unit = sourcesInserted += u

    /** The sources the import inserted, in the order they were. */
    def insertedSources: List[UUID] = sourcesInserted.toList

    /** What `o` computes of this import, computed the first time a step asks. */
    def once[A](o: Once[A]): A = computed.getOrElseUpdate(o, o.make(this)).asInstanceOf[A]

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

    // ── pass 2: the blobs, each use by its kind ──

    /** Where a blob use of these lines goes: a version's text into its row. A source's body and original are taken by
      * the library's own kind, an asset's bytes by the media's.
      */
    def blobSink(use: BlobUse, sha: String, files: ContentStore): BlobSink = use match
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
      case BlobUse.AssetBytes(u) => throw IllegalArgumentException(s"asset $u: its bytes are the media's kind to take")
      case BlobUse.Body(u)       => throw IllegalArgumentException(s"source $u: its body is the library's kind to take")
      case BlobUse.Original(_, _) =>
        throw IllegalArgumentException(s"original $sha: it is the library's kind to take")

    // ── the derived state, recomputed for what changed (with the import flag OFF: local consequences) ──

    /** The links of the articles written — of every article when a new source can turn an `[[x]]` that named nothing
      * into a source link.
      */
    def articleLinks(): Unit =
      val relink =
        if sourcesInserted.nonEmpty || merged.renames.nonEmpty then
          sql"SELECT slug, body FROM articles ORDER BY slug".query[(String, String)].run().toList
        else
          (es.articles.filter(a => writes(a.uid)).map(_.uid) ++ copied).flatMap(u =>
            sql"SELECT slug, body FROM articles WHERE uid = $u".query[(String, String)].run().headOption
          )
      relink.foreach((slug, body) => ArticleRepo.writeLinks(slug, body))
