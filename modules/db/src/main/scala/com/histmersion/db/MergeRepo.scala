package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.util.UUID
import javax.sql.DataSource

final private[db] case class MergeRow(
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
)
given DbCodec[MergeRow] = DbCodec.derived

final private[db] case class LedgerDbRow(
    foreignUid: UUID,
    kind: String,
    mergeId: UUID,
    foreignRev: String,
    localUid: UUID,
    label: Option[String],
    localRevAfter: Option[String],
    decision: String,
    recordedAt: String
)
given DbCodec[LedgerDbRow] = DbCodec.derived

/** What a collection remembers of its merges (docs/research-merge-import.md §6.1, milestone M7):
  * `merge list | show | ledger` read `merges`, `merge_ledger`, `uid_aliases` and `merge_decisions`; the web review
  * screen's draft is `merge_sessions` (V051). Stateless, like every repo.
  */
object MergeRepo:

  private val ts = BundleRepo.ts

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
  def ledger(ds: DataSource, uid: Option[String], merge: Option[String], limit: Int): Either[ApiError, MergeLedger] =
    connect(ds):
      val label =
        """CASE l.kind WHEN 'source' THEN (SELECT citekey FROM sources WHERE uid = l.local_uid)
                       WHEN 'finding' THEN (SELECT id FROM findings WHERE uid = l.local_uid)
                       WHEN 'article' THEN (SELECT slug FROM articles WHERE uid = l.local_uid)
                       WHEN 'asset' THEN (SELECT name FROM assets WHERE uid = l.local_uid)
                       WHEN 'event' THEN (SELECT id FROM events WHERE uid = l.local_uid)
                       WHEN 'goal' THEN (SELECT slug FROM goals WHERE uid = l.local_uid)
                       WHEN 'place' THEN (SELECT id FROM places WHERE uid = l.local_uid)
                       WHEN 'period' THEN (SELECT id FROM periods WHERE uid = l.local_uid)
                       WHEN 'queue-item' THEN (SELECT left(text, 60) FROM queue_items WHERE uid = l.local_uid) END"""
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
          .query[LedgerDbRow]
          .run()
          .toList
          .map(r =>
            LedgerRow(
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
