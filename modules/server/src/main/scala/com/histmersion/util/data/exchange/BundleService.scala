package com.histmersion.util.data.exchange

import com.histmersion.util.database.Transactor

import com.histmersion.domain.library.sources.referrers.SourceReferrers

import com.histmersion.api.{
  ApiError,
  Bundle,
  BundleImportResult,
  BundleManifest,
  BundleUpload,
  BundleVerifyResult,
  InstanceInfo,
  MergeApplyJob,
  MergeDetail,
  MergeInfo,
  MergeLedger,
  MergePlan,
  MergeResolutions,
  MergeSession,
  Write
}
import com.histmersion.config.AppConfig
import com.histmersion.util.data.exchange.BundleRepo
import com.histmersion.util.database.Migrate
import com.histmersion.util.settings.MergeSettingsViews
import com.histmersion.util.storage.{ByteSource, HandedFile, Location, Storage, Store, StoreContents, StoreKey}

import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

/** The portable bundle and the merge (docs/research-merge-import.md, milestones M0–M7): this instance's identity, the
  * export, verify and import of a `.hmx` bundle, the bundles kept to be planned, the plan, the apply, the review
  * screen's draft and what was merged.
  *
  * An export is built in the bundles store (`bundles.dir`) and handed to the HTTP layer as its file; the next export or
  * upload removes finished ones older than `bundles.stale-minutes`. An upload is kept there under its sha256 so it can
  * be planned and imported by name; an import reads it twice (rows, then blobs).
  */
trait BundleService:
  def instance: InstanceInfo
  def setLabel(label: Option[String]): InstanceInfo
  def exportBundle(since: Option[String]): Either[ApiError, (HandedFile, BundleManifest)]
  def verify(upload: ByteSource): BundleVerifyResult
  def importBundle(upload: ByteSource, dryRun: Boolean): Either[ApiError, BundleImportResult]
  def keep(upload: ByteSource): Either[ApiError, BundleUpload]
  def plan(bundle: String, resolutions: MergeResolutions): Either[ApiError, MergePlan]
  def importKept(bundle: String, resolutions: MergeResolutions, dryRun: Boolean): Either[ApiError, BundleImportResult]
  def applyKept(bundle: String, planHash: String, resolutions: MergeResolutions): Either[ApiError, MergeApplyJob]
  def applyStatus(job: String): Option[MergeApplyJob]
  def session(bundle: String): MergeSession
  def putSession(bundle: String, resolutions: MergeResolutions): MergeSession
  def merges(limit: Int): List[MergeInfo]
  def mergeDetail(id: String): Either[ApiError, MergeDetail]
  def mergeLedger(uid: Option[String], merge: Option[String], limit: Int): Either[ApiError, MergeLedger]

