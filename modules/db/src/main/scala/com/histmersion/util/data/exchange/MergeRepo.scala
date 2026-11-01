package com.histmersion.util.data.exchange

import com.histmersion.api.{
  ApiError,
  BundleImportResult,
  BundleInstance,
  BundleManifest,
  MergeApplyResult,
  MergeDecision,
  MergeDetail,
  MergeInfo,
  MergeLedger,
  MergeResolution,
  MergeResolutions,
  MergeSession,
  UidRedirect,
  Write
}
import com.histmersion.api.given
import com.histmersion.db.{Jsonb, Param, Sql}
import com.histmersion.db.given
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergePlanner.Fold
import com.histmersion.util.data.exchange.BundlePlan.Ledger
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.*
import java.util.UUID
import javax.sql.DataSource

final private[exchange] case class MergeRow(
    id: UUID,
    bundleId: UUID,
    bundleSha256: String,
    fromInstance: UUID,
    fromLabel: Option[String],
    appliedAt: String,
    planHash: Option[String],
    backup: Option[String],
    ledgerRows: Int,
    redirects: Int
) derives DbCodec

final private[exchange] case class LedgerRow(
    foreignUid: UUID,
    kind: String,
    mergeId: UUID,
    foreignRev: String,
    localUid: UUID,
    label: Option[String],
    localRevAfter: Option[String],
    decision: String,
    recordedAt: String
) derives DbCodec

/** What a collection remembers of its merges (docs/research-merge-import.md §6.1, milestone M7):
  * `merge list | show | ledger` read `merges`, `merge_ledger`, `uid_aliases` and `merge_decisions`; the web review
  * screen's draft is `merge_sessions` (V051). Stateless, like every repo.
  */
