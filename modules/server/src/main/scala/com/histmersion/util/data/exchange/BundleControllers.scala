package com.histmersion
package util.data.exchange

import com.histmersion.api.Problem
import com.histmersion.PageSize
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
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.constraint.any.Pure

/** The bundle and merge endpoints, MCP tools and `histmersion bundle|merge` commands (docs/research-merge-import.md):
  * the raw inputs parsed, the maintenance guard of every write, the [[BundleService]] call. An upload is the file the
  * HTTP layer streamed the body into.
  */
final class BundleControllers(bundles: BundleService, maintenance: WriteGate):

  def instance: InstanceInfo = bundles.instance

  /** Name this instance; a blank label clears it. */
  def setInstance(label: Option[InstanceInfo.Label]): Either[Api.Error, InstanceInfo] =
    maintenance.guard(WriteGate.OperationName("instance"))(Right(bundles.setLabel(label)))

  /** `GET /api/bundle?since=` — the bundle file and its download name. */
  def exportBundle(
      since: Option[BundleScope.Since.At]
  ): Either[Api.Error, (HandedFile, BundleControllers.Disposition)] =
    BundleControllers.since(since).flatMap(bundles.exportBundle).map((f, m) => f -> BundleControllers.disposition(m))

  /** `POST /api/bundle/verify`. */
  def verify(upload: HandedFile): Either[Api.Error, Bundle.VerifyResult] = readable(upload).map(bundles.verify)

  /** `POST /api/bundle/import?dryRun=`. A write: refused while a restore holds the lock. Planned with no resolutions:
    * any open issue refuses it (decide them, then `POST /api/merge/import`).
    */
  def importBundle(dryRun: ImportOptions.DryRun, upload: HandedFile): Either[Api.Error, Bundle.ImportResult] =
    maintenance.guard(WriteGate.OperationName("bundle/import"))(
      readable(upload).flatMap(bundles.importBundle(_, dryRun))
    )

  /** `POST /api/bundle/upload` — keep the bundle under its sha256 so it can be planned and imported by name. The
    * manifest is read back, and what reading the file found wrong is reported.
    */
  def upload(upload: HandedFile): Either[Api.Error, Bundle.Upload] = readable(upload).flatMap(bundles.keep)

  /** `POST /api/merge/plan` (and MCP `merge_plan`) — pure: reads, never writes. */
  def plan(req: MergePlan.Request): Either[Api.Error, MergePlan] =
    BundleControllers.sha256(req.bundle.value).flatMap(bundles.plan(_, req.resolutions))

  /** `POST /api/merge/import` — import an uploaded bundle with resolutions. A write. */
  def importUploaded(req: MergeImportRequest): Either[Api.Error, Bundle.ImportResult] =
    maintenance.guard(WriteGate.OperationName("merge/import")):
      BundleControllers
        .sha256(req.bundle.value)
        .flatMap(bundles.importKept(_, req.resolutions, ImportOptions.DryRun(req.dryRun.value)))

  /** `POST /api/merge/apply` — carry out the reviewed plan of an uploaded bundle (M5). A write, and the one that takes
    * a backup first (`histmersion backup`'s `pg_dump`, into `backups.dir`). It runs as a background job
    * ([[MergeApplyJobs]]): the request waits up to `merge.apply-wait-seconds` and then answers where it is, so a large
    * collection's backup does not time the request out. A successful apply drops the review screen's draft of the
    * bundle (its decisions are remembered by issue id).
    */
  def applyUploaded(req: MergeApplyRequest): Either[Api.Error, MergeApplyJob] =
    maintenance.guard(WriteGate.OperationName("merge/apply")):
      BundleControllers
        .sha256(req.bundle.value)
        .flatMap(bundles.applyKept(_, MergeRow.PlanHash(req.planHash.value), req.resolutions))

  /** `GET /api/merge/apply/{job}` — the job is a `jobs` row, so it answers across a restart (#888). */
  def applyStatus(job: Job.Id): Either[Api.Error, MergeApplyJob] =
    bundles
      .applyStatus(job)
      .toRight(
        Api.Error(
          Problem(
            s"no merge apply '$job' on this server " +
              "(`histmersion merge list` shows what was merged)"
          ),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )

  /** `GET /api/merge/sessions/{bundle}`. */
  def session(bundle: Api.Merge.Id): Either[Api.Error, MergeSession] =
    BundleControllers.sha256(bundle.value).map(bundles.session)

  /** `PUT /api/merge/sessions/{bundle}`. */
  def putSession(bundle: Api.Merge.Id, r: MergeResolutions): Either[Api.Error, MergeSession] =
    maintenance.guard(WriteGate.OperationName("merge/session")):
      BundleControllers.sha256(bundle.value).map(bundles.putSession(_, r))

  /** `POST /api/merge/resolutions` — the YAML of a resolution file, read; every bad line at once. */
  def parseResolutions(text: MergeResolutionsFile.Text): Either[Api.Error, MergeResolutions] =
    MergeResolutionsFile
      .parse(text)
      .left
      .map(es => Api.Error(Problem(es.mkString("; ")), Some(Api.Error.Code(Write.CodeInvalid))))

  def merges(limit: Option[Api.Merge.Limit]): List[MergeInfo] =
    bundles.merges(limit.map(_.value).flatMap(PageSize.option(_)).getOrElse(BundleControllers.ListLimit))

  def mergeDetail(id: MergeRepo.MergeIdOrPrefix): Either[Api.Error, MergeDetail] = bundles.mergeDetail(id)

  def mergeLedger(
      uid: Option[MergeRepo.UidText],
      merge: Option[MergeRepo.MergeIdOrPrefix],
      limit: Option[Api.Merge.Limit]
  ): Either[Api.Error, MergeLedger] =
    bundles.mergeLedger(
      uid,
      merge,
      limit.map(_.value).flatMap(PageSize.option(_)).getOrElse(BundleControllers.LedgerLimit)
    )

  private def readable(f: HandedFile): Either[Api.Error, ByteSource] =
    val source = HandedFile.source(f)
    if source.size.value <= 0 then
      Left(Api.Error(Problem("the upload is empty — send a .hmx bundle as the body")))
    else Right(source)

object BundleControllers:
  type Disposition = Disposition.T
  object Disposition extends Newtype[String, Pure]

  /** How many merges `merge list` shows, and ledger rows `merge ledger`, unless asked. */

  val ListLimit: PageSize = PageSize(50)
  val LedgerLimit: PageSize = PageSize(200)

  /** A bundle's name on the server: its sha256, 64 lower-case hex digits. Pure. */
  def sha256(raw: String): Either[Api.Error, Sha256] =
    Sha256
      .either(raw.trim.toLowerCase(java.util.Locale.ROOT))
      .left
      .map(_ =>
        Api.Error(
          Problem(s"'$raw' is not a bundle's sha256 (64 hex digits, as `POST /api/bundle/upload` answers)")
        )
      )

  /** `since` must be a moment: ISO-8601 with an offset (`2026-10-03T09:12:00Z`), as `asOf` is. */
  def since(raw: Option[BundleScope.Since.At]): Either[Api.Error, Option[BundleScope.Since.At]] =
    raw.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        scala.util
          .Try(OffsetDateTime.parse(s))
          .toOption
          .map(_ => Some(BundleScope.Since.At(s)))
          .toRight(
            Api.Error(
              Problem(
                s"since '$s' is not a moment — give an ISO-8601 time with its offset, " +
                  "e.g. 2026-10-03T09:12:00Z (a bundle's manifest asOf)"
              )
            )
          )

  /** `attachment; filename="histmersion-<label or id>-<asOf>.hmx"`. Pure. */
  def disposition(m: Bundle.Manifest): Disposition =
    val who = m.instance.label.map(_.value.trim).filter(_.nonEmpty).getOrElse(m.instance.id.toString.take(8))
    val stamp = m.asOf.map(_.value.filter(_.isDigit).take(14)).filter(_.nonEmpty).getOrElse("empty")
    val name = s"histmersion-$who-$stamp${Bundle.Extension}".map(c =>
      if c.isLetterOrDigit || c == '.' || c == '-' then c else '-'
    )
    Disposition(s"""attachment; filename="$name"""")
