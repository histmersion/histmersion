package com.histmersion
package util.data.exchange
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.Write
import com.histmersion.api.exchange.{
  MergeApplyResult,
  MergeDecision,
  MergeDetail,
  MergeInfo,
  MergeLedger,
  MergeResolution,
  MergeResolutions,
  MergeSession
}
import com.histmersion.api.aliases.UidRedirect
import com.histmersion.api.given
import com.histmersion.util.database.{Jsonb, Timestamps}
import com.histmersion.util.database.given
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergePlanner.Fold
import com.histmersion.util.data.exchange.BundlePlan.Ledger

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import java.util.UUID
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.data.TableName
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** What a collection remembers of its merges (docs/research-merge-import.md §6.1, milestone M7):
  * `merge list | show | ledger` read `merges`, `merge_ledger`, `uid_aliases` and `merge_decisions`; the web review
  * screen's draft is `merge_sessions` (V051). Stateless, like every repo.
  */
@atBoundary(BoundaryKind.Sql, "reads and writes the merge job rows")
object MergeRepo:
  type ListLimit = ListLimit.T
  object ListLimit extends Newtype[Int, Pure]
  type BundleName = BundleName.T
  object BundleName extends Newtype[String, Pure]
  type DecisionKey = DecisionKey.T
  object DecisionKey extends Newtype[String, Pure]
  type Importing = Importing.T
  object Importing extends Newtype[Boolean, Pure]
  type MergeIdOrPrefix = MergeIdOrPrefix.T
  object MergeIdOrPrefix extends Newtype[String, Pure]
  type UidText = UidText.T
  object UidText extends Newtype[String, Pure]

  private val mergeCols =
    sql"""m.id, m.bundle_id, m.bundle_sha256, m.from_instance, m.from_label, ${Timestamps.iso(
        sql"m.applied_at"
      )}, m.plan_hash, m.backup,
        (SELECT count(*)::int FROM merge_ledger l WHERE l.merge_id = m.id),
        (SELECT count(*)::int FROM uid_aliases u WHERE u.merge_id = m.id)"""

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def info(r: MergeRow): MergeInfo =
    MergeInfo(
      r.id,
      r.bundleId,
      MergeInfo.BundleSha256(r.bundleSha256.value),
      Bundle.Instance(r.fromInstance, r.fromLabel.map(l => Bundle.Instance.Label(l.value))),
      MergeInfo.AppliedAt(r.appliedAt.value),
      r.planHash.map(h => MergeInfo.PlanHash(h.value)),
      r.backup.map(b => MergeInfo.Backup(b.value)),
      MergeInfo.LedgerRows(r.ledgerRows.value),
      MergeInfo.Redirects(r.redirects.value)
    )

  /** The merges, newest first. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def list(limit: PageSize)(using DbCon): List[MergeInfo] =
    sql"SELECT $mergeCols FROM merges m ORDER BY m.applied_at DESC, m.id DESC LIMIT ${math.max(1, limit.value)}"
      .query[MergeRow]
      .run()
      .toList
      .map(info)

  /** One merge by its id or an unambiguous prefix of it: `Left` when none, or several, match. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def find(idOrPrefix: MergeIdOrPrefix)(using DbCon): Either[Api.Error, MergeRow] =
    val p = idOrPrefix.value.trim.toLowerCase(java.util.Locale.ROOT)
    if p.isEmpty || !p.forall(c => c.isDigit || (c >= 'a' && c <= 'f') || c == '-') then
      Left(
        Api.Error(
          Problem.Invalid.Broken.here(Problem.Invalid.Rule.MergeId(Problem.Raw.of(idOrPrefix))),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      sql"SELECT $mergeCols FROM merges m WHERE m.id::text LIKE ${p + "%"} ORDER BY m.applied_at DESC, m.id DESC LIMIT 2"
        .query[MergeRow]
        .run()
        .toList match
        case List(r) => Right(r)
        case Nil     =>
          Left(
            Api.Error(
              Problem.NotFound.Listed(
                Problem.TypeName.of(MergeIdOrPrefix),
                Problem.Raw.of(idOrPrefix),
                Problem.NotFound.Listing.Merges
              ),
              Some(Api.Error.Code(Write.CodeNotFound))
            )
          )
        case _ =>
          Left(
            Api.Error(
              Problem.Unclassified(Legacy.Text(s"'$idOrPrefix' names more than one merge — give more of its id")),
              Some(Api.Error.Code(Write.CodeInvalid))
            )
          )

  /** One merge with its report (an apply's or an import's, as written), its resolutions and the decisions it left to
    * remember.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def detail(idOrPrefix: MergeIdOrPrefix)(using DbCon): Either[Api.Error, MergeDetail] =
    find(idOrPrefix).map { r =>
      val (report, resolutions) = sql"SELECT report::text, resolutions::text FROM merges WHERE id = ${r.id}"
        .query[(String, Option[String])]
        .run()
        .head
      val json = Jsonb.read[Json](Jsonb.JsonText(report))
      // an apply wrote a MergeApplyResult (it has `applied` and `tally`), an import a BundleImportResult
      val applied = json.filter(_.cursor.downField("tally").focus.isDefined).flatMap(_.as[MergeApplyResult].toOption)
      val imported = if applied.isDefined then None
      else json.flatMap(_.as[Bundle.ImportResult].toOption)
      val remembered = sql"SELECT issue_id, decision FROM merge_decisions WHERE merge_id = ${r.id} ORDER BY issue_id"
        .query[(String, String)]
        .run()
        .toList
        .map((i, d) => MergeResolution(MergeResolution.Issue(i), MergeResolution.Decision(d)))
      MergeDetail(
        info(r),
        applied,
        imported,
        resolutions.flatMap(t => Jsonb.read[MergeResolutions](Jsonb.JsonText(t))),
        remembered
      )
    }

  /** The ledger rows of one record (a uid — the bundle's or the one here — or a citekey), of one merge, or the latest
    * `limit`; and the redirects that name the record.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def ledger(uid: Option[UidText], merge: Option[MergeIdOrPrefix], limit: PageSize, rules: MergeRuleSet)(using
      DbCon
  ): Either[Api.Error, MergeLedger] =
    val label = rules.ledgerLabelSql
    val select = sql"""SELECT l.foreign_uid, l.kind, l.merge_id, l.foreign_rev, l.local_uid, $label, l.local_rev_after,
                            l.decision, ${Timestamps.iso(sql"l.recorded_at")} FROM merge_ledger l"""
    def asUid(s: String): Option[UUID] = scala.util.Try(UUID.fromString(s.trim)).toOption
    val target: Either[Api.Error, Option[UUID]] = uid.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(u) =>
        asUid(u)
          .orElse(sql"""SELECT uid FROM sources WHERE citekey = $u
                UNION ALL SELECT s.uid FROM source_aliases a JOIN sources s ON s.citekey = a.citekey WHERE a.alias = $u
                LIMIT 1""".query[UUID].run().headOption)
          .map(Some(_))
          .toRight(
            Api.Error(
              Problem.NotFound.Entity(Problem.TypeName.of(UidText), Problem.Raw(s"$u")),
              Some(Api.Error.Code(Write.CodeNotFound))
            )
          )
    for
      t <- target
      m <- merge.map(_.value.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(x) => find(MergeIdOrPrefix(x)).map(r => Some(r.id))
    yield
      val n = math.max(1, limit.value)
      val rows = (t, m) match
        case (Some(u), _) =>
          // the record's own row, and those of every bundle uid that redirects to it
          sql"""$select WHERE l.foreign_uid = ${u.toString}::uuid OR l.local_uid = ${u.toString}::uuid
                     OR l.foreign_uid IN (SELECT alias_uid FROM uid_aliases WHERE uid = ${u.toString}::uuid)
                     ORDER BY l.recorded_at DESC, l.foreign_uid LIMIT $n"""
        case (None, Some(mid)) =>
          sql"$select WHERE l.merge_id = ${mid.toString}::uuid ORDER BY l.kind, l.foreign_uid LIMIT $n"
        case _ =>
          sql"$select ORDER BY l.recorded_at DESC, l.foreign_uid LIMIT $n"
      val entries = rows
        .query[LedgerRow]
        .run()
        .toList
        .map(r =>
          com.histmersion.api.research.LedgerRow(
            r.foreignUid,
            com.histmersion.api.research.LedgerRow.Kind(r.kind.value),
            r.mergeId,
            com.histmersion.api.research.LedgerRow.ForeignRev(r.foreignRev.value),
            r.localUid,
            r.label.map(l => com.histmersion.api.research.LedgerRow.Label(l.value)),
            r.localRevAfter.map(l => com.histmersion.api.research.LedgerRow.LocalRevAfter(l.value)),
            com.histmersion.api.research.LedgerRow.Decision(r.decision.value),
            com.histmersion.api.research.LedgerRow.RecordedAt(r.recordedAt.value)
          )
        )
      val redirects = t.toList.flatMap { u =>
        sql"""SELECT alias_uid, uid, kind, merge_id FROM uid_aliases WHERE alias_uid = $u OR uid = $u
              ORDER BY alias_uid"""
          .query[(UUID, UUID, String, Option[UUID])]
          .run()
          .toList
          .map((a, b, k, mid) => UidRedirect(a, b, UidRedirect.Kind(k), mid))
      }
      MergeLedger(
        uid.map(u => MergeLedger.Query(u.value)).orElse(merge.map(m => MergeLedger.Query(m.value))),
        entries,
        redirects
      )

  // ── the review screen's draft (V051) ──────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def session(bundle: BundleName)(using DbCon): MergeSession =
    sql"SELECT resolutions::text, ${Timestamps.iso(sql"updated_at")} FROM merge_sessions WHERE bundle = $bundle"
      .query[(String, String)]
      .run()
      .headOption
      .flatMap((r, at) =>
        Jsonb
          .read[MergeResolutions](Jsonb.JsonText(r))
          .map(MergeSession(MergeSession.Bundle(bundle.value), _, Some(MergeSession.UpdatedAt(at))))
      )
      .getOrElse(MergeSession(MergeSession.Bundle(bundle.value)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def putSession(bundle: BundleName, r: MergeResolutions)(using DbCon): MergeSession =
    val json = Jsonb.write(r)
    sql"""INSERT INTO merge_sessions (bundle, resolutions, updated_at) VALUES ($bundle, $json::jsonb, now())
          ON CONFLICT (bundle) DO UPDATE SET resolutions = EXCLUDED.resolutions, updated_at = now()""".update.run()
    session(bundle)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def dropSession(bundle: BundleName)(using DbCon): Unit =
    Unused(sql"DELETE FROM merge_sessions WHERE bundle = $bundle".update.run(), Unused.Why.Idempotent)

  // ── what an import and a merge write (in the caller's transaction) ─────────────────────────────

  /** One import at a time per database: two would decide against the same state and both write. */
  def lockImports()(using DbCon): Unit =
    val _ = sql"SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('histmersion.bundle-import'))) l".query[Int].run()

  /** While on, rows keep the clocks the bundle gives them: the change-clock triggers stand aside. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def importing(on: Importing)(using DbCon): Unit =
    val setting: String = if on.value then "on" else "off"
    val _ = sql"SELECT set_config('histmersion.importing', $setting, true)".query[String].run()

  /** Record what was merged — only what changed in the ledger, and a `merges` row only when the import changed anything
    * at all, so importing the same bundle twice writes nothing. The ledger is keyed by the BUNDLE's uid and names the
    * local one it maps to.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def record(
      m: Bundle.Manifest,
      bundleSha: Sha256,
      ownBundle: BundlePlan.EntityOwnBundle,
      decided: List[MergePlanner.Row],
      report: Json,
      changed: RowCount,
      applied: Option[(MergeRow.AppliedAt, MergeRow.Backup, MergeRow.FromLabel)] = None
  )(using DbCon): Option[UUID] =
    val existing = sql"""SELECT foreign_uid, foreign_rev, local_rev_after FROM merge_ledger
                         WHERE foreign_uid = ANY(${decided.map(_.foreignUid.toString)}::uuid[])"""
      .query[(UUID, Ledger.ForeignRev, Option[Ledger.LocalRevAfter])]
      .run()
      .map((u, f, l) => u -> Ledger(f, l))
      .toMap
    val rows = if ownBundle.value then Nil
    else
      decided.flatMap { d =>
        BundlePlan
          .ledgerAfter(
            d.decision,
            d.foreignRev,
            d.localizedRev,
            d.localRev,
            BundlePlan.Local.Tombstoned(d.tombstone.value)
          )
          .filterNot(l => existing.get(d.foreignUid).contains(l))
          .map(d -> _)
      }
    if rows.isEmpty && changed.value == 0 then None
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

  /** The records a merge dropped, as tombstones. */
  def tombstone(dropped: List[(UUID, Bundle.Kind, UUID)])(using DbCon): Unit =
    dropped.foreach { (u, k, origin) =>
      Unused(
        sql"""INSERT INTO tombstones (uid, kind, origin) VALUES ($u, ${k.wire}, $origin)
            ON CONFLICT (uid) DO NOTHING""".update.run(),
        Unused.Why.Idempotent
      )
    }

  /** A merge's own change of rows it folded into: their clocks move. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  def touch(table: TableName, uids: List[UUID])(using DbCon): Unit =
    Unused(
      sql"UPDATE ${SqlLiteral(table.value)} SET updated_at = now() WHERE uid = ANY(${uids.map(u => u.toString)}::uuid[])".update
        .run(),
      Unused.Why.Idempotent
    )

  /** One redirect per folded record: the bundle's uid names the survivor here. */
  def redirect(folds: List[Fold], mergeId: UUID)(using DbCon): Unit =
    folds.foreach { f =>
      sql"""INSERT INTO uid_aliases (alias_uid, uid, kind, merge_id) VALUES (${f.foreignUid}, ${f.localUid}, ${f.kind.wire}, $mergeId)
            ON CONFLICT (alias_uid) DO UPDATE SET uid = EXCLUDED.uid, kind = EXCLUDED.kind, merge_id = EXCLUDED.merge_id""".update
        .run()
    }

  /** The decisions a merge leaves to remember, by issue id. */
  def remember(decisions: List[(DecisionKey, MergeDecision)], mergeId: UUID)(using DbCon): Unit =
    decisions.foreach { (id, d) =>
      sql"""INSERT INTO merge_decisions (issue_id, decision, merge_id) VALUES ($id, ${d.wire}, $mergeId)
            ON CONFLICT (issue_id) DO UPDATE SET decision = EXCLUDED.decision, merge_id = EXCLUDED.merge_id,
              decided_at = now()""".update.run()
    }
