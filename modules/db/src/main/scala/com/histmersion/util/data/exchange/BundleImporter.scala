package com.histmersion.util.data.exchange

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.{Bundle, MergeApplyResult, MergeIssue, MergeResolutions, MergeTally}
import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.Jsonb
import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.exchange.BundlePlan.Decision
import com.histmersion.util.storage.{ByteSource, ContentStore}

import com.augustnagro.magnum.*
import io.circe.syntax.*
import java.io.InputStream
import java.util.UUID
import javax.sql.DataSource

/** `bundle import` and `merge apply` over the registry's carried kinds (docs/research-merge-import.md §6–§7): ONE
  * transaction, holding the import lock, in two passes over the bundle:
  *
  *   1. read the manifest and the entity files ([[BundleReader]]); plan every line ([[MergePlans]]); check — and report
  *      ALL of — what would make the import wrong. Any of those, or `dryRun`, and nothing is written. Otherwise the
  *      kinds' rows are written, stage by stage and rank by rank, with the bundle's own uids, origins and timestamps;
  *   2. stream the blobs, each checked against its sha256 as it is consumed, into the kind that owns its use; then what
  *      hangs off them, and the derived state.
  *
  * Rows written from the bundle keep its timestamps: the change-clock triggers stand aside while
  * `histmersion.importing` is on. The derived steps run with it off, so a LOCAL row the import changes moves and is
  * exported next time like any other change. Then the ledger and the `merges` row ([[MergeRepo.record]]).
  */
