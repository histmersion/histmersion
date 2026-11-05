package com.histmersion.util.data.exchange

import com.histmersion.api.Write
import com.histmersion.api.exchange.{
  Bundle,
  MergeApplyJob,
  MergeApplyRequest,
  MergeDetail,
  MergeImportRequest,
  MergeInfo,
  MergeLedger,
  MergePlan,
  MergeResolutions,
  MergeSession
}
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.storage.{ByteSource, HandedFile}

import java.time.OffsetDateTime
import com.histmersion.api.Api

/** The bundle and merge endpoints, MCP tools and `histmersion bundle|merge` commands (docs/research-merge-import.md):
  * the raw inputs parsed, the maintenance guard of every write, the [[BundleService]] call. An upload is the file the
  * HTTP layer streamed the body into.
  */
final class BundleControllers(bundles: BundleService, maintenance: WriteGate):

  def instance: InstanceInfo = bundles.instance

  /** Name this instance; a blank label clears it. */
  def setInstance(label: Option[com.histmersion.util.data.exchange.BundleControllers.SetInstanceLabel]): Either[Api.Error, InstanceInfo] =
    maintenance.guard("instance")(Right(bundles.setLabel(label)))

  /** `GET /api/bundle?since=` — the bundle file and its download name. */
  def exportBundle(since: Option[com.histmersion.util.data.exchange.BundleControllers.ExportBundleSince]): Either[Api.Error, (HandedFile, com.histmersion.util.data.exchange.BundleControllers.ExportBundleResultPart2Part2)] =
    BundleControllers.since(since).flatMap(bundles.exportBundle).map((f, m) => f -> BundleControllers.disposition(m))

  /** `POST /api/bundle/verify`. */
  def verify(upload: HandedFile): Either[Api.Error, Bundle.VerifyResult] = readable(upload).map(bundles.verify)

  /** `POST /api/bundle/import?dryRun=`. A write: refused while a restore holds the lock. Planned with no resolutions:
    * any open issue refuses it (decide them, then `POST /api/merge/import`).
    */
  def importBundle(dryRun: com.histmersion.util.data.exchange.BundleControllers.ImportBundleDryRun, upload: HandedFile): Either[Api.Error, Bundle.ImportResult] =
    maintenance.guard("bundle/import")(readable(upload).flatMap(bundles.importBundle(_, dryRun)))

  /** `POST /api/bundle/upload` — keep the bundle under its sha256 so it can be planned and imported by name. The
    * manifest is read back, and what reading the file found wrong is reported.
    */
  def upload(upload: HandedFile): Either[Api.Error, Bundle.Upload] = readable(upload).flatMap(bundles.keep)

  /** `POST /api/merge/plan` (and MCP `merge_plan`) — pure: reads, never writes. */
  def plan(req: MergePlan.Request): Either[Api.Error, MergePlan] =
    BundleControllers.sha256(req.bundle).flatMap(bundles.plan(_, req.resolutions))

  /** `POST /api/merge/import` — import an uploaded bundle with resolutions. A write. */
  def importUploaded(req: MergeImportRequest): Either[Api.Error, Bundle.ImportResult] =
    maintenance.guard("merge/import"):
      BundleControllers.sha256(req.bundle).flatMap(bundles.importKept(_, req.resolutions, req.dryRun))

  /** `POST /api/merge/apply` — carry out the reviewed plan of an uploaded bundle (M5). A write, and the one that takes
    * a backup first (`histmersion backup`'s `pg_dump`, into `backups.dir`). It runs as a background job
    * ([[MergeApplyJobs]]): the request waits up to `merge.apply-wait-seconds` and then answers where it is, so a large
    * collection's backup does not time the request out. A successful apply drops the review screen's draft of the
    * bundle (its decisions are remembered by issue id).
    */
  def applyUploaded(req: MergeApplyRequest): Either[Api.Error, MergeApplyJob] =
    maintenance.guard("merge/apply"):
      BundleControllers.sha256(req.bundle).flatMap(bundles.applyKept(_, req.planHash, req.resolutions))

  /** `GET /api/merge/apply/{job}` — the job is a `jobs` row, so it answers across a restart (#888). */
  def applyStatus(job: com.histmersion.util.data.exchange.BundleControllers.ApplyStatusJob): Either[Api.Error, MergeApplyJob] =
    bundles
      .applyStatus(job)
      .toRight(
        Api.Error(
          s"no merge apply '$job' on this server " +
            "(`histmersion merge list` shows what was merged)",
          Some(Write.CodeNotFound)
        )
      )

  /** `GET /api/merge/sessions/{bundle}`. */
  def session(bundle: com.histmersion.util.data.exchange.BundleControllers.SessionBundle): Either[Api.Error, MergeSession] = BundleControllers.sha256(bundle).map(bundles.session)

  /** `PUT /api/merge/sessions/{bundle}`. */
  def putSession(bundle: com.histmersion.util.data.exchange.BundleControllers.PutSessionBundle, r: MergeResolutions): Either[Api.Error, MergeSession] =
    maintenance.guard("merge/session"):
      BundleControllers.sha256(bundle).map(bundles.putSession(_, r))

  /** `POST /api/merge/resolutions` — the YAML of a resolution file, read; every bad line at once. */
  def parseResolutions(text: com.histmersion.util.data.exchange.BundleControllers.ParseResolutionsText): Either[Api.Error, MergeResolutions] =
    MergeResolutionsFile.parse(text).left.map(es => Api.Error(es.mkString("; "), Some(Write.CodeInvalid)))

  def merges(limit: Option[com.histmersion.util.data.exchange.BundleControllers.MergesLimit]): List[MergeInfo] =
    bundles.merges(limit.filter(_ > 0).getOrElse(BundleControllers.ListLimit))

  def mergeDetail(id: com.histmersion.util.data.exchange.BundleControllers.MergeDetailId): Either[Api.Error, MergeDetail] = bundles.mergeDetail(id)

  def mergeLedger(uid: Option[com.histmersion.util.data.exchange.BundleControllers.MergeLedgerUid], merge: Option[com.histmersion.util.data.exchange.BundleControllers.MergeLedgerMerge], limit: Option[com.histmersion.util.data.exchange.BundleControllers.MergeLedgerLimit]): Either[Api.Error, MergeLedger] =
    bundles.mergeLedger(uid, merge, limit.filter(_ > 0).getOrElse(BundleControllers.LedgerLimit))

  private def readable(f: HandedFile): Either[Api.Error, ByteSource] =
    val source = HandedFile.source(f)
    if source.size <= 0 then Left(Api.Error("the upload is empty — send a .hmx bundle as the body"))
    else Right(source)