object BundleService:

  /** What the service runs over: the database, the settings of the moment, the stores, the clock, the registry's
    * carried kinds and merge rules, the apply's jobs and the backup an apply takes first; `sourceReferrers` keep what
    * every domain stores of a source right when an import renames one.
    */
  final case class Wiring(
      ds: DataSource,
      config: () => AppConfig,
      storage: Storage,
      clock: Clock,
      carried: CarriedKinds,
      rules: MergeRuleSet,
      followUps: MergeFollowUps,
      applyJobs: MergeApplyJobs,
      backup: () => Either[ApiError, String],
      sourceReferrers: SourceReferrers
  )

  def apply(wiring: Wiring): BundleService = Implementation(wiring)

  final private class Implementation(w: Wiring) extends BundleService:
    private def cfg: AppConfig = w.config()
    private def bundles: Store = w.storage.store(Location.Bundles)

    /** The server's stored originals: the converter's spool, `<sha256>.<ext>`. */
    private def originals: StoreContents = StoreContents(w.storage.store(Location.IngestOriginals))

    private def options(dryRun: Boolean): ImportOptions =
      ImportOptions(dryRun, cfg.limits.chunkChars, cfg.places.pointToleranceKm, w.sourceReferrers)

    def instance: InstanceInfo = Transactor(w.ds).read(BundleRepo.instance)

    def setLabel(label: Option[String]): InstanceInfo = Transactor(w.ds).transact(BundleRepo.setLabel(label))

    def exportBundle(since: Option[String]): Either[ApiError, (HandedFile, BundleManifest)] =
      val store = bundles
      for
        _ <- usable(store)
        _ = pruneStale(store)
        key = StoreKey.applyUnsafe(s"export-${UUID.randomUUID()}${Bundle.Extension}")
        part <- store.reserve(key).left.map(e => ApiError(s"bundle export failed: ${e.message}"))
        m <- write(part, since)
        _ <- part.commit().left.map(e => ApiError(s"could not finish ${store.where(key)}: ${e.message}"))
        file <- store.onDiskFile(key).left.map(e => ApiError(s"could not finish ${store.where(key)}: ${e.message}"))
      yield file -> m

    private def usable(store: Store): Either[ApiError, Unit] =
      store
        .makeDirectory()
        .left
        .map(e => ApiError(s"bundles directory ${store.where()} is not usable: ${e.message}"))

    private def write(part: com.histmersion.util.storage.Reservation, since: Option[String]) =
      val written =
        try
          val out = java.io.BufferedOutputStream(part.output(), 1 << 16)
          try BundleExport.write(w.ds, out, since, originals, w.carried)
          finally out.close()
        catch case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      written.left.map { e =>
        part.discard()
        ApiError(s"bundle export failed: $e")
      }

    /** Finished exports past their download window, uploads nobody planned or imported for as long, and parts a crash
      * left behind. Best effort.
      */
    private def pruneStale(store: Store): Unit =
      val cutoff = w.clock.instant().minusSeconds(60L * math.max(0, cfg.bundles.staleMinutes))
      scala.util
        .Try {
          store
            .list()
            .filter(s => s.isFile && (s.key.name.startsWith("export-") || s.key.name.startsWith("upload-")))
            .filter(_.modified.isBefore(cutoff))
            .foreach(s => scala.util.Try(store.delete(s.key)))
        }
        .failed
        .foreach(e => scribe.warn(s"bundle: could not prune ${store.where()}: ${e.getMessage}"))

    def verify(upload: ByteSource): BundleVerifyResult =
      upload.read(in => BundleReader.verify(in, Migrate.current(w.ds)))

    def importBundle(upload: ByteSource, dryRun: Boolean): Either[ApiError, BundleImportResult] =
      importFrom(upload, upload.read(BundleReader.sha256Of), MergeResolutions(), dryRun)

    private def importFrom(
        bundle: ByteSource,
        sha: String,
        resolutions: MergeResolutions,
        dryRun: Boolean
    ): Either[ApiError, BundleImportResult] =
      BundleImporter
        .run(
          w.ds,
          bundle,
          sha,
          options(dryRun),
          originals,
          resolutions,
          MergeSettingsViews.settings(cfg.merge),
          w.carried,
          w.rules
        )
        .left
        .map(e => ApiError(s"bundle import failed: $e"))

    /** Where an uploaded bundle is kept: `upload-<sha256>.hmx`, content-addressed. */
    private def keyOf(sha: String): StoreKey = StoreKey.applyUnsafe(s"upload-$sha${Bundle.Extension}")

    /** A kept bundle, touched: a planned bundle stays as long as it is being worked on. */
    private def kept(sha: String): Either[ApiError, ByteSource] =
      val store = bundles
      store.get(keyOf(sha)) match
        case None =>
          Left(
            ApiError(
              s"no uploaded bundle $sha — upload it first (POST /api/bundle/upload; " +
                "`histmersion merge plan <file>` does), or it was cleared after bundles.stale-minutes",
              Some(Write.CodeNotFound)
            )
          )
        case Some(blob) =>
          store.touch(blob.key, w.clock.instant())
          Right(blob.asSource)

    def keep(upload: ByteSource): Either[ApiError, BundleUpload] =
      val sha = upload.read(BundleReader.sha256Of)
      val store = bundles
      val key = keyOf(sha)
      for
        _ <- usable(store)
        _ = pruneStale(store)
        _ <- copyIn(store, key, upload).left.map(e => ApiError(s"could not keep the bundle in ${store.where()}: $e"))
        blob <- store.get(key).toRight(ApiError(s"could not keep the bundle in ${store.where()}: it is gone"))
      yield
        val loaded = blob.asSource.read(BundleReader.read(_, hashBlobs = false))
        BundleUpload(sha, blob.size.value, loaded.manifest, loaded.problems)

    private def copyIn(store: Store, key: StoreKey, upload: ByteSource): Either[String, Unit] =
      store.reserve(key).left.map(_.message).flatMap { part =>
        try
          scala.util
            .Try {
              val out = part.output()
              try upload.read(in => in.transferTo(out): Unit)
              finally out.close()
            }
            .toEither
            .left
            .map(e => Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
            .flatMap(_ => part.commit().left.map(_.message).map(_ => ()))
        finally part.close()
      }

    def plan(bundle: String, resolutions: MergeResolutions): Either[ApiError, MergePlan] =
      kept(bundle).flatMap { source =>
        MergePlans
          .planFile(
            w.ds,
            source,
            resolutions,
            MergeSettingsViews.settings(cfg.merge),
            originals,
            cfg.limits.chunkChars,
            w.carried,
            w.rules
          )
          .left
          .map(e => ApiError(s"merge plan failed: $e"))
      }

    def importKept(
        bundle: String,
        resolutions: MergeResolutions,
        dryRun: Boolean
    ): Either[ApiError, BundleImportResult] =
      kept(bundle).flatMap(importFrom(_, bundle, resolutions, dryRun))

    def applyKept(bundle: String, planHash: String, resolutions: MergeResolutions): Either[ApiError, MergeApplyJob] =
      kept(bundle).flatMap { source =>
        w.applyJobs.start(bundle, planHash) { () =>
          BundleImporter
            .apply(
              w.ds,
              source,
              bundle,
              options(dryRun = false),
              originals,
              resolutions,
              MergeSettingsViews.settings(cfg.merge),
              planHash,
              () => w.backup().left.map(_.message),
              w.carried,
              w.rules,
              w.followUps
            )
            .map { r =>
              if r.applied && r.mergeId.isDefined then Transactor(w.ds).transact(MergeRepo.dropSession(bundle))
              r
            }
            .left
            .map(e => s"merge apply failed: $e")
        }
      }

    def applyStatus(job: String): Option[MergeApplyJob] = w.applyJobs.status(job)

    def session(bundle: String): MergeSession = Transactor(w.ds).read(MergeRepo.session(bundle))

    def putSession(bundle: String, resolutions: MergeResolutions): MergeSession =
      Transactor(w.ds).transact(MergeRepo.putSession(bundle, resolutions))

    def merges(limit: Int): List[MergeInfo] = Transactor(w.ds).read(MergeRepo.list(limit))

    def mergeDetail(id: String): Either[ApiError, MergeDetail] = Transactor(w.ds).read(MergeRepo.detail(id))

    def mergeLedger(uid: Option[String], merge: Option[String], limit: Int): Either[ApiError, MergeLedger] =
      Transactor(w.ds).read(MergeRepo.ledger(uid, merge, limit, w.rules))
