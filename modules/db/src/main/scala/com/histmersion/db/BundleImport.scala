package com.histmersion.db

import com.histmersion.core.*
import com.histmersion.core.BundlePlan.{Decision, Ledger, Local}
import com.histmersion.core.BundleEntities.BlobUse

import com.augustnagro.magnum.*
import io.circe.syntax.*
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.sql.DataSource

/** `bundle import` WITHOUT matching (docs/research-merge-import.md §7.3, milestone M2): into an empty
  * instance, or back into the instance a bundle came from (or a fork of it). A foreign row is the
  * local row with the same uid, or no row; what happens to it is [[BundlePlan]]'s decision.
  *
  * The import is ONE transaction, in two passes over the file:
  *
  *  1. read the manifest and the entity files ([[BundleReader]]); gather what this instance knows of
  *     every uid (its rev, a tombstone, the ledger); decide every line; check — and report ALL of —
  *     what would make the import wrong: a label (citekey, slug, id) another row already has, a
  *     reference to something neither side has, a missing blob, a gazetteer place this instance
  *     lacks. Any of those, or `dryRun`, and nothing is written. Otherwise the rows are written, in
  *     dependency order, with the bundle's own uids, origins and timestamps;
  *  2. stream the blobs: OCR bodies, version bodies, asset bytes and stored originals, each checked
  *     against its sha256 as it is consumed; then what hangs off them (asset links), and the derived
  *     state (FTS chunks, `finding_places`, `article_links`, parked edges now resolvable).
  *
  * Rows written from the bundle keep its timestamps: the V030/V031 triggers stand aside while
  * `histmersion.importing` is on. The derived steps run with it off, so a LOCAL row the import
  * changes (a parked edge promoted) moves and is exported next time like any other change.
  *
  * Then the ledger: what was merged, so the next import of a newer bundle from the same place knows
  * the common ancestor ([[BundlePlan]]). An own bundle leaves no ledger — there is nothing to remember. */