object BundleControllers:
  /** How many merges `merge list` shows, and ledger rows `merge ledger`, unless asked. */
  type SetInstanceLabel = SetInstanceLabel.T
  object SetInstanceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportBundleResultPart2Part2 = ExportBundleResultPart2Part2.T
  object ExportBundleResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportBundleSince = ExportBundleSince.T
  object ExportBundleSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportBundleDryRun = ImportBundleDryRun.T
  object ImportBundleDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyStatusJob = ApplyStatusJob.T
  object ApplyStatusJob extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionBundle = SessionBundle.T
  object SessionBundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSessionBundle = PutSessionBundle.T
  object PutSessionBundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResolutionsText = ParseResolutionsText.T
  object ParseResolutionsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergesLimit = MergesLimit.T
  object MergesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeDetailId = MergeDetailId.T
  object MergeDetailId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerUid = MergeLedgerUid.T
  object MergeLedgerUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerMerge = MergeLedgerMerge.T
  object MergeLedgerMerge extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerLimit = MergeLedgerLimit.T
  object MergeLedgerLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256ResultPart2 = Sha256ResultPart2.T
  object Sha256ResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256Raw = Sha256Raw.T
  object Sha256Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SinceResultPart2 = SinceResultPart2.T
  object SinceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SinceRaw = SinceRaw.T
  object SinceRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispositionResult = DispositionResult.T
  object DispositionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ListLimit = 50
  val LedgerLimit = 200

  /** A bundle's name on the server: its sha256, 64 lower-case hex digits. Pure. */
  def sha256(raw: com.histmersion.util.data.exchange.BundleControllers.Sha256Raw): Either[Api.Error, com.histmersion.util.data.exchange.BundleControllers.Sha256ResultPart2] =
    val s = raw.trim.toLowerCase(java.util.Locale.ROOT)
    if s.length == 64 && s.forall(c => c.isDigit || (c >= 'a' && c <= 'f')) then Right(s)
    else Left(Api.Error(s"'$raw' is not a bundle's sha256 (64 hex digits, as `POST /api/bundle/upload` answers)"))

  /** `since` must be a moment: ISO-8601 with an offset (`2026-10-03T09:12:00Z`), as `asOf` is. */
  def since(raw: Option[com.histmersion.util.data.exchange.BundleControllers.SinceRaw]): Either[Api.Error, Option[com.histmersion.util.data.exchange.BundleControllers.SinceResultPart2]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        scala.util
          .Try(OffsetDateTime.parse(s))
          .toOption
          .map(_ => Some(s))
          .toRight(
            Api.Error(
              s"since '$s' is not a moment — give an ISO-8601 time with its offset, " +
                "e.g. 2026-10-03T09:12:00Z (a bundle's manifest asOf)"
            )
          )

  /** `attachment; filename="histmersion-<label or id>-<asOf>.hmx"`. Pure. */
  def disposition(m: Bundle.Manifest): com.histmersion.util.data.exchange.BundleControllers.DispositionResult =
    val who = m.instance.label.map(_.trim).filter(_.nonEmpty).getOrElse(m.instance.id.toString.take(8))
    val stamp = m.asOf.map(_.filter(_.isDigit).take(14)).filter(_.nonEmpty).getOrElse("empty")
    val name = s"histmersion-$who-$stamp${Bundle.Extension}".map(c =>
      if c.isLetterOrDigit || c == '.' || c == '-' then c else '-'
    )
    s"""attachment; filename="$name""""
