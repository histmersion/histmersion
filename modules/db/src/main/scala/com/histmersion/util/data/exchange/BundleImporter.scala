package com.histmersion
package util.data.exchange
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.{Bundle, MergeApplyResult, MergeIssue, MergeResolutions, MergeTally}
import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.{Jsonb, Transactor, WritePermit}
import com.histmersion.domain.{CreatedAt, MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.exchange.BundlePlan.Decision
import com.histmersion.util.storage.{ByteSource, ContentStore}

import com.augustnagro.magnum.*
import java.io.InputStream
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

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
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object BundleImporter:
  type BackupFile = BackupFile.T
  object BackupFile extends Newtype[String, Pure]
  type IssueName = IssueName.T
  object IssueName extends Newtype[String, Pure]

  def run(
      ds: DataSource,
      bundle: ByteSource,
      bundleSha256: Sha256,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      kinds: CarriedKinds,
      rules: MergeRuleSet,
      clock: Clock
  )(using WritePermit): Either[Problems, Bundle.ImportResult] =
    val loaded = bundle.read(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None    => Left(loaded.unreadable)
      case Some(m) =>
        try
          Right(
            Transactor(ds).write:
              given CreatedAt = CreatedAt.now(clock)
              importIn(bundle, bundleSha256, m, loaded, opts, files, resolutions, merge, kinds, rules)
          )
        catch case a: ImportAbort => Right(failed(m, opts, a.problems))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def failed(
      m: Bundle.Manifest,
      opts: ImportOptions,
      problems: List[Problem]
  ): Bundle.ImportResult =
    Bundle.ImportResult(
      m.bundleId,
      m.instance,
      (if opts.dryRun.dryRun then Bundle.ImportResult.DryRun.DryRun else Bundle.ImportResult.DryRun.Apply),
      applied = Bundle.ImportResult.Applied.Skipped,
      Nil,
      Bundle.ImportResult.Blobs(0),
      problems,
      Nil,
      Nil
    )

  /** The issue as a problem line names it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def named(i: MergeIssue): IssueName =
    IssueName(s"${i.id} ${i.entity.wire} ${i.right.orElse(i.left).fold("")(_.label)} — ${i.title}")

  extension (i: MergeIssue)
    /** The issue as a problem that holds the import up until it is decided. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def holdingUp: Problem = Problem.Conflict.Held(Problem.Where(s"${named(i)}"), Problem.Conflict.Hold.Open)

    /** Does `problem` say that carrying out this issue's decision is `merge apply`'s? */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def carriedBy(problem: Problem): Boolean = problem match
      case Problem.Conflict.Held(subject, Problem.Conflict.Hold.Carries(_)) => s"$subject" == s"${i.id}"
      case _                                                                => false

  /** What the decided rows do, per kind of record, for the kinds they touch. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def counted(decided: List[MergePlanner.Row]): List[Bundle.KindCounts] =
    Bundle.Kind.values.toList
      .map(k => BundlePlan.counts(k, decided.filter(_.kind == k).map(_.decision)))
      .filter(c =>
        c.inserted.value + c.updated.value + c.unchanged.value + c.keptLocal.value + c.conflicts.value + c.deleted.value + c.skippedDeleted.value + c.held.value > 0
      )

  // ── pass 1: plan, validate, write the rows ───────────────────────────────────────────────────

  /** The merge plan (M3) decides every line: the same record here (by uid, a redirect or the ledger) as M2 did, and
    * everything else as the matcher and the resolutions say. The import carries out what M2 can — inserts,
    * fast-forwards, deletions, a conflict decided `keep-left`/`keep-right`, a record left out (`keep-left` on a taken
    * label), two works kept apart (`both`) — and refuses, with the plan's issues, while any issue is open or a decision
    * needs `merge apply` (M5).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def importIn(
      bundle: ByteSource,
      bundleSha: Sha256,
      m: Bundle.Manifest,
      loaded: BundleReader.Loaded,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  )(using DbCon, CreatedAt): Bundle.ImportResult =
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
    val problems: List[Problem] =
      plan.problems ++ holding.map(_.holdingUp) ++ plan.resolutionErrors ++ planned.needsApply
    val conflictIssues =
      plan.issues.filter(i =>
        i.kind == MergeIssue.Kind.ChangedBothSides || i.kind == MergeIssue.Kind.DeletedThereChangedHere
      )
    val conflicts = conflictIssues.map(i =>
      Bundle.ImportResult.Conflicts(named(i).value + i.decision.fold(" — open")(d => s" — decided ${d.wire}"))
    )
    val counts = counted(decided)
    val needed = neededBlobs(es, writes)
    val shown =
      holding ++ plan.issues.filter(i => i.decision.isDefined && planned.needsApply.exists(i.carriedBy))
    def result(applied: Bundle.ImportResult.Applied, mergeId: Option[UUID]) =
      Bundle.ImportResult(
        m.bundleId,
        m.instance,
        (if opts.dryRun.dryRun then Bundle.ImportResult.DryRun.DryRun else Bundle.ImportResult.DryRun.Apply),
        applied,
        counts,
        if applied.applied then Bundle.ImportResult.Blobs(needed.size) else Bundle.ImportResult.Blobs(0),
        problems,
        conflicts,
        planned.notes.map(n => Bundle.ImportResult.Notes(n.value)),
        mergeId,
        shown,
        Some(Bundle.ImportResult.PlanHash(plan.planHash.value))
      )

    if problems.nonEmpty || opts.dryRun.dryRun then result(applied = Bundle.ImportResult.Applied.Skipped, None)
    else
      writeAll(bundle, input, planned, opts, files, kinds)
      val mergeId = MergeRepo.record(
        m,
        bundleSha,
        (if ownBundle then BundlePlan.EntityOwnBundle.OwnBundle else BundlePlan.EntityOwnBundle.SharedBundle),
        decided,
        result(applied = Bundle.ImportResult.Applied.Applied, None).asJson,
        RowCount(result(applied = Bundle.ImportResult.Applied.Applied, None).changed.value)
      )
      result(applied = Bundle.ImportResult.Applied.Applied, mergeId)

  /** Every kind's steps over the decided lines: the rows, the blobs, what hangs off them and the derived state. Inside
    * the caller's transaction; the importing flag is left on.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def writeAll(
      bundle: ByteSource,
      input: MergePlanner.Input,
      planned: MergePlanner.Result,
      opts: ImportOptions,
      files: ContentStore,
      kinds: CarriedKinds
  )(using DbCon)(using at: CreatedAt): Unit =
    val es = planned.localized
    val decided = planned.rows
    val decisionOf: Map[UUID, Decision] = decided.map(d => d.uid -> d.decision).toMap
    def writes(uid: UUID): Boolean = decisionOf.get(uid).exists(d => d == Decision.Insert || d == Decision.FastForward)
    val needed = neededBlobs(es, writes)
    MergeRepo.importing(MergeRepo.Importing.Importing)
    val w =
      BundleImport.Writer(
        es,
        decided,
        decisionOf,
        writes,
        input.local,
        planned.skipAliases.map(c => Citekey.assume(c.value)),
        planned.apply,
        opts,
        kinds,
        at
      )
    kinds.steps(ImportStage.Rows).foreach(_.write(w))
    // ── pass 2: the blobs ──
    if needed.nonEmpty then
      val failures = bundle.read(in => blobs(in, needed, w, files, kinds))
      if failures.nonEmpty then throw ImportAbort(failures)
    kinds.steps(ImportStage.AfterBlobs).foreach(_.write(w))
    MergeRepo.importing(MergeRepo.Importing.Merging)
    kinds.steps(ImportStage.Derived).foreach(_.write(w))
    MergeRepo.importing(MergeRepo.Importing.Importing)

  /** sha256 → its uses, for the blobs a WRITTEN row needs (an unchanged row's blob is skipped). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def neededBlobs(es: BundleEntities, writes: UUID => Boolean): Map[Sha256, List[BlobUse]] =
    es.blobRefs.view
      .mapValues(_.filter(u => writes(u.record)))
      .filter(_._2.nonEmpty)
      .flatMap((sha, uses) => Sha256.option(sha.value).map(_ -> uses))
      .toMap

  /** Stream the bundle's blobs and hand the ones `needed` names to the kinds that own their uses; every failure is
    * returned. A blob any use keeps in the database is held in memory (an OCR body or an asset, bounded by
    * `limits.asset-bytes`) and checked first; one only file stores take is streamed into them.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def blobs(
      in: InputStream,
      needed: Map[Sha256, List[BlobUse]],
      w: BundleImport.Writer,
      files: ContentStore,
      kinds: CarriedKinds
  ): List[Problem] =
    val tar = BundleTar.Reader(in)
    val failures = List.newBuilder[Problem]
    @annotation.tailrec
    def loop(): Unit =
      tar.next() match
        case Left(e)        => failures += e
        case Right(None)    => ()
        case Right(Some(e)) =>
          Bundle
            .blobSha(e.name.value)
            .flatMap(a => Sha256.option(a.value))
            .flatMap(sha => needed.get(sha).map(sha -> _)) match
            case None              => ()
            case Some((sha, uses)) =>
              val sinks = uses.flatMap(kinds.sink(_, sha, w, files))
              val held = sinks.collect { case BlobSink.Held(write) => write }
              val streamed = sinks.collect { case BlobSink.Streamed(store) => store }
              if held.isEmpty then streamed.foreach(store => store(tar.stream).foreach(f => failures += f))
              else
                val bytes = tar.readAll()
                val actual = BundleFormat.sha256Hex(bytes)
                if actual.value != sha.value then
                  failures += Problem.Io.Entry(
                    Problem.Io.Format.Bundle,
                    Problem.Io.Path(s"${sha.value}"),
                    Problem.Io.Fault
                      .Differs(Problem.Io.Measure.Sha256, Problem.Raw(s"${sha.value}"), Problem.Raw(s"${actual.value}"))
                  )
                else
                  held.foreach(_(bytes))
                  streamed.foreach(store => store(java.io.ByteArrayInputStream(bytes)).foreach(f => failures += f))
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(
      ds: DataSource,
      bundle: ByteSource,
      bundleSha256: Sha256,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      planHash: MergeRow.PlanHash,
      backup: () => Either[Problems, String],
      kinds: CarriedKinds,
      rules: MergeRuleSet,
      followUps: MergeFollowUps,
      clock: Clock
  )(using WritePermit): Either[Problems, MergeApplyResult] =
    val loaded = bundle.read(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None    => Left(loaded.unreadable)
      case Some(m) =>
        try
          Right(
            Transactor(ds).write:
              given CreatedAt = CreatedAt.now(clock)
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
        catch
          case a: ImportAbort =>
            Right(
              MergeApplyResult(
                m.bundleId,
                m.instance,
                applied = MergeApplyResult.Applied.Skipped,
                MergeApplyResult.PlanHash(planHash.value),
                problems = a.problems
              )
            )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def applyIn(
      bundle: ByteSource,
      bundleSha: Sha256,
      m: Bundle.Manifest,
      loaded: BundleReader.Loaded,
      opts: ImportOptions,
      files: ContentStore,
      resolutions: MergeResolutions,
      merge: MergeSettings,
      planHash: MergeRow.PlanHash,
      backup: () => Either[Problems, String],
      kinds: CarriedKinds,
      rules: MergeRuleSet,
      followUps: MergeFollowUps
  )(using DbCon, CreatedAt): MergeApplyResult =
    MergeRepo.lockImports()
    val (input, planned) = MergePlans.plan(bundle, m, loaded, resolutions, merge, files, opts.chunkChars, kinds, rules)
    val plan = planned.plan
    val noText = plan.issues.filter(i =>
      i.condition.isEmpty && i.decision.exists(_.kind == MergeDecision.Kind.File) &&
        !resolutions.resolve.exists(r => r.issue.value.trim == i.id.value && r.text.isDefined)
    )
    val problems: List[Problem] = plan.problems ++
      Option
        .when(plan.planHash.value != planHash.value.trim)(
          Problem.Conflict.Held(
            Problem.Where(s"${plan.planHash}"),
            Problem.Conflict.Hold.PlanChanged(Problem.Raw(s"$planHash"))
          )
        )
        .toList ++
      plan.open.map(_.holdingUp) ++ plan.resolutionErrors ++
      noText.flatMap(i =>
        i.decision.map(d =>
          Problem.Conflict.Held(Problem.Where(s"${i.id}"), Problem.Conflict.Hold.TextNotSent(Problem.Raw(s"${d.wire}")))
        )
      )
    val counts = counted(planned.rows)
    val base = MergeApplyResult(
      m.bundleId,
      m.instance,
      applied = MergeApplyResult.Applied.Skipped,
      MergeApplyResult.PlanHash(plan.planHash.value),
      counts = counts,
      tally = planned.apply.tally,
      problems = problems,
      issues = plan.open,
      notes = planned.notes.map(n => MergeApplyResult.Notes(n.value))
    )
    if problems.nonEmpty then base
    else if plan.changes.value == 0 then
      base
        .modify(_.applied)
        .setTo(MergeApplyResult.Applied.Applied)
        .modify(_.replanChanges)
        .setTo(Some(MergeApplyResult.ReplanChanges(0)))
        .modify(_.tally)
        .setTo(MergeTally())
    else
      backup() match
        case Left(e) =>
          base
            .modify(_.problems)
            .setTo(e.toVector.toList)
        case Right(dump) =>
          writeAll(bundle, input, planned, opts, files, kinds)
          val ap = planned.apply
          // what the merge leaves behind: redirects, dropped records, its own changes' clocks
          MergeRepo.tombstone(ap.tombstones)
          val folded = planned.rows.filter(r => r.decision == Decision.FastForward && ap.touch(r.uid))
          folded.groupBy(_.kind).foreach((k, rs) => MergeRepo.touch(kinds.tableOf(k), rs.map(_.uid).distinct))
          val report = base
            .modify(_.applied)
            .setTo(MergeApplyResult.Applied.Applied)
            .modify(_.backup)
            .setTo(Some(MergeApplyResult.Backup(dump)))
            .modify(_.problems)
            .setTo(Nil)
          val mergeId = MergeRepo
            .record(
              m,
              bundleSha,
              (if input.ownBundle then BundlePlan.EntityOwnBundle.OwnBundle
               else BundlePlan.EntityOwnBundle.SharedBundle),
              planned.rows,
              report.asJson,
              RowCount(report.changed.value + plan.changes.value),
              Some(
                (
                  MergeRow.AppliedAt(plan.planHash.value),
                  MergeRow.Backup(dump),
                  MergeRow.FromLabel(Jsonb.write(resolutions).value)
                )
              )
            )
            .getOrElse(
              throw ImportAbort(
                List(Problem.Run.Stopped(Problem.Run.Stop.Unrecorded(Problem.Conflict.Activity.MergeApply)))
              )
            )
          MergeRepo.redirect(ap.folds, mergeId)
          MergeRepo.remember(ap.remember.map((id, d) => MergeRepo.DecisionKey(id.value) -> d), mergeId)
          val (reviews, logEntry) = followUps.recordIn(
            m,
            MergeFollowUps.FromRecordRef(input.fromLabel.value),
            mergeId,
            ap,
            counts,
            MergeFollowUps.DumpRecordRef(dump)
          )
          // the same bundle, planned again over what was just written: nothing should be left to do
          val (_, again) = MergePlans.plan(bundle, m, loaded, resolutions, merge, files, opts.chunkChars, kinds, rules)
          report
            .modify(_.mergeId)
            .setTo(Some(mergeId))
            .modify(_.reviews)
            .setTo(reviews.map(s => MergeApplyResult.Reviews(s.value)))
            .modify(_.logEntry)
            .setTo(Some(MergeApplyResult.LogEntry(logEntry.value)))
            .modify(_.replanChanges)
            .setTo(Some(MergeApplyResult.ReplanChanges(again.plan.changes.value)))