object MergeRepo:

  private val ts = BundleSql.ts

  private val mergeCols =
    s"""m.id, m.bundle_id, m.bundle_sha256, m.from_instance, m.from_label, ${ts("m.applied_at")}, m.plan_hash, m.backup,
        (SELECT count(*)::int FROM merge_ledger l WHERE l.merge_id = m.id),
        (SELECT count(*)::int FROM uid_aliases u WHERE u.merge_id = m.id)"""

  private def info(r: MergeRow): MergeInfo =
    MergeInfo(
      r.id,
      r.bundleId,
      r.bundleSha256,
      BundleInstance(r.fromInstance, r.fromLabel),
      r.appliedAt,
      r.planHash,
      r.backup,
      r.ledgerRows,
      r.redirects
    )

  /** The merges, newest first. */
  def list(ds: DataSource, limit: Int): List[MergeInfo] =
    connect(ds):
      Sql
        .of(
          s"SELECT $mergeCols FROM merges m ORDER BY m.applied_at DESC, m.id DESC LIMIT ?",
          Param.I(math.max(1, limit))
        )
        .frag
        .query[MergeRow]
        .run()
        .toList
        .map(info)

  /** One merge by its id or an unambiguous prefix of it: `Left` when none, or several, match. */
  def find(idOrPrefix: String)(using DbCon): Either[ApiError, MergeRow] =
    val p = idOrPrefix.trim.toLowerCase(java.util.Locale.ROOT)
    if p.isEmpty || !p.forall(c => c.isDigit || (c >= 'a' && c <= 'f') || c == '-') then
      Left(
        ApiError(
          s"'$idOrPrefix' is not a merge id (the id `merge list` prints, or its first characters)",
          Some(Write.CodeInvalid)
        )
      )
    else
      Sql
        .of(
          s"SELECT $mergeCols FROM merges m WHERE m.id::text LIKE ? ORDER BY m.applied_at DESC LIMIT 2",
          Param.S(p + "%")
        )
        .frag
        .query[MergeRow]
        .run()
        .toList match
        case List(r) => Right(r)
        case Nil     =>
          Left(ApiError(s"no merge '$idOrPrefix' (`histmersion merge list` lists them)", Some(Write.CodeNotFound)))
        case _ =>
          Left(ApiError(s"'$idOrPrefix' names more than one merge — give more of its id", Some(Write.CodeInvalid)))

  /** One merge with its report (an apply's or an import's, as written), its resolutions and the decisions it left to
    * remember.
    */
  def detail(ds: DataSource, idOrPrefix: String): Either[ApiError, MergeDetail] =
    connect(ds):
      find(idOrPrefix).map { r =>
        val (report, resolutions) = sql"SELECT report::text, resolutions::text FROM merges WHERE id = ${r.id}"
          .query[(String, Option[String])]
          .run()
          .head
        val json = Jsonb.read[io.circe.Json](report)
        // an apply wrote a MergeApplyResult (it has `applied` and `tally`), an import a BundleImportResult
        val applied = json.filter(_.hcursor.downField("tally").succeeded).flatMap(_.as[MergeApplyResult].toOption)
        val imported = if applied.isDefined then None else json.flatMap(_.as[BundleImportResult].toOption)
        val remembered = sql"SELECT issue_id, decision FROM merge_decisions WHERE merge_id = ${r.id} ORDER BY issue_id"
          .query[(String, String)]
          .run()
          .toList
          .map((i, d) => MergeResolution(i, d))
        MergeDetail(info(r), applied, imported, resolutions.flatMap(Jsonb.read[MergeResolutions]), remembered)
      }

  /** The ledger rows of one record (a uid — the bundle's or the one here — or a citekey), of one merge, or the latest
    * `limit`; and the redirects that name the record.
    */
  def ledger(
      ds: DataSource,
      uid: Option[String],
      merge: Option[String],
      limit: Int,
      rules: MergeRuleSet
  ): Either[ApiError, MergeLedger] =
    connect(ds):
      val label = rules.ledgerLabelSql
      val select = s"""SELECT l.foreign_uid, l.kind, l.merge_id, l.foreign_rev, l.local_uid, $label, l.local_rev_after,
                              l.decision, ${ts("l.recorded_at")} FROM merge_ledger l"""
      def asUid(s: String): Option[UUID] = scala.util.Try(UUID.fromString(s.trim)).toOption
      val target: Either[ApiError, Option[UUID]] = uid.map(_.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(u) =>
          asUid(u)
            .orElse(sql"""SELECT uid FROM sources WHERE citekey = $u
                  UNION ALL SELECT s.uid FROM source_aliases a JOIN sources s ON s.citekey = a.citekey WHERE a.alias = $u
                  LIMIT 1""".query[UUID].run().headOption)
            .map(Some(_))
            .toRight(ApiError(s"'$u' is neither a uid nor a citekey here", Some(Write.CodeNotFound)))
      for
        t <- target
        m <- merge.map(_.trim).filter(_.nonEmpty) match
          case None    => Right(None)
          case Some(x) => find(x).map(r => Some(r.id))
      yield
        val n = math.max(1, limit)
        val rows = (t, m) match
          case (Some(u), _) =>
            // the record's own row, and those of every bundle uid that redirects to it
            Sql.of(
              s"""$select WHERE l.foreign_uid = ?::uuid OR l.local_uid = ?::uuid
                       OR l.foreign_uid IN (SELECT alias_uid FROM uid_aliases WHERE uid = ?::uuid)
                       ORDER BY l.recorded_at DESC, l.foreign_uid LIMIT ?""",
              Param.S(u.toString),
              Param.S(u.toString),
              Param.S(u.toString),
              Param.I(n)
            )
          case (None, Some(mid)) =>
            Sql.of(
              s"$select WHERE l.merge_id = ?::uuid ORDER BY l.kind, l.foreign_uid LIMIT ?",
              Param.S(mid.toString),
              Param.I(n)
            )
          case _ => Sql.of(s"$select ORDER BY l.recorded_at DESC, l.foreign_uid LIMIT ?", Param.I(n))
        val entries = rows.frag
          .query[LedgerRow]
          .run()
          .toList
          .map(r =>
            com.histmersion.api.LedgerRow(
              r.foreignUid,
              r.kind,
              r.mergeId,
              r.foreignRev,
              r.localUid,
              r.label,
              r.localRevAfter,
              r.decision,
              r.recordedAt
            )
          )
        val redirects = t.toList.flatMap { u =>
          sql"""SELECT alias_uid, uid, kind, merge_id FROM uid_aliases WHERE alias_uid = $u OR uid = $u
                ORDER BY alias_uid"""
            .query[(UUID, UUID, String, Option[UUID])]
            .run()
            .toList
            .map((a, b, k, mid) => UidRedirect(a, b, k, mid))
        }
        MergeLedger(uid.orElse(merge), entries, redirects)

  // ── the review screen's draft (V051) ──────────────────────────────────────────────────────────

  def session(ds: DataSource, bundle: String): MergeSession =
    connect(ds):
      Sql
        .of(s"SELECT resolutions::text, ${ts("updated_at")} FROM merge_sessions WHERE bundle = ?", Param.S(bundle))
        .frag
        .query[(String, String)]
        .run()
        .headOption
        .flatMap((r, at) => Jsonb.read[MergeResolutions](r).map(MergeSession(bundle, _, Some(at))))
        .getOrElse(MergeSession(bundle))

  def putSession(ds: DataSource, bundle: String, r: MergeResolutions): MergeSession =
    val json = Jsonb.write(r)
    transact(ds):
      sql"""INSERT INTO merge_sessions (bundle, resolutions, updated_at) VALUES ($bundle, $json::jsonb, now())
            ON CONFLICT (bundle) DO UPDATE SET resolutions = EXCLUDED.resolutions, updated_at = now()""".update.run()
    session(ds, bundle)

  def dropSession(ds: DataSource, bundle: String): Unit =
    val _ = transact(ds)(sql"DELETE FROM merge_sessions WHERE bundle = $bundle".update.run())

  // ── what an import and a merge write (in the caller's transaction) ─────────────────────────────

  /** One import at a time per database: two would decide against the same state and both write. */
  def lockImports()(using DbCon): Unit =
    val _ = sql"SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('histmersion.bundle-import'))) l".query[Int].run()

  /** While on, rows keep the clocks the bundle gives them: the change-clock triggers stand aside. */
  def importing(on: Boolean)(using DbCon): Unit =
    val _ = sql"SELECT set_config('histmersion.importing', ${if on then "on" else "off"}, true)".query[String].run()

  /** Record what was merged — only what changed in the ledger, and a `merges` row only when the import changed anything
    * at all, so importing the same bundle twice writes nothing. The ledger is keyed by the BUNDLE's uid and names the
    * local one it maps to.
    */
  def record(
      m: BundleManifest,
      bundleSha: String,
      ownBundle: Boolean,
      decided: List[MergePlanner.Row],
      report: io.circe.Json,
      changed: Int,
      applied: Option[(String, String, String)] = None
  )(using DbCon): Option[UUID] =
    val existing = sql"""SELECT foreign_uid, foreign_rev, local_rev_after FROM merge_ledger
                         WHERE foreign_uid = ANY(${decided.map(_.foreignUid.toString)}::uuid[])"""
      .query[(UUID, String, Option[String])]
      .run()
      .map((u, f, l) => u -> Ledger(f, l))
      .toMap
    val rows = if ownBundle then Nil
    else
      decided.flatMap { d =>
        BundlePlan
          .ledgerAfter(d.decision, d.foreignRev, d.localizedRev, d.localRev, d.tombstone)
          .filterNot(l => existing.get(d.foreignUid).contains(l))
          .map(d -> _)
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

  /** The records a merge dropped, as tombstones. */
  def tombstone(dropped: List[(UUID, BundleKind, UUID)])(using DbCon): Unit =
    dropped.foreach { (u, k, origin) =>
      sql"""INSERT INTO tombstones (uid, kind, origin) VALUES ($u, ${k.wire}, $origin)
            ON CONFLICT (uid) DO NOTHING""".update.run()
    }

  /** A merge's own change of rows it folded into: their clocks move. */
  def touch(table: com.histmersion.util.data.TableName, uids: List[UUID])(using DbCon): Unit =
    Sql
      .of(s"UPDATE ${table.value} SET updated_at = now() WHERE uid = ANY(?::uuid[])", Param.A(uids.map(_.toString)))
      .frag
      .update
      .run()

  /** One redirect per folded record: the bundle's uid names the survivor here. */
  def redirect(folds: List[Fold], mergeId: UUID)(using DbCon): Unit =
    folds.foreach { f =>
      sql"""INSERT INTO uid_aliases (alias_uid, uid, kind, merge_id) VALUES (${f.foreignUid}, ${f.localUid}, ${f.kind.wire}, $mergeId)
            ON CONFLICT (alias_uid) DO UPDATE SET uid = EXCLUDED.uid, kind = EXCLUDED.kind, merge_id = EXCLUDED.merge_id""".update
        .run()
    }

  /** The decisions a merge leaves to remember, by issue id. */
  def remember(decisions: List[(String, MergeDecision)], mergeId: UUID)(using DbCon): Unit =
    decisions.foreach { (id, d) =>
      sql"""INSERT INTO merge_decisions (issue_id, decision, merge_id) VALUES ($id, ${d.wire}, $mergeId)
            ON CONFLICT (issue_id) DO UPDATE SET decision = EXCLUDED.decision, merge_id = EXCLUDED.merge_id,
              decided_at = now()""".update.run()
    }