object BundleImporter:

  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunBundleSha256 = RunBundleSha256.T
  object RunBundleSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailedProblems = FailedProblems.T
  object FailedProblems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamedResult = NamedResult.T
  object NamedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportInBundleSha = ImportInBundleSha.T
  object ImportInBundleSha extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeededBlobsResultPart1 = NeededBlobsResultPart1.T
  object NeededBlobsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeededBlobsWritesResult = NeededBlobsWritesResult.T
  object NeededBlobsWritesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlobsResult = BlobsResult.T
  object BlobsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlobsNeededPart1 = BlobsNeededPart1.T
  object BlobsNeededPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyResultPart1 = ApplyResultPart1.T
  object ApplyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyBundleSha256 = ApplyBundleSha256.T
  object ApplyBundleSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPlanHash = ApplyPlanHash.T
  object ApplyPlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyBackupResultPart1 = ApplyBackupResultPart1.T
  object ApplyBackupResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyBackupResultPart2 = ApplyBackupResultPart2.T
  object ApplyBackupResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyInBundleSha = ApplyInBundleSha.T
  object ApplyInBundleSha extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyInPlanHash = ApplyInPlanHash.T
  object ApplyInPlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyInBackupResultPart1 = ApplyInBackupResultPart1.T
  object ApplyInBackupResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyInBackupResultPart2 = ApplyInBackupResultPart2.T
  object ApplyInBackupResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def run(
      ds: DataSource,
      bundle: ByteSource,
      bundleSha256: com.histmersion.util.data.exchange.BundleImporter.RunBundleSha256,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  ): Either[com.histmersion.util.data.exchange.BundleImporter.RunResultPart1, Bundle.ImportResult] =
    val loaded = bundle.read(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None    => Left(("not a readable bundle" :: loaded.problems).mkString("; "))
      case Some(m) =>
        try
          Right(
            transact(ds)(
              importIn(bundle, bundleSha256, m, loaded, opts, files, resolutions, merge, kinds, rules)
            )
          )
        catch case a: ImportAbort => Right(failed(m, opts, a.problems))

  private def failed(m: Bundle.Manifest, opts: ImportOptions, problems: List[com.histmersion.util.data.exchange.BundleImporter.FailedProblems]): Bundle.ImportResult =
    Bundle.ImportResult(m.bundleId, m.instance, opts.dryRun, applied = false, Nil, 0, problems, Nil, Nil)

  /** The issue as a problem line names it. */
  private def named(i: MergeIssue): com.histmersion.util.data.exchange.BundleImporter.NamedResult =
    s"${i.id} ${i.entity.wire} ${i.right.orElse(i.left).fold("")(_.label)} — ${i.title}"

  /** What the decided rows do, per kind of record, for the kinds they touch. */
  private def counted(decided: List[MergePlanner.Row]): List[Bundle.KindCounts] =
    com.histmersion.api.exchange.Bundle.Kind.values.toList
      .map(k => BundlePlan.counts(k, decided.filter(_.kind == k).map(_.decision)))
      .filter(c =>
        c.inserted + c.updated + c.unchanged + c.keptLocal + c.conflicts + c.deleted + c.skippedDeleted + c.held > 0
      )

  // ── pass 1: plan, validate, write the rows ───────────────────────────────────────────────────

  /** The merge plan (M3) decides every line: the same record here (by uid, a redirect or the ledger) as M2 did, and
    * everything else as the matcher and the resolutions say. The import carries out what M2 can — inserts,
    * fast-forwards, deletions, a conflict decided `keep-left`/`keep-right`, a record left out (`keep-left` on a taken
    * label), two works kept apart (`both`) — and refuses, with the plan's issues, while any issue is open or a decision
    * needs `merge apply` (M5).
    */
  private def importIn(
      bundle: ByteSource,
      bundleSha: com.histmersion.util.data.exchange.BundleImporter.ImportInBundleSha,
      m: Bundle.Manifest,
      loaded: BundleReader.Loaded,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  )(using DbCon): Bundle.ImportResult =
    MergeRepo.lockImports()
    val (input, planned) = MergePlans.plan(bundle, m, loaded, resolutions, merge, files, opts.chunkChars, kinds, rules)
    val plan = planned.plan
    val es = planned.localized
    val ownBundle = input.ownBundle
    val decided = planned.rows
    val decisionOf: Map[UUID, Decision] = decided.map(d => d.uid -> d.decision).toMap
    def writes(uid: UUID): Boolean = decisionOf.get(uid).exists(d => d == Decision.Insert || d == Decision.FastForward)

    // ── what holds it up: the plan's problems, its open issues, decisions only merge apply can do ──
    val holding = plan.issues.filter(_.open)
    val problems = plan.problems ++
      holding.map(i => s"${named(i)}: open — decide it (`merge plan --write-template`)") ++
      plan.resolutionErrors.map(e => s"resolution $e") ++ planned.needsApply
    val conflictIssues =
      plan.issues.filter(i =>
        i.kind == MergeIssue.Kind.ChangedBothSides || i.kind == MergeIssue.Kind.DeletedThereChangedHere
      )
    val conflicts = conflictIssues.map(i => named(i) + i.decision.fold(" — open")(d => s" — decided ${d.wire}"))
    val counts = counted(decided)
    val needed = neededBlobs(es, writes)
    val shown =
      holding ++ plan.issues.filter(i => i.decision.isDefined && planned.needsApply.exists(_.startsWith(i.id)))
    def result(applied: Boolean, mergeId: Option[UUID]) =
      Bundle.ImportResult(
        m.bundleId,
        m.instance,
        opts.dryRun,
        applied,
        counts,
        if applied then needed.size else 0,
        problems,
        conflicts,
        planned.notes,
        mergeId,
        shown,
        Some(plan.planHash)
      )

    if problems.nonEmpty || opts.dryRun then result(applied = false, None)
    else
      writeAll(bundle, input, planned, opts, files, kinds)
      val mergeId = MergeRepo.record(
        m,
        bundleSha,
        ownBundle,
        decided,
        result(applied = true, None).asJson,
        result(applied = true, None).changed
      )
      result(applied = true, mergeId)

  /** Every kind's steps over the decided lines: the rows, the blobs, what hangs off them and the derived state. Inside
    * the caller's transaction; the importing flag is left on.
    */
  private def writeAll(
      bundle: ByteSource,
      input: MergePlanner.Input,
      planned: MergePlanner.Result,
      opts: ImportOptions,
      files: ContentStore,
      kinds: CarriedKinds
  )(using DbCon): Unit =
    val es = planned.localized
    val decided = planned.rows
    val decisionOf: Map[UUID, Decision] = decided.map(d => d.uid -> d.decision).toMap
    def writes(uid: UUID): Boolean = decisionOf.get(uid).exists(d => d == Decision.Insert || d == Decision.FastForward)
    val needed = neededBlobs(es, writes)
    MergeRepo.importing(true)
    val w =
      BundleImport.Writer(es, decided, decisionOf, writes, input.local, planned.skipAliases, planned.apply, opts, kinds)
    kinds.steps(ImportStage.Rows).foreach(_.write(w))
    // ── pass 2: the blobs ──
    if needed.nonEmpty then
      val failures = bundle.read(in => blobs(in, needed, w, files, kinds))
      if failures.nonEmpty then throw ImportAbort(failures)
    kinds.steps(ImportStage.AfterBlobs).foreach(_.write(w))
    MergeRepo.importing(false)
    kinds.steps(ImportStage.Derived).foreach(_.write(w))
    MergeRepo.importing(true)

  /** sha256 → its uses, for the blobs a WRITTEN row needs (an unchanged row's blob is skipped). */
  private def neededBlobs(es: BundleEntities, writes: UUID => com.histmersion.util.data.exchange.BundleImporter.NeededBlobsWritesResult): Map[com.histmersion.util.data.exchange.BundleImporter.NeededBlobsResultPart1, List[BlobUse]] =
    es.blobRefs.view
      .mapValues(_.filter(u => writes(u.record)))
      .filter(_._2.nonEmpty)
      .toMap

  /** Stream the bundle's blobs and hand the ones `needed` names to the kinds that own their uses; every failure is
    * returned. A blob any use keeps in the database is held in memory (an OCR body or an asset, bounded by
    * `limits.asset-bytes`) and checked first; one only file stores take is streamed into them.
    */
  private def blobs(
      in: InputStream,
      needed: Map[com.histmersion.util.data.exchange.BundleImporter.BlobsNeededPart1, List[BlobUse]],
      w: BundleImport.Writer,
      files: ContentStore,
      kinds: CarriedKinds
  ): List[com.histmersion.util.data.exchange.BundleImporter.BlobsResult] =
    val tar = BundleTar.Reader(in)
    val failures = List.newBuilder[String]
    @annotation.tailrec
    def loop(): Unit =
      tar.next() match
        case Left(e)        => failures += e
        case Right(None)    => ()
        case Right(Some(e)) =>
          Bundle.blobSha(e.name).flatMap(sha => needed.get(sha).map(sha -> _)) match
            case None              => ()
            case Some((sha, uses)) =>
              val sinks = uses.flatMap(kinds.sink(_, sha, w, files))
              val held = sinks.collect { case BlobSink.Held(write) => write }
              val streamed = sinks.collect { case BlobSink.Streamed(store) => store }
              if held.isEmpty then streamed.foreach(store => store(tar.stream).foreach(failures += _))
              else
                val bytes = tar.readAll()
                val actual = BundleFormat.sha256Hex(bytes)
                if actual != sha then failures += s"blob $sha: its content hashes to $actual"
                else
                  held.foreach(_(bytes))
                  streamed.foreach(store => store(java.io.ByteArrayInputStream(bytes)).foreach(failures += _))
          loop()
    loop()
    failures.result()

  // ── merge apply (M5) ──────────────────────────────────────────────────────────────────────────

  /** `merge apply` (docs/research-merge-import.md §6.1, §6.4): carry out the plan of a bundle — the folds of matched
    * records included — in ONE transaction, after a backup.
    *
    * Refused (nothing written, no backup taken) while the plan has a problem, an open issue or an unusable resolution,
    * while a `file:` decision has no text, and when the plan's hash is not `planHash` — the collection, the bundle or
    * the resolutions changed since the plan the user reviewed. A plan with nothing to change is applied as a no-op: no
    * backup, no write. Otherwise `backup` runs (the server takes a `pg_dump`; a refusal of it refuses the apply), then
    * the writes; then the redirects (`uid_aliases`, one per folded record), the ledger (local uid = the survivor), the
    * `merges` row with the plan hash, the backup and the resolutions, the remembered decisions, a `/review-article`
    * task per merged article and a `log` entry. Last, the same bundle is planned again inside the transaction: what
    * that plan would still change is reported — 0 when the merge is idempotent.
    */
  def apply(
      ds: DataSource,
      bundle: ByteSource,
      bundleSha256: com.histmersion.util.data.exchange.BundleImporter.ApplyBundleSha256,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      planHash: com.histmersion.util.data.exchange.BundleImporter.ApplyPlanHash,
      backup: () => Either[com.histmersion.util.data.exchange.BundleImporter.ApplyBackupResultPart1, com.histmersion.util.data.exchange.BundleImporter.ApplyBackupResultPart2],
      kinds: CarriedKinds,
      rules: MergeRuleSet,
      followUps: MergeFollowUps
  ): Either[com.histmersion.util.data.exchange.BundleImporter.ApplyResultPart1, MergeApplyResult] =
    val loaded = bundle.read(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None    => Left(("not a readable bundle" :: loaded.problems).mkString("; "))
      case Some(m) =>
        try
          Right(
            transact(ds)(
              applyIn(
                bundle,
                bundleSha256,
                m,
                loaded,
                opts,
                files,
                resolutions,
                merge,
                planHash,
                backup,
                kinds,
                rules,
                followUps
              )
            )
          )
        catch
          case a: ImportAbort =>
            Right(MergeApplyResult(m.bundleId, m.instance, applied = false, planHash, problems = a.problems))

  private def applyIn(
      bundle: ByteSource,
      bundleSha: com.histmersion.util.data.exchange.BundleImporter.ApplyInBundleSha,
      m: Bundle.Manifest,
      loaded: BundleReader.Loaded,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      planHash: com.histmersion.util.data.exchange.BundleImporter.ApplyInPlanHash,
      backup: () => Either[com.histmersion.util.data.exchange.BundleImporter.ApplyInBackupResultPart1, com.histmersion.util.data.exchange.BundleImporter.ApplyInBackupResultPart2],
      kinds: CarriedKinds,
      rules: MergeRuleSet,
      followUps: MergeFollowUps
  )(using DbCon): MergeApplyResult =
    MergeRepo.lockImports()
    val (input, planned) = MergePlans.plan(bundle, m, loaded, resolutions, merge, files, opts.chunkChars, kinds, rules)
    val plan = planned.plan
    val noText = plan.issues.filter(i =>
      i.condition.isEmpty && i.decision.exists(_.kind == MergeDecision.Kind.File) &&
        !resolutions.resolve.exists(r => r.issue.trim == i.id && r.text.isDefined)
    )
    val problems = plan.problems ++
      Option
        .when(plan.planHash != planHash.trim)(
          s"the plan is ${plan.planHash}, not $planHash: this collection, the bundle or the resolutions changed since " +
            "it was planned — plan again, review it, and apply that plan"
        )
        .toList ++
      plan.open.map(i => s"${named(i)}: open — decide it (`merge plan --write-template`)") ++
      plan.resolutionErrors.map(e => s"resolution $e") ++
      noText.map(i => s"${i.id}: ${i.decision.get.wire} — the merged text was not sent (the CLI reads the file)")
    val counts = counted(planned.rows)
    val base = MergeApplyResult(
      m.bundleId,
      m.instance,
      applied = false,
      plan.planHash,
      counts = counts,
      tally = planned.apply.tally,
      problems = problems,
      issues = plan.open,
      notes = planned.notes
    )
    if problems.nonEmpty then base
    else if plan.changes == 0 then base.modify(_.applied).setTo(true).modify(_.replanChanges).setTo(Some(0)).modify(_.tally).setTo(MergeTally())
    else
      backup() match
        case Left(e) =>
          base.modify(_.problems).setTo(List(s"the backup before the merge failed, nothing was written: $e"))
        case Right(dump) =>
          writeAll(bundle, input, planned, opts, files, kinds)
          val ap = planned.apply
          // what the merge leaves behind: redirects, dropped records, its own changes' clocks
          MergeRepo.tombstone(ap.tombstones)
          val folded = planned.rows.filter(r => r.decision == Decision.FastForward && ap.touch(r.uid))
          folded.groupBy(_.kind).foreach((k, rs) => MergeRepo.touch(kinds.tableOf(k), rs.map(_.uid).distinct))
          val report = base.modify(_.applied).setTo(true).modify(_.backup).setTo(Some(dump)).modify(_.problems).setTo(Nil)
          val mergeId = MergeRepo
            .record(
              m,
              bundleSha,
              input.ownBundle,
              planned.rows,
              report.asJson,
              report.changed + plan.changes,
              Some((plan.planHash, dump, Jsonb.write(resolutions)))
            )
            .getOrElse(throw ImportAbort(List("the merge wrote nothing")))
          MergeRepo.redirect(ap.folds, mergeId)
          MergeRepo.remember(ap.remember, mergeId)
          val (reviews, logEntry) = followUps.recordIn(m, input.fromLabel, mergeId, ap, counts, dump)
          // the same bundle, planned again over what was just written: nothing should be left to do
          val (_, again) = MergePlans.plan(bundle, m, loaded, resolutions, merge, files, opts.chunkChars, kinds, rules)
          report.modify(
            _.mergeId).setTo(Some(mergeId)).modify(_.reviews).setTo(reviews).modify(_.logEntry).setTo(Some(logEntry)).modify(_.replanChanges).setTo(Some(again.plan.changes)
          )
