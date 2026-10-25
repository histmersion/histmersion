package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{BundleExport, BundleImport, BundleRepo, MergeRepo, MergeState, Migrate, Originals}

import java.io.{File, InputStream}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.jdk.CollectionConverters.*

/** The portable bundle (docs/research-merge-import.md, milestones M0–M2): this instance's identity,
  * `bundle export`, `bundle verify` and `bundle import`. Reached only through [[Handlers]], so the
  * REST routes and the CLI share one implementation.
  *
  * An export is built into `bundles.dir` and handed to the HTTP layer as a file; the next export
  * deletes finished ones older than `bundles.stale-minutes`. An upload (verify, import) is the
  * temporary file tapir streamed the body into, read twice by an import (rows, then blobs). */
final class BundleHandlers(deps: Deps, backup: () => Either[ApiError, String]):

  /** No backup service: a test, or a caller that never applies a merge. */
  def this(deps: Deps) = this(deps, () => Left(ApiError("this server takes no backups")))


  private val ds          = deps.ds
  private def cfg         = deps.cfg
  private val maintenance = deps.maintenance

  private def dir: Path = Path.of(cfg.bundles.dir).toAbsolutePath.normalize

  def instance: InstanceInfo = BundleRepo.instance(ds)

  def setInstance(in: InstanceLabel): Either[ApiError, InstanceInfo] =
    maintenance.guard("instance")(Right(BundleRepo.setLabel(ds, in.label)))

  /** `GET /api/bundle?since=` — the bundle file and its download name. */
  def exportBundle(since: Option[String]): Either[ApiError, (File, String)] =
    for
      at   <- BundleHandlers.since(since)
      _    <- scala.util.Try(Files.createDirectories(dir)).toEither.left
                .map(e => ApiError(s"bundles directory $dir is not usable: ${e.getMessage}"))
      _     = pruneStale()
      part  = dir.resolve(s"export-${UUID.randomUUID()}.hmx.part")
      m    <- write(part, at)
      done  = dir.resolve(part.getFileName.toString.stripSuffix(".part"))
      _    <- scala.util.Try(Files.move(part, done, StandardCopyOption.ATOMIC_MOVE)).toEither.left
                .map(e => ApiError(s"could not finish $done: ${e.getMessage}"))
    yield done.toFile -> BundleHandlers.disposition(m)

  private def write(part: Path, since: Option[String]): Either[ApiError, BundleManifest] =
    val out = Files.newOutputStream(part)
    val written =
      try BundleExport.write(ds, java.io.BufferedOutputStream(out, 1 << 16), since, originals)
      catch case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      finally out.close()
    written.left.map { e =>
      val _ = Files.deleteIfExists(part)
      ApiError(s"bundle export failed: $e")
    }

  /** Finished exports past their download window, uploads nobody planned or imported for as long,
    * and parts a crash left behind. Best effort. */
  private def pruneStale(): Unit =
    val cutoff = Instant.now().minusSeconds(60L * math.max(0, cfg.bundles.staleMinutes))
    scala.util.Try {
      val s = Files.list(dir)
      try
        s.iterator.asScala
          .filter(p => p.getFileName.toString.startsWith("export-") || p.getFileName.toString.startsWith("upload-"))
          .filter(p => Files.getLastModifiedTime(p).toInstant.isBefore(cutoff))
          .foreach(p => scala.util.Try(Files.deleteIfExists(p)))
      finally s.close()
    }.failed.foreach(e => scribe.warn(s"bundle: could not prune $dir: ${e.getMessage}"))

  /** `POST /api/bundle/verify`. */
  def verify(upload: File): Either[ApiError, BundleVerifyResult] =
    readable(upload).map(f => withStream(f)(in => BundleReader.verify(in, Migrate.current(ds))))

  /** `POST /api/bundle/import?dryRun=`. A write: refused while a restore holds the lock. Planned
    * with no resolutions: any open issue refuses it (decide them, then `POST /api/merge/import`). */
  def importBundle(dryRun: Boolean, upload: File): Either[ApiError, BundleImportResult] =
    maintenance.guard("bundle/import"):
      readable(upload).flatMap { f =>
        val sha = withStream(f)(BundleReader.sha256Of)
        importFile(f.toPath, sha, MergeResolutions(), dryRun)
      }

  private def importFile(file: Path, sha: String, resolutions: MergeResolutions, dryRun: Boolean)
      : Either[ApiError, BundleImportResult] =
    BundleImport.run(ds, file, sha,
        BundleImport.Options(dryRun, cfg.limits.chunkChars, cfg.places.pointToleranceKm), originals, resolutions, MergeSettingsViews.settings(cfg.merge))
      .left.map(e => ApiError(s"bundle import failed: $e"))

  // ── uploaded bundles and the merge plan (M3) ─────────────────────────────────────────────────

  /** Where an uploaded bundle is kept: `upload-<sha256>.hmx`, content-addressed. */
  private def uploaded(sha: String): Either[ApiError, Path] =
    BundleHandlers.sha256(sha).flatMap { h =>
      val p = dir.resolve(s"upload-$h${Bundle.Extension}")
      if Files.isRegularFile(p) then Right(p)
      else Left(ApiError(s"no uploaded bundle $h — upload it first (POST /api/bundle/upload; " +
        "`histmersion merge plan <file>` does), or it was cleared after bundles.stale-minutes", Some(Write.CodeNotFound)))
    }

  /** `POST /api/bundle/upload` — keep the bundle under its sha256 so it can be planned and imported
    * by name. The manifest is read back, and what reading the file found wrong is reported. */
  def upload(upload: File): Either[ApiError, BundleUpload] =
    readable(upload).flatMap { f =>
      val sha = withStream(f)(BundleReader.sha256Of)
      for
        _ <- scala.util.Try(Files.createDirectories(dir)).toEither.left
               .map(e => ApiError(s"bundles directory $dir is not usable: ${e.getMessage}"))
        _  = pruneStale()
        dest = dir.resolve(s"upload-$sha${Bundle.Extension}")
        _ <- scala.util.Try {
               val part = dir.resolve(s"upload-$sha${Bundle.Extension}.part")
               val _    = Files.copy(f.toPath, part, StandardCopyOption.REPLACE_EXISTING)
               Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
             }.toEither.left.map(e => ApiError(s"could not keep the bundle in $dir: ${e.getMessage}"))
      yield
        val loaded = withStream(dest.toFile)(BundleReader.read(_, hashBlobs = false))
        BundleUpload(sha, Files.size(dest), loaded.manifest, loaded.problems)
    }

  /** `POST /api/merge/plan` (and MCP `merge_plan`) — pure: reads, never writes. */
  def plan(req: MergePlanRequest): Either[ApiError, MergePlan] =
    uploaded(req.bundle).flatMap { p =>
      touch(p)
      MergeState.planFile(ds, p, req.resolutions, MergeSettingsViews.settings(cfg.merge), originals, cfg.limits.chunkChars)
        .left.map(e => ApiError(s"merge plan failed: $e"))
    }

  /** `POST /api/merge/import` — import an uploaded bundle with resolutions. A write. */
  def importUploaded(req: MergeImportRequest): Either[ApiError, BundleImportResult] =
    maintenance.guard("merge/import"):
      uploaded(req.bundle).flatMap { p =>
        touch(p)
        importFile(p, BundleHandlers.sha256(req.bundle).getOrElse(req.bundle), req.resolutions, req.dryRun)
      }

  /** `POST /api/merge/apply` — carry out the reviewed plan of an uploaded bundle (M5). A write, and the
    * one that takes a backup first (`histmersion backup`'s `pg_dump`, into `backups.dir`). It runs as a
    * background job ([[MergeApplyJobs]]): the request waits up to `merge.apply-wait-seconds` and then
    * answers where it is, so a large collection's backup does not time the request out. A successful
    * apply drops the review screen's draft of the bundle (its decisions are remembered by issue id). */
  def applyUploaded(req: MergeApplyRequest): Either[ApiError, MergeApplyJob] =
    maintenance.guard("merge/apply"):
      uploaded(req.bundle).flatMap { p =>
        touch(p)
        val sha = BundleHandlers.sha256(req.bundle).getOrElse(req.bundle)
        applyJobs.start(sha, req.planHash) { () =>
          BundleImport.apply(ds, p, sha,
              BundleImport.Options(dryRun = false, cfg.limits.chunkChars, cfg.places.pointToleranceKm), originals,
              req.resolutions, MergeSettingsViews.settings(cfg.merge), req.planHash, () => backup().left.map(_.message))
            .map { r =>
              if r.applied && r.mergeId.isDefined then MergeRepo.dropSession(ds, sha)
              r
            }
            .left.map(e => s"merge apply failed: $e")
        }
      }

  /** `GET /api/merge/apply/{job}` — the job is a `jobs` row, so it answers across a restart (#888). */
  def applyStatus(job: String): Either[ApiError, MergeApplyJob] =
    applyJobs.status(job).toRight(ApiError(s"no merge apply '$job' on this server " +
      "(`histmersion merge list` shows what was merged)", Some(Write.CodeNotFound)))

  private val applyJobs = MergeApplyJobs(ds, cfg.merge.applyWaitSeconds, deps.pins)

  // ── the review screen's draft, the resolution file, the merges (M6–M7) ─────────────────────────

  /** `GET /api/merge/sessions/{bundle}`. */
  def session(bundle: String): Either[ApiError, MergeSession] =
    BundleHandlers.sha256(bundle).map(MergeRepo.session(ds, _))

  /** `PUT /api/merge/sessions/{bundle}`. */
  def putSession(bundle: String, r: MergeResolutions): Either[ApiError, MergeSession] =
    maintenance.guard("merge/session"):
      BundleHandlers.sha256(bundle).map(MergeRepo.putSession(ds, _, r))

  /** `POST /api/merge/resolutions` — the YAML of a resolution file, read; every bad line at once. */
  def parseResolutions(text: String): Either[ApiError, MergeResolutions] =
    MergeResolutionsFile.parse(text).left.map(es => ApiError(es.mkString("; "), Some(Write.CodeInvalid)))

  def merges(limit: Option[Int]): List[MergeInfo] = MergeRepo.list(ds, limit.filter(_ > 0).getOrElse(BundleHandlers.ListLimit))

  def mergeDetail(id: String): Either[ApiError, MergeDetail] = MergeRepo.detail(ds, id)

  def mergeLedger(uid: Option[String], merge: Option[String], limit: Option[Int]): Either[ApiError, MergeLedger] =
    MergeRepo.ledger(ds, uid, merge, limit.filter(_ > 0).getOrElse(BundleHandlers.LedgerLimit))

  /** A planned bundle stays as long as it is being worked on. */
  private def touch(p: Path): Unit =
    scala.util.Try(Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.from(Instant.now())))
      .failed.foreach(e => scribe.warn(s"bundle: could not touch $p: ${e.getMessage}"))

  private def readable(f: File): Either[ApiError, File] =
    if f.length() <= 0 then Left(ApiError("the upload is empty — send a .hmx bundle as the body"))
    else Right(f)

  private def withStream[A](f: File)(use: InputStream => A): A =
    val in = java.io.BufferedInputStream(Files.newInputStream(f.toPath), 1 << 16)
    try use(in) finally in.close()

  /** The server's stored originals: the converter's spool, `<sha256>.<ext>`. */
  private def originals: Originals = Originals.inDir(Path.of(cfg.converter.spoolDir))

