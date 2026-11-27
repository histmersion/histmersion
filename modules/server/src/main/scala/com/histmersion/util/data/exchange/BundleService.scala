package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.database.Transactor

import com.histmersion.PageSize
import com.histmersion.api.Write
import com.histmersion.api.exchange.{
  Bundle,
  MergeApplyJob,
  MergeDetail,
  MergeInfo,
  MergeLedger,
  MergePlan,
  MergeResolutions,
  MergeSession
}
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.config.AppConfig
import com.histmersion.util.data.exchange.BundleRepo
import com.histmersion.util.database.Migrate
import com.histmersion.util.jobs.MergeApplyRuns
import com.histmersion.util.settings.MergeSettingsViews
import com.histmersion.util.storage.{ByteSource, HandedFile, Location, Storage, Store}

import java.time.Clock
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.util.jobs.Job
import com.histmersion.util.storage.Reservation
import com.histmersion.api.ByteCount
import com.histmersion.util.problem.Problems
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator

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
  def setLabel(label: Option[InstanceInfo.Label])(using WritePermit): InstanceInfo
  def exportBundle(since: Option[BundleScope.Since.At]): Either[Api.Error, (HandedFile, Bundle.Manifest)]
  def verify(upload: ByteSource): Bundle.VerifyResult
  def importBundle(upload: ByteSource, dryRun: ImportOptions.DryRun)(using
      WritePermit
  ): Either[Api.Error, Bundle.ImportResult]
  def keep(upload: ByteSource): Either[Api.Error, Bundle.Upload]
  def plan(bundle: Sha256, resolutions: MergeResolutions): Either[Api.Error, MergePlan]
  def importKept(
      bundle: Sha256,
      resolutions: MergeResolutions,
      dryRun: ImportOptions.DryRun
  )(using WritePermit): Either[Api.Error, Bundle.ImportResult]
  def applyKept(
      bundle: Sha256,
      planHash: MergeRow.PlanHash,
      resolutions: MergeResolutions
  )(using WritePermit)(using Creator.New): Either[Api.Error, MergeApplyJob]
  def applyStatus(job: Job.Id): Option[MergeApplyJob]
  def session(bundle: Sha256): MergeSession
  def putSession(bundle: Sha256, resolutions: MergeResolutions)(using WritePermit): MergeSession
  def merges(limit: PageSize): List[MergeInfo]
  def mergeDetail(id: MergeRepo.MergeIdOrPrefix): Either[Api.Error, MergeDetail]
  def mergeLedger(
      uid: Option[MergeRepo.UidText],
      merge: Option[MergeRepo.MergeIdOrPrefix],
      limit: PageSize
  ): Either[Api.Error, MergeLedger]