object BundleImport:

  final case class Options(dryRun: Boolean, chunkChars: Int, toleranceKm: Double)

  /** The ledger's foreign rev for a tombstone, which has no content to hash. */
  val DeletedRev: String = BundlePlan.DeletedRev

  /** A failure inside the transaction that must roll it back and still be reported as a value. */
  private final class Abort(val problems: List[String]) extends RuntimeException(problems.mkString("; "))

  private type Decided = MergePlanner.Row

  def run(ds: DataSource, file: Path, bundleSha256: String, opts: Options, originals: Originals,
      resolutions: MergeResolutions, merge: MergeConfig): Either[String, BundleImportResult] =
    val loaded = MergeState.withFile(file)(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None    => Left(("not a readable bundle" :: loaded.problems).mkString("; "))
      case Some(m) =>
        try Right(transact(ds)(importIn(file, bundleSha256, m, loaded, opts, originals, resolutions, merge)))
        catch case a: Abort => Right(failed(m, opts, a.problems))

  private def failed(m: BundleManifest, opts: Options, problems: List[String]): BundleImportResult =
    BundleImportResult(m.bundleId, m.instance, opts.dryRun, applied = false, Nil, 0, problems, Nil, Nil)

  private def withFile[A](file: Path)(f: InputStream => A): A =
    val in = Files.newInputStream(file)
    try f(in) finally in.close()

  // ── pass 1: plan, validate, write the rows ───────────────────────────────────────────────────

  /** The merge plan (M3) decides every line: the same record here (by uid, a redirect or the ledger)
    * as M2 did, and everything else as the matcher and the resolutions say. The import carries out
    * what M2 can — inserts, fast-forwards, deletions, a conflict decided `keep-left`/`keep-right`, a
    * record left out (`keep-left` on a taken label), two works kept apart (`both`) — and refuses, with
    * the plan's issues, while any issue is open or a decision needs `merge apply` (M5). */
  private def importIn(file: Path, bundleSha: String, m: BundleManifest, loaded: BundleReader.Loaded,
      opts: Options, originals: Originals, resolutions: MergeResolutions, merge: MergeConfig)(using DbCon): BundleImportResult =
    // one import at a time per database: two would decide against the same state and both write
    val _ = sql"SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('histmersion.bundle-import'))) l".query[Int].run()
    val (input, planned) = MergeState.plan(file, m, loaded, resolutions, merge, originals, opts.chunkChars)
    val plan      = planned.plan
    val es        = planned.localized
    val ownBundle = input.ownBundle
    val decided: List[Decided] = planned.rows
    val decisionOf: Map[UUID, Decision] = decided.map(d => d.uid -> d.decision).toMap
    def writes(uid: UUID): Boolean = decisionOf.get(uid).exists(d => d == Decision.Insert || d == Decision.FastForward)

    // ── what holds it up: the plan's problems, its open issues, decisions only merge apply can do ──
    def named(i: MergeIssue): String =
      s"${i.id} ${i.entity.wire} ${i.right.orElse(i.left).fold("")(_.label)} — ${i.title}"
    val holding = plan.issues.filter(_.open)
    val problems = plan.problems ++
      holding.map(i => s"${named(i)}: open — decide it (`merge plan --write-template`)") ++
      plan.resolutionErrors.map(e => s"resolution $e") ++ planned.needsApply
    val conflictIssues = plan.issues.filter(i => i.kind == IssueKind.ChangedBothSides || i.kind == IssueKind.DeletedThereChangedHere)
    val conflicts = conflictIssues.map(i => named(i) + i.decision.fold(" — open")(d => s" — decided ${d.wire}"))
    val counts = BundleKind.values.toList.map(k => BundlePlan.counts(k, decided.filter(_.kind == k).map(_.decision)))
      .filter(c => c.inserted + c.updated + c.unchanged + c.keptLocal + c.conflicts + c.deleted + c.skippedDeleted + c.held > 0)
    val needed = neededBlobs(es, writes)
    val shown  = holding ++ plan.issues.filter(i => i.decision.isDefined && planned.needsApply.exists(_.startsWith(i.id)))
    def result(applied: Boolean, mergeId: Option[UUID]) =
      BundleImportResult(m.bundleId, m.instance, opts.dryRun, applied, counts, if applied then needed.size else 0,
        problems, conflicts, planned.notes, mergeId, shown, Some(plan.planHash))

    if problems.nonEmpty || opts.dryRun then result(applied = false, None)
    else
      writeAll(file, input, planned, opts, originals)
      val mergeId = ledgerAndMerge(m, bundleSha, ownBundle, decided, result(applied = true, None).asJson, result(applied = true, None).changed)
      result(applied = true, mergeId)

  /** Pass 1 (the rows) and pass 2 (the blobs) of the decided lines, then what `merge apply` adds and
    * the derived state. Inside the caller's transaction; the importing flag is left on. */
  private def writeAll(file: Path, input: MergePlanner.Input, planned: MergePlanner.Result, opts: Options,
      originals: Originals)(using DbCon): Unit =
    val es = planned.localized
    val decided = planned.rows
    val decisionOf: Map[UUID, Decision] = decided.map(d => d.uid -> d.decision).toMap
    def writes(uid: UUID): Boolean = decisionOf.get(uid).exists(d => d == Decision.Insert || d == Decision.FastForward)
    val needed = neededBlobs(es, writes)
    importing(true)
    val w = Writer(es, decisionOf, writes, input.local, planned.skipAliases, planned.apply)
    w.deletes(decided.filter(d => d.tombstone && (d.decision == Decision.Delete || d.decision == Decision.RecordTombstone)),
      es.tombstones.map(t => t.uid -> t).toMap)
    w.sources(opts)
    w.rest()
    w.eventSets()
    // ── pass 2: the blobs ──
    if needed.nonEmpty then
      val failures = withFile(file)(in => w.blobs(in, needed, originals))
      if failures.nonEmpty then throw Abort(failures)
    w.afterBlobs()
    importing(false)
    w.derived(opts)
    importing(true)

  // ── merge apply (M5) ──────────────────────────────────────────────────────────────────────────

  /** `merge apply` (docs/research-merge-import.md §6.1, §6.4): carry out the plan of a bundle — the
    * folds of matched records included — in ONE transaction, after a backup.
    *
    * Refused (nothing written, no backup taken) while the plan has a problem, an open issue or an
    * unusable resolution, while a `file:` decision has no text, and when the plan's hash is not
    * `planHash` — the collection, the bundle or the resolutions changed since the plan the user
    * reviewed. A plan with nothing to change is applied as a no-op: no backup, no write. Otherwise
    * `backup` runs (the server takes a `pg_dump`; a refusal of it refuses the apply), then the
    * writes; then the redirects (`uid_aliases`, one per folded record), the ledger (local uid = the
    * survivor), the `merges` row with the plan hash, the backup and the resolutions, the remembered
    * decisions, a `/review-article` task per merged article and a `log` entry. Last, the same bundle is
    * planned again inside the transaction: what that plan would still change is reported — 0 when
    * the merge is idempotent. */
  def apply(ds: DataSource, file: Path, bundleSha256: String, opts: Options, originals: Originals,
      resolutions: MergeResolutions, merge: MergeConfig, planHash: String,
      backup: () => Either[String, String]): Either[String, MergeApplyResult] =
    val loaded = MergeState.withFile(file)(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None    => Left(("not a readable bundle" :: loaded.problems).mkString("; "))
      case Some(m) =>
        try Right(transact(ds)(applyIn(file, bundleSha256, m, loaded, opts, originals, resolutions, merge, planHash, backup)))
        catch case a: Abort => Right(MergeApplyResult(m.bundleId, m.instance, applied = false, planHash, problems = a.problems))

  private def applyIn(file: Path, bundleSha: String, m: BundleManifest, loaded: BundleReader.Loaded, opts: Options,
      originals: Originals, resolutions: MergeResolutions, merge: MergeConfig, planHash: String,
      backup: () => Either[String, String])(using DbCon): MergeApplyResult =
    val _ = sql"SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('histmersion.bundle-import'))) l".query[Int].run()
    val (input, planned) = MergeState.plan(file, m, loaded, resolutions, merge, originals, opts.chunkChars)
    val plan = planned.plan
    def named(i: MergeIssue): String =
      s"${i.id} ${i.entity.wire} ${i.right.orElse(i.left).fold("")(_.label)} — ${i.title}"
    val noText = plan.issues.filter(i => i.condition.isEmpty && i.decision.exists(_.kind == DecisionKind.File) &&
      !resolutions.resolve.exists(r => r.issue.trim == i.id && r.text.isDefined))
    val problems = plan.problems ++
      Option.when(plan.planHash != planHash.trim)(
        s"the plan is ${plan.planHash}, not $planHash: this collection, the bundle or the resolutions changed since " +
          "it was planned — plan again, review it, and apply that plan").toList ++
      plan.open.map(i => s"${named(i)}: open — decide it (`merge plan --write-template`)") ++
      plan.resolutionErrors.map(e => s"resolution $e") ++
      noText.map(i => s"${i.id}: ${i.decision.get.wire} — the merged text was not sent (the CLI reads the file)")
    val counts = BundleKind.values.toList.map(k => BundlePlan.counts(k, planned.rows.filter(_.kind == k).map(_.decision)))
      .filter(c => c.inserted + c.updated + c.unchanged + c.keptLocal + c.conflicts + c.deleted + c.skippedDeleted + c.held > 0)
    val base = MergeApplyResult(m.bundleId, m.instance, applied = false, plan.planHash, counts = counts,
      tally = planned.apply.tally, problems = problems, issues = plan.open, notes = planned.notes)
    if problems.nonEmpty then base
    else if plan.changes == 0 then base.copy(applied = true, replanChanges = Some(0), tally = MergeTally())
    else
      backup() match
        case Left(e) => base.copy(problems = List(s"the backup before the merge failed, nothing was written: $e"))
        case Right(dump) =>
          writeAll(file, input, planned, opts, originals)
          val ap = planned.apply
          // what the merge leaves behind: redirects, dropped records, its own changes' clocks
          ap.tombstones.foreach { (u, k, origin) =>
            sql"""INSERT INTO tombstones (uid, kind, origin) VALUES ($u, ${k.wire}, $origin)
                  ON CONFLICT (uid) DO NOTHING""".update.run()
          }
          val folded = planned.rows.filter(r => r.decision == Decision.FastForward && ap.touch(r.uid))
          folded.groupBy(_.kind).foreach { (k, rs) =>
            Sql.of(s"UPDATE ${tableOf(k)} SET updated_at = now() WHERE uid = ANY(?::uuid[])",
              Param.A(rs.map(_.uid.toString).distinct)).frag.update.run()
          }
          import io.circe.syntax.*
          val report = base.copy(applied = true, backup = Some(dump), problems = Nil)
          val mergeId = ledgerAndMerge(m, bundleSha, input.ownBundle, planned.rows, report.asJson, report.changed + plan.changes,
            Some((plan.planHash, dump, resolutions.asJson.noSpaces))).getOrElse(throw Abort(List("the merge wrote nothing")))
          ap.folds.foreach { f =>
            sql"""INSERT INTO uid_aliases (alias_uid, uid, kind, merge_id) VALUES (${f.foreignUid}, ${f.localUid}, ${f.kind.wire}, $mergeId)
                  ON CONFLICT (alias_uid) DO UPDATE SET uid = EXCLUDED.uid, kind = EXCLUDED.kind, merge_id = EXCLUDED.merge_id"""
              .update.run()
          }
          ap.remember.foreach { (id, d) =>
            sql"""INSERT INTO merge_decisions (issue_id, decision, merge_id) VALUES ($id, ${d.wire}, $mergeId)
                  ON CONFLICT (issue_id) DO UPDATE SET decision = EXCLUDED.decision, merge_id = EXCLUDED.merge_id,
                    decided_at = now()""".update.run()
          }
          val from = input.fromLabel
          val short = mergeId.toString.take(8)
          val reviews = ap.reviews.map { slug =>
            QueueRepo.insert(Queue.Kind.Tasks, Queue.QueueAdd(s"/review-article $slug --fix — merged with $from's (merge $short)",
              topic = Some("merge"), reason = Some(s"`merge apply` changed the article; a merged text is not reviewed")))
            slug
          }
          val t = ap.tally
          val log = QueueRepo.insert(Queue.Kind.Log, Queue.QueueAdd(
            s"Merged the bundle of $from (${m.bundleId.toString.take(8)}…) as merge $short: " +
              s"${Plural.count(t.sourcesMerged, "source")} merged, ${counts.find(_.kind == BundleKind.Source).fold(0)(_.inserted)} new, " +
              s"${Plural.count(reviews.size, "article")} to review; backup $dump",
            topic = Some("merge")))
          // the same bundle, planned again over what was just written: nothing should be left to do
          val (_, again) = MergeState.plan(file, m, loaded, resolutions, merge, originals, opts.chunkChars)
          report.copy(mergeId = Some(mergeId), reviews = reviews, logEntry = Some(log.id),
            replanChanges = Some(again.plan.changes))

  private def importing(on: Boolean)(using DbCon): Unit =
    val _ = sql"SELECT set_config('histmersion.importing', ${if on then "on" else "off"}, true)".query[String].run()

  /** sha256 → its uses, for the blobs a WRITTEN row needs (an unchanged row's blob is skipped). */
  private def neededBlobs(es: BundleEntities, writes: UUID => Boolean): Map[String, List[BlobUse]] =
    es.blobRefs.view.mapValues(_.filter {
      case BlobUse.Body(u)          => writes(u)
      case BlobUse.Original(u, _)   => writes(u)
      case BlobUse.VersionBody(u)   => writes(u)
      case BlobUse.AssetBytes(u)    => writes(u)
    }).filter(_._2.nonEmpty).toMap

  // ── the ledger ──────────────────────────────────────────────────────────────────────────────

  /** Record what was merged — only what changed in the ledger, and a `merges` row only when the
    * import changed anything at all, so importing the same bundle twice writes nothing. The ledger is
    * keyed by the BUNDLE's uid and names the local one it maps to. */
  private def ledgerAndMerge(m: BundleManifest, bundleSha: String, ownBundle: Boolean, decided: List[Decided],
      report: io.circe.Json, changed: Int, applied: Option[(String, String, String)] = None)(using DbCon): Option[UUID] =
    val existing = sql"""SELECT foreign_uid, foreign_rev, local_rev_after FROM merge_ledger
                         WHERE foreign_uid = ANY(${decided.map(_.foreignUid.toString)}::uuid[])"""
      .query[(UUID, String, Option[String])].run().map((u, f, l) => u -> Ledger(f, l)).toMap
    val rows = if ownBundle then Nil else decided.flatMap { d =>
      BundlePlan.ledgerAfter(d.decision, d.foreignRev, d.localizedRev, d.localRev, d.tombstone)
        .filterNot(l => existing.get(d.foreignUid).contains(l)).map(d -> _)
    }
    if rows.isEmpty && changed == 0 then None
    else
      val (hash, backup, res) = (applied.map(_._1), applied.map(_._2), applied.map(_._3))
      val id = sql"""INSERT INTO merges (bundle_id, bundle_sha256, from_instance, from_label, report, plan_hash, backup,
                       resolutions)
                     VALUES (${m.bundleId}, $bundleSha, ${m.instance.id}, ${m.instance.label},
                             ${report.noSpaces}::jsonb, $hash, $backup, $res::jsonb)
                     RETURNING id""".query[UUID].run().head
      rows.foreach { (d, l) =>
        sql"""INSERT INTO merge_ledger (foreign_uid, kind, merge_id, foreign_rev, local_uid, local_rev_after, decision)
              VALUES (${d.foreignUid}, ${d.kind.wire}, $id, ${l.foreignRev}, ${d.uid}, ${l.localRevAfter},
                      ${d.decision.toString})
              ON CONFLICT (foreign_uid) DO UPDATE SET kind = EXCLUDED.kind, merge_id = EXCLUDED.merge_id,
                foreign_rev = EXCLUDED.foreign_rev, local_uid = EXCLUDED.local_uid,
                local_rev_after = EXCLUDED.local_rev_after, decision = EXCLUDED.decision,
                recorded_at = now()""".update.run()
      }
      Some(id)

  // ── the writes ──────────────────────────────────────────────────────────────────────────────

  private def rNull(p: Option[BundleRange]): Boolean = p.isEmpty
  private def rLo(p: Option[BundleRange]): Option[Long] = p.flatMap(_.start)
  private def rHi(p: Option[BundleRange]): Option[Long] = p.flatMap(_.end)

  /** Applies the decided lines. One instance per import; it remembers what the later steps need. */
  private final class Writer(es: BundleEntities, decisionOf: Map[UUID, Decision], writes: UUID => Boolean,
      local: BundleEntities, skipAliases: Set[String], apply: MergePlanner.Apply)(using DbCon):

    private def decision(u: UUID): Option[Decision] = decisionOf.get(u)
    private def inserts(u: UUID): Boolean = decision(u).contains(Decision.Insert)

    /** citekey / slug / id of a uid as it stands NOW (after the writes so far). */
    private def citekeyOf(u: UUID): String =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[String].run().head
    private def slugOf(u: UUID): String = sql"SELECT slug FROM articles WHERE uid = $u".query[String].run().head
    private def citekeyOpt(u: UUID): Option[String] =
      sql"SELECT citekey FROM sources WHERE uid = $u".query[String].run().headOption
    private def eventIdOf(u: UUID): Option[String] = sql"SELECT id FROM events WHERE uid = $u".query[String].run().headOption
    private def goalSlugOf(u: UUID): Option[String] = sql"SELECT slug FROM goals WHERE uid = $u".query[String].run().headOption

    /** The label a target has HERE, when its uid names a row here; else the bundle's label. */
    private def targetLabel(tt: String, tid: String, tuid: Option[UUID]): String =
      tuid.flatMap { u =>
        val q = tt match
          case "finding" => Some(sql"SELECT id FROM findings WHERE uid = $u")
          case "source"  => Some(sql"SELECT citekey FROM sources WHERE uid = $u")
          case "article" => Some(sql"SELECT slug FROM articles WHERE uid = $u")
          case "event"   => Some(sql"SELECT id FROM events WHERE uid = $u")
          case _         => None
        q.flatMap(_.query[String].run().headOption)
      }.getOrElse(tid)

    /** A place the other side renamed whose line is not written here (this collection's version was
      * kept) keeps its id: the bundle's events and child places that name the new id point at it. */
    private val placeHere: String => String =
      val ours = local.places.map(p => p.uid -> p.id).toMap
      val kept = es.places.filterNot(p => writes(p.uid)).flatMap(p => ours.get(p.uid).filter(_ != p.id).map(p.id -> _)).toMap
      id => kept.getOrElse(id, id)

    // what the derived step and pass 2 need
    private val copied        = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val bodyChanged   = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val sourcesTouched = scala.collection.mutable.LinkedHashSet.empty[UUID]
    private val insertedSources = scala.collection.mutable.LinkedHashSet.empty[UUID]

    /** Tombstones first: a row the other side deleted makes room for one it created in its place. */
    def deletes(ds: List[Decided], tombs: Map[UUID, BundleTombstone]): Unit =
      ds.foreach { d =>
        val t = tombs(d.uid)
        if d.decision == Decision.Delete then
          val table = tableOf(t.kind)
          Sql.of(s"DELETE FROM $table WHERE uid = ?::uuid", Param.S(t.uid.toString)).frag.update.run()
        sql"""INSERT INTO tombstones (uid, kind, origin, deleted_at)
              VALUES (${t.uid}, ${t.kind.wire}, ${t.origin}, ${t.deletedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET deleted_at = EXCLUDED.deleted_at""".update.run()
      }

    def sources(opts: Options): Unit =
      // `rename-left`: a source here gives its citekey up first, to the bundle's work that has it; the
      // old citekey does not stay an alias (it is the other work's now)
      apply.renames.foreach { (_, from, to) =>
        importing(false)
        SourceRepo.renameIn(from, to).left.foreach(e => throw Abort(List(s"source $from → $to: ${e.message}")))
        sql"DELETE FROM source_aliases WHERE alias = $from".update.run()
        importing(true)
      }
      val localCk = local.sources.map(s => s.uid -> s.citekey).toMap ++
        apply.renames.map((u, _, to) => u -> to)
      es.sources.filter(s => writes(s.uid)).foreach { s =>
        // renamed over there: the whole rename here first (findings, links, aliases follow)
        localCk.get(s.uid).filter(_ != s.citekey).foreach { old =>
          importing(false)
          SourceRepo.renameIn(old, s.citekey).left.foreach(e => throw Abort(List(s"source $old → ${s.citekey}: ${e.message}")))
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
                  CASE WHEN ${rNull(s.period)} THEN NULL ELSE int8range(${rLo(s.period)}::bigint, ${rHi(s.period)}::bigint + 1, '[)') END,
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
                  period = CASE WHEN ${rNull(s.period)} THEN NULL
                                ELSE int8range(${rLo(s.period)}::bigint, ${rHi(s.period)}::bigint + 1, '[)') END,
                  summary = ${s.summary}, notes = ${s.notes}, source = ${s.provenance},
                  cited_by_count = ${s.citedByCount}, influential_citations = ${s.influentialCitations},
                  added_date = ${s.addedDate}::date, sha256 = ${s.originalSha256},
                  updated_at = ${s.updatedAt}::timestamptz
                WHERE uid = ${s.uid}""".update.run()
        sourcesTouched += s.uid
        // the ISBNs (doi and sha256 are derived from the row by V032's trigger)
        sql"DELETE FROM source_identifiers WHERE source_uid = ${s.uid} AND scheme = 'isbn'".update.run()
        s.isbns.getOrElse(Nil).foreach(i =>
          sql"""INSERT INTO source_identifiers (source_uid, scheme, value) VALUES (${s.uid}, 'isbn', $i)
                ON CONFLICT DO NOTHING""".update.run())
      }
      // a source's whole edge set travels with it
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
      es.unresolvedEdges.filter(e => writes(e.srcUid)).foreach { e =>
        val _ = SourceRepo.insertEdges(List(Edge(citekeyOf(e.srcUid), e.dst, e.kind)))
      }
      es.aliases.filterNot(a => skipAliases(a.alias)).foreach { a =>
        // never an alias equal to the source's own citekey (a key renamed back here, #827)
        val n = sql"""INSERT INTO source_aliases (alias, citekey, created_at)
              SELECT ${a.alias}, citekey, ${a.createdAt}::timestamptz FROM sources
              WHERE uid = ${a.sourceUid} AND citekey <> ${a.alias}
              ON CONFLICT (alias) DO NOTHING""".update.run()
        if n > 0 then sql"DELETE FROM alias_tombstones WHERE alias = ${a.alias}".update.run(): Unit
      }
      es.findings.filter(f => writes(f.uid)).foreach { f =>
        sourcesTouched += f.sourceUid
        val ck = citekeyOf(f.sourceUid)
        sql"""INSERT INTO findings (uid, origin, id, citekey, claim, region_name, geom, period, period_name,
                tags, confidence, significance, updated_at)
              VALUES (${f.uid}, ${f.origin}, ${f.id}, $ck, ${f.claim}, ${f.region},
                CASE WHEN ${f.lon}::float8 IS NULL OR ${f.lat}::float8 IS NULL THEN NULL
                     ELSE ST_SetSRID(ST_MakePoint(${f.lon}::float8, ${f.lat}::float8), 4326) END,
                CASE WHEN ${rNull(f.period)} THEN NULL ELSE int8range(${rLo(f.period)}::bigint, ${rHi(f.period)}::bigint + 1, '[)') END,
                ${f.periodName}, ${f.tags}::text[], ${f.confidence}, ${f.significance}, ${f.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, id = EXCLUDED.id,
                citekey = EXCLUDED.citekey, claim = EXCLUDED.claim, region_name = EXCLUDED.region_name,
                geom = EXCLUDED.geom, period = EXCLUDED.period, period_name = EXCLUDED.period_name,
                tags = EXCLUDED.tags, confidence = EXCLUDED.confidence, significance = EXCLUDED.significance,
                updated_at = EXCLUDED.updated_at""".update.run()
      }
      // the newer verdict wins (§4); a source that is not here (deleted, skipped) has none
      es.assessments.foreach { a =>
        sql"""INSERT INTO assessments (citekey, checked_at, retracted, notices, cited_by, influential, verdict)
              SELECT citekey, ${a.checkedAt}::timestamptz, ${a.retracted}, ${BundleFormat.printer.print(a.notices)}::jsonb,
                     ${a.citedBy}, ${a.influential}, ${a.verdict}
              FROM sources WHERE uid = ${a.sourceUid}
              ON CONFLICT (citekey) DO UPDATE SET checked_at = EXCLUDED.checked_at,
                retracted = EXCLUDED.retracted, notices = EXCLUDED.notices, cited_by = EXCLUDED.cited_by,
                influential = EXCLUDED.influential, verdict = EXCLUDED.verdict
              WHERE assessments.checked_at < EXCLUDED.checked_at""".update.run()
      }

    /** Rename what the other side renamed (the same uid, a new label), through the ordinary rename
      * path, before the row itself is written (an upsert keeps the label it finds). */
    private def followRenames[A](lines: List[A], uid: A => UUID, label: A => String, localLabel: Map[UUID, String],
        what: String)(rename: (String, String) => Either[ApiError, ?]): Unit =
      lines.filter(a => writes(uid(a))).foreach { a =>
        localLabel.get(uid(a)).filter(_ != label(a)).foreach { old =>
          importing(false)
          rename(old, label(a)).left.foreach(e => throw Abort(List(s"$what $old → ${label(a)}: ${e.message}")))
          importing(true)
        }
      }

    /** Places and named periods of the other collection's own (V052): before the events that point
      * at them. Their sets (names, parents, links, types) are replaced; a parent that is neither here
      * nor in the bundle is left out. */
    private def placesAndPeriods(): Unit =
      def rangeSql(p: Option[BundleRange]) = (p.flatMap(_.start), p.flatMap(_.end))
      val places = es.places.filter(p => writes(p.uid))
      places.foreach { p =>
        val (lo, hi) = rangeSql(p.period)
        val props = p.properties.map(BundleFormat.printer.print)
        sql"""INSERT INTO places (uid, origin, id, label, kind, geom, period, source, properties, dataset, updated_at)
              VALUES (${p.uid}, ${p.origin}, ${p.id}, ${p.label}, ${p.kind}, ST_GeomFromEWKB(decode(${p.geom}, 'hex')),
                int8range($lo::bigint, $hi::bigint + 1, '[)'), ${p.source}, $props::jsonb, 'local',
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
          val (lo, hi) = rangeSql(n.period)
          sql"""INSERT INTO place_names (place_id, name, lang, period)
                VALUES ($id, ${n.name}, ${n.lang.getOrElse("")},
                  CASE WHEN ${n.period.isEmpty} THEN NULL ELSE int8range($lo::bigint, $hi::bigint + 1, '[)') END)
                ON CONFLICT DO NOTHING""".update.run()
        }
        p.relations.foreach { r =>
          val (lo, hi) = rangeSql(r.period)
          sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
                SELECT $id, ${placeHere(r.parentId)}, ${r.kind}, int8range($lo::bigint, $hi::bigint + 1, '[)')
                WHERE EXISTS (SELECT 1 FROM places WHERE id = ${placeHere(r.parentId)})
                ON CONFLICT DO NOTHING""".update.run()
        }
        p.links.foreach { l =>
          sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
                VALUES ($id, ${l.scheme}, ${l.externalId}, ${l.relation}) ON CONFLICT DO NOTHING""".update.run()
        }
        p.types.foreach { t =>
          val (lo, hi) = rangeSql(t.period)
          sql"""INSERT INTO place_types (place_id, type_label, period, citekey)
                VALUES ($id, ${t.typeLabel}, int8range($lo::bigint, $hi::bigint + 1, '[)'), ${t.citekey})
                ON CONFLICT DO NOTHING""".update.run()
        }
      }
      val periods = es.periods.filter(p => writes(p.uid))
      periods.foreach { p =>
        val (lo, hi) = rangeSql(p.period)
        sql"""INSERT INTO periods (uid, origin, id, label, authority, period, region, dataset, updated_at)
              VALUES (${p.uid}, ${p.origin}, ${p.id}, ${p.label}, ${p.authority},
                int8range($lo::bigint, $hi::bigint + 1, '[)'), ${p.region}, 'local', ${p.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, label = EXCLUDED.label,
                authority = EXCLUDED.authority, period = EXCLUDED.period, region = EXCLUDED.region,
                dataset = 'local', updated_at = EXCLUDED.updated_at""".update.run()
      }
      periods.foreach { p =>
        sql"""UPDATE periods SET parent_id = (SELECT id FROM periods WHERE id = ${p.parentId}), updated_at = ${p.updatedAt}::timestamptz
              WHERE uid = ${p.uid}""".update.run()
      }

    /** A goal's links (unioned with this collection's) and schedules (this collection's kept, #881).
      * A linked record is found by its uid, so the link follows a rename or a merge; a queue item that
      * is not here is left out. A schedule brought in has no next run until it is saved here: an
      * imported collection never starts agent runs on its own. */
    private def goalSets(g: BundleGoal): Unit =
      val slug = sql"SELECT slug FROM goals WHERE uid = ${g.uid}".query[String].run().head
      g.links.getOrElse(Nil).foreach { l =>
        val ref: Option[String] = l.kind match
          case "source"     => l.refUid.flatMap(citekeyOpt).orElse(Some(l.ref))
          case "article"    => l.refUid.flatMap(u => sql"SELECT slug FROM articles WHERE uid = $u".query[String].run().headOption)
                                 .orElse(Some(l.ref))
          case "queue-item" => l.refUid.flatMap(u => sql"SELECT id::text FROM queue_items WHERE uid = $u".query[String].run().headOption)
          case _            => Some(l.ref)
        ref.foreach { r =>
          sql"""INSERT INTO goal_links (goal_slug, kind, ref, note, created_at)
                VALUES ($slug, ${l.kind}, $r, ${l.note}, ${l.createdAt}::timestamptz) ON CONFLICT DO NOTHING""".update.run()
        }
      }
      g.schedules.getOrElse(Nil).foreach { x =>
        val usd = x.budgetUsd.map(BigDecimal(_))
        sql"""INSERT INTO goal_schedules (goal_slug, kind, cron, enabled, budget_usd, last_note)
              VALUES ($slug, ${x.kind}, ${x.cron}, ${x.enabled}, $usd,
                'brought in by a merge: save it once here to schedule it')
              ON CONFLICT DO NOTHING""".update.run()
      }

    /** Articles, events, goals, the queue, and the asset metadata that needs no new bytes. */
    def rest(): Unit =
      // a place renamed over there (#888): the rename here first — its names, parents, footprints,
      // events and finding assignments follow by key, and the old id stays an alias — then the row
      followRenames(es.places, _.uid, _.id, local.places.map(p => p.uid -> p.id).toMap, "place")(PlaceRepo.renameIn)
      placesAndPeriods()
      // old place ids (V055), as old slugs: never one that is a place id here, never one another place has
      es.placeAliases.foreach { a =>
        sql"""INSERT INTO place_aliases (alias, place_id, created_at)
              SELECT ${a.alias}, id, ${a.createdAt}::timestamptz FROM places
              WHERE uid = ${a.placeUid} AND id <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM places WHERE id = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }
      followRenames(es.articles, _.uid, _.slug, local.articles.map(a => a.uid -> a.slug).toMap, "article")(ArticleRepo.renameIn)
      followRenames(es.events, _.uid, _.id, local.events.map(e => e.uid -> e.id).toMap, "event")(EventRepo.renameIn)
      followRenames(es.goals, _.uid, _.slug, local.goals.map(g => g.uid -> g.slug).toMap, "goal")(GoalRepo.renameIn)
      // the text a merge replaces is kept as a version first
      apply.snapshots.toList.filter(writes).foreach { u =>
        sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
              SELECT slug, body, summary, updated_at FROM articles WHERE uid = $u""".update.run()
      }
      es.articles.filter(a => writes(a.uid)).foreach { a =>
        sql"""INSERT INTO articles (uid, origin, slug, title, kind, body, summary, tags, regions, period, updated_at)
              VALUES (${a.uid}, ${a.origin}, ${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
                ${a.tags}::text[], ${a.regions}::text[],
                CASE WHEN ${rNull(a.period)} THEN NULL ELSE int8range(${rLo(a.period)}::bigint, ${rHi(a.period)}::bigint + 1, '[)') END,
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
                CASE WHEN ${rNull(a.period)} THEN NULL ELSE int8range(${rLo(a.period)}::bigint, ${rHi(a.period)}::bigint + 1, '[)') END)
              ON CONFLICT (uid) DO NOTHING""".update.run()
        copied += a.uid
      }
      // old slugs, as old citekeys: never one that is a slug here, never one another article has
      es.articleAliases.foreach { a =>
        sql"""INSERT INTO article_aliases (alias, slug, created_at)
              SELECT ${a.alias}, slug, ${a.createdAt}::timestamptz FROM articles
              WHERE uid = ${a.articleUid} AND slug <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }
      apply.versions.foreach { v =>
        sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
              SELECT slug, ${v.body}, ${v.summary}, ${v.versionOf}::timestamptz FROM articles WHERE uid = ${v.articleUid}"""
          .update.run()
      }
      // events: the rows, then their parents (a parent may come later in uid order), then the sets
      val events = es.events.filter(e => writes(e.uid))
      events.foreach { e =>
        val names = e.names.getOrElse(Nil)
        sql"""INSERT INTO events (uid, origin, id, label, kind, not_before, begin_by, end_from, not_after,
                historicity, geom, place_id, description, citekey, updated_at, scale,
                catalogue, names, wikidata, central, plus_minus, dating_method)
              VALUES (${e.uid}, ${e.origin}, ${e.id}, ${e.label}, ${e.kind}, ${e.notBefore}, ${e.beginBy},
                ${e.endFrom}, ${e.notAfter}, ${e.historicity}, ST_GeomFromEWKB(decode(${e.geom}, 'hex')),
                ${e.placeId.map(placeHere)}, ${e.description}, ${e.citekey}, ${e.updatedAt}::timestamptz,
                ${e.scale.flatMap(com.histmersion.core.EventScale.parse(_).flatten)},
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
      // old event ids (V053): after every event is in
      es.eventAliases.foreach { a =>
        sql"""INSERT INTO event_aliases (alias, event_id, created_at)
              SELECT ${a.alias}, id, ${a.createdAt}::timestamptz FROM events
              WHERE uid = ${a.eventUid} AND id <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM events WHERE id = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }
      val goals = es.goals.filter(g => writes(g.uid))
      goals.foreach { g =>
        val plan = g.plan.map(BundleFormat.printer.print)
        val usd  = g.budgetUsd.map(BigDecimal(_))
        sql"""INSERT INTO goals (uid, origin, slug, title, status, notes, tags, regions, period, created_at, updated_at,
                plan, plan_approved_at, budget_usd)
              VALUES (${g.uid}, ${g.origin}, ${g.slug}, ${g.title}, ${g.status}, ${g.notes}, ${g.tags}::text[],
                ${g.regions}::text[],
                CASE WHEN ${rNull(g.period)} THEN NULL ELSE int8range(${rLo(g.period)}::bigint, ${rHi(g.period)}::bigint + 1, '[)') END,
                ${g.createdAt}::timestamptz, ${g.updatedAt}::timestamptz, $plan::jsonb, ${g.planApprovedAt}::timestamptz, $usd)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, title = EXCLUDED.title,
                status = EXCLUDED.status, notes = EXCLUDED.notes, tags = EXCLUDED.tags,
                regions = EXCLUDED.regions, period = EXCLUDED.period, created_at = EXCLUDED.created_at,
                updated_at = EXCLUDED.updated_at, plan = EXCLUDED.plan, plan_approved_at = EXCLUDED.plan_approved_at,
                budget_usd = EXCLUDED.budget_usd""".update.run()
      }
      goals.foreach { g =>
        val parent = g.parentUid.flatMap(goalSlugOf)
        sql"UPDATE goals SET parent_slug = $parent, updated_at = ${g.updatedAt}::timestamptz WHERE uid = ${g.uid}"
          .update.run()
      }
      // after every goal is in (a link can name another goal's record, a schedule its own goal)
      goals.foreach(goalSets)
      es.goalAliases.foreach { a =>
        sql"""INSERT INTO goal_aliases (alias, goal_slug, created_at)
              SELECT ${a.alias}, slug, ${a.createdAt}::timestamptz FROM goals
              WHERE uid = ${a.goalUid} AND slug <> ${a.alias}
                AND NOT EXISTS (SELECT 1 FROM goals WHERE slug = ${a.alias})
              ON CONFLICT (alias) DO NOTHING""".update.run()
      }
      es.queue.filter(q => writes(q.uid)).foreach { q =>
        sql"""INSERT INTO queue_items (uid, origin, list, text, topic, status, reason, created_at, done_at, updated_at)
              VALUES (${q.uid}, ${q.origin}, ${q.list}, ${q.text}, ${q.topic}, ${q.status}, ${q.reason},
                ${q.createdAt}::timestamptz, ${q.doneAt}::timestamptz, ${q.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, list = EXCLUDED.list,
                text = EXCLUDED.text, topic = EXCLUDED.topic, status = EXCLUDED.status,
                reason = EXCLUDED.reason, created_at = EXCLUDED.created_at, done_at = EXCLUDED.done_at,
                updated_at = EXCLUDED.updated_at""".update.run()
      }
      // asset metadata whose bytes are already here (same sha): no blob needed
      val localSha = local.assets.map(a => a.uid -> a.sha256).toMap
      es.assets.filter(a => writes(a.uid) && localSha.get(a.uid).contains(a.sha256)).foreach(writeAsset(_, None))

    /** The event-catalogue sets (#1647), after every event, source and place is in: each line whose
      * event (and source) is here, merged by its own rule ([[BundleEventSets]]) — the newer catalogue
      * record, the newer candidate evidence with a decision kept over a proposal, the newer check,
      * correction and site type. Like an assessment, independent of the event line's decision: these
      * are what the importers and the person found, not the event's content. Idempotent: a line the
      * collection already has, or has newer, writes nothing. */
    def eventSets(): Unit =
      val ids = scala.collection.mutable.Map.empty[UUID, Option[String]]
      def eventId(u: UUID): Option[String] = ids.getOrElseUpdate(u, eventIdOf(u))
      import BundleEventSets.newer

      if es.eventCatalogue.nonEmpty then
        val here = Sql.raw(s"SELECT provider, provider_id, ${BundleRepo.ts("retrieved_at")} FROM event_catalogue").frag
          .query[(String, String, String)].run().map((p, i, at) => (p, i) -> at).toMap
        es.eventCatalogue.foreach { c =>
          eventId(c.eventUid).filter(_ => newer(here.get((c.provider, c.providerId)), Some(c.retrievedAt))).foreach { id =>
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

      if es.eventCandidates.nonEmpty then
        val here = Sql.raw(s"SELECT event_id, citekey, status, ${BundleRepo.ts("computed_at")} FROM event_link_candidates")
          .frag.query[(String, String, String, String)].run().map((e, c, st, at) => (e, c) -> (st, at)).toMap
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
                  sql"UPDATE event_link_candidates SET status = $keep WHERE event_id = $id AND citekey = $ck".update.run(): Unit
        }

      if es.eventChecks.nonEmpty then
        val here = Sql.raw(s"SELECT event_id, ${BundleRepo.ts("checked_at")} FROM event_link_checks").frag
          .query[(String, String)].run().toMap
        es.eventChecks.foreach { k =>
          eventId(k.eventUid).filter(id => newer(here.get(id), Some(k.checkedAt))).foreach { id =>
            sql"""INSERT INTO event_link_checks (event_id, checked_at) VALUES ($id, ${k.checkedAt}::timestamptz)
                  ON CONFLICT (event_id) DO UPDATE SET checked_at = EXCLUDED.checked_at""".update.run()
          }
        }

      if es.corrections.nonEmpty then
        val clock = s"greatest(${BundleRepo.ts("updated_at")}, coalesce(${BundleRepo.ts("checked_at")}, ''))"
        val here = Sql.raw(s"SELECT target_kind, target_id, field, $clock FROM corrections").frag
          .query[(String, String, String, String)].run().map((k, t, f, at) => (k, t, f) -> at).toMap
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

      if es.siteTypes.nonEmpty then
        val here = Sql.raw(s"SELECT id, ${BundleRepo.ts("site_type_checked_at")} FROM places WHERE site_type_checked_at IS NOT NULL")
          .frag.query[(String, String)].run().toMap
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
                  CASE WHEN ${rNull(a.period)} THEN NULL ELSE int8range(${rLo(a.period)}::bigint, ${rHi(a.period)}::bigint + 1, '[)') END,
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
                  period = CASE WHEN ${rNull(a.period)} THEN NULL
                                ELSE int8range(${rLo(a.period)}::bigint, ${rHi(a.period)}::bigint + 1, '[)') END,
                  updated_at = ${a.updatedAt}::timestamptz
                WHERE uid = ${a.uid}""".update.run()

    // ── pass 2 ──
    /** Stream the bundle's blobs and apply the ones `needed` names; every failure is returned. */
    def blobs(in: InputStream, needed: Map[String, List[BlobUse]], originals: Originals): List[String] =
      val tar      = BundleTar.Reader(in)
      val failures = List.newBuilder[String]
      val assetsBy = es.assets.map(a => a.uid -> a).toMap
      val versionsBy = es.versions.map(v => v.uid -> v).toMap
      @annotation.tailrec
      def loop(): Unit =
        tar.next() match
          case Left(e)     => failures += e
          case Right(None) => ()
          case Right(Some(e)) =>
            Bundle.blobSha(e.name).flatMap(sha => needed.get(sha).map(sha -> _)) match
              case None => ()
              case Some((sha, uses)) =>
                val (inDb, files) = uses.partition {
                  case _: BlobUse.Original => false
                  case _                   => true
                }
                if inDb.isEmpty then
                  files.foreach { case BlobUse.Original(_, ext) =>
                    originals.store(sha, ext, tar.stream).left.foreach(m => failures += s"original $sha: $m")
                  case _ => () }
                else
                  // in the database, so held in memory: an OCR body or an asset (limits.asset-bytes)
                  val bytes  = tar.readAll()
                  val actual = BundleFormat.sha256Hex(bytes)
                  if actual != sha then failures += s"blob $sha: its content hashes to $actual"
                  else
                    inDb.foreach {
                      case BlobUse.Body(u) =>
                        sql"UPDATE sources SET ocr_markdown = ${String(bytes, UTF_8)} WHERE uid = $u".update.run()
                      case BlobUse.VersionBody(u) =>
                        val v = versionsBy(u)
                        sql"""INSERT INTO article_versions (uid, origin, slug, body, summary, updated_at, created_at)
                              VALUES (${v.uid}, ${v.origin}, ${slugOf(v.articleUid)}, ${String(bytes, UTF_8)},
                                ${v.summary}, ${v.versionOf}::timestamptz, ${v.updatedAt}::timestamptz)
                              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, slug = EXCLUDED.slug,
                                body = EXCLUDED.body, summary = EXCLUDED.summary,
                                updated_at = EXCLUDED.updated_at, created_at = EXCLUDED.created_at""".update.run()
                      case BlobUse.AssetBytes(u) => writeAsset(assetsBy(u), Some(bytes))
                      case BlobUse.Original(_, _) => ()
                    }
                    files.foreach { case BlobUse.Original(_, ext) =>
                      originals.store(sha, ext, java.io.ByteArrayInputStream(bytes))
                        .left.foreach(m => failures += s"original $sha: $m")
                    case _ => () }
            loop()
      loop()
      failures.result()

    /** What hangs off the assets and the sources: asset links and attestations; and what a merge
      * changes in a body it took from the bundle (figures renamed) and in this collection's own
      * attestations (moved into that body, or dropped). */
    def afterBlobs(): Unit =
      apply.bodyRenames.foreach { (u, renames) =>
        renames.foreach { (from, to) =>
          sql"""UPDATE sources SET ocr_markdown = replace(ocr_markdown, ${"assets/" + from}, ${"assets/" + to})
                WHERE uid = $u""".update.run()
        }
        bodyChanged += u
      }
      if apply.attestations.nonEmpty then
        importing(false) // this collection's own rows: their clocks move and a drop leaves a tombstone
        apply.attestations.foreach { f =>
          if f.drop then sql"DELETE FROM attestations WHERE uid = ${f.uid}".update.run()
          else
            sql"""UPDATE attestations SET char_start = ${f.anchor.map(_.start)}, char_end = ${f.anchor.map(_.end)},
                    chunk_ord = ${f.chunkOrd} WHERE uid = ${f.uid}""".update.run()
        }
        importing(true)
      linksAndAttestations()

    private def linksAndAttestations(): Unit =
      es.assetLinks.filter(l => writes(l.uid)).foreach { l =>
        val (ck, name) = sql"""SELECT citekey, name FROM assets WHERE uid = ${l.assetUid}""".query[(String, String)].run().head
        val target = targetLabel(l.targetType, l.targetId, l.targetUid)
        sql"""INSERT INTO asset_links (uid, origin, citekey, asset_name, relation, target_type, target_id, xywh, notes, updated_at)
              VALUES (${l.uid}, ${l.origin}, $ck, $name, ${l.relation}, ${l.targetType}, $target, ${l.xywh},
                ${l.notes}, ${l.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, citekey = EXCLUDED.citekey,
                asset_name = EXCLUDED.asset_name, relation = EXCLUDED.relation,
                target_type = EXCLUDED.target_type, target_id = EXCLUDED.target_id, xywh = EXCLUDED.xywh,
                notes = EXCLUDED.notes, updated_at = EXCLUDED.updated_at""".update.run()
      }
      es.attestations.filter(t => writes(t.uid)).foreach { t =>
        val ck     = citekeyOf(t.sourceUid)
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

    /** The derived state, recomputed for what changed (with the import flag OFF: these are local
      * consequences, not bundle content). */
    def derived(opts: Options): Unit =
      bodyChanged.foreach { u =>
        val (ck, md) = sql"SELECT citekey, ocr_markdown FROM sources WHERE uid = $u".query[(String, Option[String])].run().head
        val _ = SourceRepo.replaceChunks(ck, md, opts.chunkChars)
      }
      sourcesTouched.foreach { u => val _ = PlaceRepo.materialiseFor(citekeyOf(u), opts.toleranceKm) }
      insertedSources.foreach(u => SourceRepo.promote(citekeyOf(u)))
      // a new source can turn an `[[x]]` that named nothing into a source link: relink every article
      // then; otherwise only the articles written
      val relink =
        if insertedSources.nonEmpty || apply.renames.nonEmpty then
          sql"SELECT slug, body FROM articles ORDER BY slug".query[(String, String)].run().toList
        else
          (es.articles.filter(a => writes(a.uid)).map(_.uid) ++ copied).flatMap(u =>
            sql"SELECT slug, body FROM articles WHERE uid = $u".query[(String, String)].run().headOption)
      relink.foreach((slug, body) => ArticleRepo.writeLinks(slug, body))

  private def tableOf(k: BundleKind): String = k match
    case BundleKind.Source         => "sources"
    case BundleKind.Finding        => "findings"
    case BundleKind.Article        => "articles"
    case BundleKind.ArticleVersion => "article_versions"
    case BundleKind.Asset          => "assets"
    case BundleKind.AssetLink      => "asset_links"
    case BundleKind.Attestation    => "attestations"
    case BundleKind.Event          => "events"
    case BundleKind.Goal           => "goals"
    case BundleKind.QueueItem      => "queue_items"
    case BundleKind.Place          => "places"
    case BundleKind.Period         => "periods"