object BundleHandlers:
  /** How many merges `merge list` shows, and ledger rows `merge ledger`, unless asked. */
  val ListLimit   = 50
  val LedgerLimit = 200

  /** A bundle's name on the server: its sha256, 64 lower-case hex digits. Pure. */
  def sha256(raw: String): Either[ApiError, String] =
    val s = raw.trim.toLowerCase(java.util.Locale.ROOT)
    if s.length == 64 && s.forall(c => c.isDigit || (c >= 'a' && c <= 'f')) then Right(s)
    else Left(ApiError(s"'$raw' is not a bundle's sha256 (64 hex digits, as `POST /api/bundle/upload` answers)"))

  /** `since` must be a moment: ISO-8601 with an offset (`2026-10-03T09:12:00Z`), as `asOf` is. */
  def since(raw: Option[String]): Either[ApiError, Option[String]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None => Right(None)
      case Some(s) =>
        scala.util.Try(OffsetDateTime.parse(s)).toOption
          .map(_ => Some(s))
          .toRight(ApiError(s"since '$s' is not a moment — give an ISO-8601 time with its offset, " +
            "e.g. 2026-10-03T09:12:00Z (a bundle's manifest asOf)"))

  /** `attachment; filename="histmersion-<label or id>-<asOf>.hmx"`. Pure. */
  def disposition(m: BundleManifest): String =
    val who   = m.instance.label.map(_.trim).filter(_.nonEmpty).getOrElse(m.instance.id.toString.take(8))
    val stamp = m.asOf.map(_.filter(_.isDigit).take(14)).filter(_.nonEmpty).getOrElse("empty")
    val name  = s"histmersion-$who-$stamp${Bundle.Extension}".map(c => if c.isLetterOrDigit || c == '.' || c == '-' then c else '-')
    s"""attachment; filename="$name""""


/** `merge apply` in the background (docs/research-merge-import.md §7.7, open question 3): the apply
  * takes a `pg_dump` before it writes, which on a large collection outlasts an HTTP request. One apply
  * at a time — a second is refused while one runs (the import's advisory lock would serialise them
  * anyway, but a person should not queue a second merge blind).
  *
  * The job is a `jobs` row (kind `merge-apply`, #888), so the CLI and the web screen can follow it
  * across a restart: what it reported once done, and — when the server stopped while it ran —
  * `interrupted` (the startup reconciliation fails every unfinished job; the apply's transaction
  * committed or rolled back as a whole). Only the wait for the first answer is in memory. */
final class MergeApplyJobs(ds: javax.sql.DataSource, waitSeconds: => Int,
    pins: com.histmersion.server.hot.Pinning = com.histmersion.server.hot.Pinning.none):
  import com.histmersion.db.JobRepo
  import java.util.concurrent.{CompletableFuture, TimeUnit, TimeoutException}

  /** Start `run` on its own thread and wait up to `waitSeconds` for it. */
  def start(bundle: String, planHash: String)(run: () => Either[String, MergeApplyResult]): Either[ApiError, MergeApplyJob] =
    synchronized {
      if JobRepo.mergeApplyRunning(ds) then Left(ApiError("a merge apply is still running — follow it with " +
        "`GET /api/merge/apply/{job}` and apply again when it is done", Some(Backup.CodeMaintenance)))
      else
        val id   = JobRepo.startMergeApply(ds, bundle, planHash)
        val done = CompletableFuture[Unit]()
        // on the generation (database) the request was pinned to
        val work = pins.carry {
          val outcome = scala.util.Try(run()).toEither.left.map(e =>
            s"merge apply crashed: ${e.getClass.getSimpleName}: ${e.getMessage}").flatten
          try JobRepo.finishMergeApply(ds, id, outcome)
          catch case e: Exception => scribe.error(s"merge apply $id: could not record its end: ${e.getMessage}", e)
          finally { val _ = done.complete(()) }
        }
        val _ = Thread.ofPlatform().daemon(true).name("histmersion-merge-apply").start(() => work.run())
        Right(id -> done)
    }.map { (id, done) =>
      try { val _ = done.get(math.max(0, waitSeconds).toLong, TimeUnit.SECONDS) }
      catch case _: TimeoutException => ()
      status(id).getOrElse(MergeApplyJob(id, MergeJobState.Running, bundle, planHash, 0))
    }

  def status(id: String): Option[MergeApplyJob] = JobRepo.mergeApply(ds, id.trim)