object BundleService:
  final case class Wiring(
      ds: DataSource,
      config: () => AppConfig,
      storage: Storage,
      clock: Clock,
      carried: CarriedKinds,
      rules: MergeRuleSet,
      followUps: MergeFollowUps,
      applyJobs: MergeApplyJobs,
      backup: () => Either[Api.Error, BundleImporter.BackupFile]
  )

  def apply(wiring: Wiring): BundleService = Implementation(wiring)

  final private class Implementation(w: Wiring) extends BundleService:
    private def cfg: AppConfig = w.config()
    private def bundles: Store = w.storage.store(Location.Bundles)

    /** The server's stored originals: the converter's spool, `<sha256>.<ext>`. */
    private def originals: Store.Contents = Store.Contents(w.storage.store(Location.IngestOriginals))

    private def options(dryRun: ImportOptions.DryRun): ImportOptions =
      ImportOptions(
        dryRun,
        ImportOptions.ChunkChars(cfg.limits.chunkChars.value),
        ImportOptions.ToleranceKm(cfg.places.pointToleranceKm.value)
      )

    def instance: InstanceInfo = Transactor(w.ds).read(BundleRepo.instance)

    def setLabel(label: Option[InstanceInfo.Label])(using WritePermit): InstanceInfo =
      Transactor(w.ds).write(BundleRepo.setLabel(label))

    def exportBundle(since: Option[BundleScope.Since.At]): Either[Api.Error, (HandedFile, Bundle.Manifest)] =
      val store = bundles
      for
        _ <- usable(store).left.map(Api.Error(_))
        _ = pruneStale(store)
        key = Store.Key.applyUnsafe(s"export-${UUID.randomUUID()}${Bundle.Extension}")
        part <- store
          .reserve(key)
          .left
          .map(e => Api.Error(Problem.Unclassified(Legacy.Text(s"bundle export failed: ${e.message.sentence}"))))
        m <- write(part, since)
        _ <- part
          .commit()
          .left
          .map(e =>
            Api.Error(Problem.Unclassified(Legacy.Text(s"could not finish ${store.where(key)}: ${e.message.sentence}")))
          )
        file <- store
          .onDiskFile(key)
          .left
          .map(e =>
            Api.Error(Problem.Unclassified(Legacy.Text(s"could not finish ${store.where(key)}: ${e.message.sentence}")))
          )
      yield file -> m

    private def usable(store: Store): Either[Problems, Unit] =
      store
        .makeDirectory()
        .left
        .map(e =>
          Problem
            .Unclassified(Legacy.Text(s"bundles directory ${store.where()} is not usable: ${e.message.sentence}"))
        )

    private def write(part: Reservation, since: Option[BundleScope.Since.At]) =
      val written =
        try
          val out = java.io.BufferedOutputStream(part.output(), 1 << 16)
          try BundleExport.write(w.ds, out, since, originals, w.carried)
          finally out.close()
        catch case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      written.left.map { e =>
        part.discard()
        Api.Error(Problem.Unclassified(Legacy.Text(s"bundle export failed: $e")))
      }

    /** Finished exports past their download window, uploads nobody planned or imported for as long, and parts a crash
      * left behind. Best effort.
      */
    private def pruneStale(store: Store): Unit =
      val cutoff = w.clock.instant().minusSeconds(60L * math.max(0, cfg.bundles.staleMinutes.value))
      scala.util
        .Try {
          store
            .list()
            .filter(s => s.isFile && (s.key.name.value.startsWith("export-") || s.key.name.value.startsWith("upload-")))
            .filter(_.modified.isBefore(cutoff))
            .foreach(s => scala.util.Try(store.delete(s.key)))
        }
        .failed
        .foreach(e => scribe.warn(s"bundle: could not prune ${store.where()}: ${e.getMessage}"))

    def verify(upload: ByteSource): Bundle.VerifyResult =
      upload.read(in => BundleReader.verify(in, Migrate.current(w.ds).map(v => Bundle.Manifest.SchemaVersion(v.value))))

    def importBundle(upload: ByteSource, dryRun: ImportOptions.DryRun)(using
        WritePermit
    ): Either[Api.Error, Bundle.ImportResult] =
      shaOf(upload).left
        .map(Api.Error(_))
        .flatMap(importFrom(upload, _, MergeResolutions(), dryRun).left.map(Api.Error(_)))

    /** The sha256 of an uploaded file, as the server computes it. */
    private def shaOf(upload: ByteSource): Either[Problems, Sha256] =
      Sha256
        .either(upload.read(BundleReader.sha256Of).value)
        .left
        .map(e => Problem.Unclassified(Legacy.Text(s"the upload's sha256 is unusable: $e")))

    private def importFrom(
        bundle: ByteSource,
        sha: Sha256,
        resolutions: MergeResolutions,
        dryRun: ImportOptions.DryRun
    )(using WritePermit): Either[Problems, Bundle.ImportResult] =
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
          w.rules,
          w.clock
        )

    /** Where an uploaded bundle is kept: `upload-<sha256>.hmx`, content-addressed. */
    private def keyOf(sha: Sha256): Store.Key = Store.Key.applyUnsafe(s"upload-$sha${Bundle.Extension}")

    /** A kept bundle, touched: a planned bundle stays as long as it is being worked on. */
    private def kept(sha: Sha256): Either[Api.Error, ByteSource] =
      val store = bundles
      store.get(keyOf(sha)) match
        case None =>
          Left(
            Api.Error(
              Problem.Unclassified(
                Legacy.Text(
                  s"no uploaded bundle $sha — upload it first (POST /api/bundle/upload; " +
                    "`histmersion merge plan <file>` does), or it was cleared after bundles.stale-minutes"
                )
              ),
              Some(Api.Error.Code(Write.CodeNotFound))
            )
          )
        case Some(blob) =>
          store.touch(blob.key, w.clock.instant())
          Right(blob.asSource)

    def keep(upload: ByteSource): Either[Api.Error, Bundle.Upload] =
      val store = bundles
      for
        sha <- shaOf(upload).left.map(Api.Error(_))
        key = keyOf(sha)
        _ <- usable(store).left.map(Api.Error(_))
        _ = pruneStale(store)
        _ <- copyIn(store, key, upload).left.map(e =>
          Api.Error(Problem.Unclassified(Legacy.Text(s"could not keep the bundle in ${store.where()}: $e")))
        )
        blob <- store
          .get(key)
          .toRight(
            Api.Error(Problem.Unclassified(Legacy.Text(s"could not keep the bundle in ${store.where()}: it is gone")))
          )
      yield
        val loaded = blob.asSource.read(BundleReader.read(_, hashBlobs = false))
        Bundle.Upload(
          com.histmersion.api.Sha256(sha.value),
          ByteCount(blob.size.value),
          loaded.manifest,
          loaded.problems
        )

    private def copyIn(store: Store, key: Store.Key, upload: ByteSource): Either[String, Unit] =
      store.reserve(key).left.map(_.message.sentence).flatMap { part =>
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
            .flatMap(_ => part.commit().left.map(_.message.sentence).map(_ => ()))
        finally part.close()
      }

    def plan(bundle: Sha256, resolutions: MergeResolutions): Either[Api.Error, MergePlan] =
      kept(bundle).flatMap { source =>
        MergePlans
          .planFile(
            w.ds,
            source,
            resolutions,
            MergeSettingsViews.settings(cfg.merge),
            originals,
            ImportOptions.ChunkChars(cfg.limits.chunkChars.value),
            w.carried,
            w.rules
          )
          .left
          .map(e => Api.Error(e))
      }

    def importKept(
        bundle: Sha256,
        resolutions: MergeResolutions,
        dryRun: ImportOptions.DryRun
    )(using WritePermit): Either[Api.Error, Bundle.ImportResult] =
      kept(bundle).flatMap(importFrom(_, bundle, resolutions, dryRun).left.map(Api.Error(_)))

    def applyKept(
        bundle: Sha256,
        planHash: MergeRow.PlanHash,
        resolutions: MergeResolutions
    )(using WritePermit)(using Creator.New): Either[Api.Error, MergeApplyJob] =
      kept(bundle).flatMap { source =>
        w.applyJobs.start(bundle, planHash) { () =>
          BundleImporter
            .apply(
              w.ds,
              source,
              bundle,
              options(dryRun = ImportOptions.DryRun(false)),
              originals,
              resolutions,
              MergeSettingsViews.settings(cfg.merge),
              planHash,
              () => w.backup().fold(e => Left(e.problems), f => Right(f.value)),
              w.carried,
              w.rules,
              w.followUps,
              w.clock
            )
            .map { r =>
              if r.applied.value && r.mergeId.isDefined then
                Transactor(w.ds).write(MergeRepo.dropSession(MergeRepo.BundleName(bundle.value)))
              r
            }
        }
      }

    def applyStatus(job: Job.Id): Option[MergeApplyJob] = w.applyJobs.status(job)

    def session(bundle: Sha256): MergeSession =
      Transactor(w.ds).read(MergeRepo.session(MergeRepo.BundleName(bundle.value)))

    def putSession(bundle: Sha256, resolutions: MergeResolutions)(using WritePermit): MergeSession =
      Transactor(w.ds).write(MergeRepo.putSession(MergeRepo.BundleName(bundle.value), resolutions))

    def merges(limit: PageSize): List[MergeInfo] = Transactor(w.ds).read(MergeRepo.list(limit))

    def mergeDetail(id: MergeRepo.MergeIdOrPrefix): Either[Api.Error, MergeDetail] =
      Transactor(w.ds).read(MergeRepo.detail(id))

    def mergeLedger(
        uid: Option[MergeRepo.UidText],
        merge: Option[MergeRepo.MergeIdOrPrefix],
        limit: PageSize
    ): Either[Api.Error, MergeLedger] =
      Transactor(w.ds).read(MergeRepo.ledger(uid, merge, limit, w.rules))
