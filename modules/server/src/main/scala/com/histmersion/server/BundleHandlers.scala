package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{BundleExport, BundleImport, BundleRepo, Migrate, Originals}

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
final class BundleHandlers(deps: Deps):

  private val ds          = deps.ds
  private val cfg         = deps.cfg
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

  /** Finished exports past their download window, and parts a crash left behind. Best effort. */
  private def pruneStale(): Unit =
    val cutoff = Instant.now().minusSeconds(60L * math.max(0, cfg.bundles.staleMinutes))
    scala.util.Try {
      val s = Files.list(dir)
      try
        s.iterator.asScala
          .filter(p => p.getFileName.toString.startsWith("export-"))
          .filter(p => Files.getLastModifiedTime(p).toInstant.isBefore(cutoff))
          .foreach(p => scala.util.Try(Files.deleteIfExists(p)))
      finally s.close()
    }.failed.foreach(e => scribe.warn(s"bundle: could not prune $dir: ${e.getMessage}"))

  /** `POST /api/bundle/verify`. */
  def verify(upload: File): Either[ApiError, BundleVerifyResult] =
    readable(upload).map(f => withStream(f)(in => BundleReader.verify(in, Migrate.current(ds))))

  /** `POST /api/bundle/import?dryRun=`. A write: refused while a restore holds the lock. */
  def importBundle(dryRun: Boolean, upload: File): Either[ApiError, BundleImportResult] =
    maintenance.guard("bundle/import"):
      readable(upload).flatMap { f =>
        val sha = withStream(f)(BundleReader.sha256Of)
        BundleImport.run(ds, f.toPath, sha,
            BundleImport.Options(dryRun, cfg.limits.chunkChars, cfg.places.pointToleranceKm), originals)
          .left.map(e => ApiError(s"bundle import failed: $e"))
      }

  private def readable(f: File): Either[ApiError, File] =
    if f.length() <= 0 then Left(ApiError("the upload is empty — send a .hmx bundle as the body"))
    else Right(f)

  private def withStream[A](f: File)(use: InputStream => A): A =
    val in = java.io.BufferedInputStream(Files.newInputStream(f.toPath), 1 << 16)
    try use(in) finally in.close()

  /** The server's stored originals: the converter's spool, `<sha256>.<ext>`. */
  private val originals: Originals = Originals.inDir(Path.of(cfg.converter.spoolDir))

object BundleHandlers:
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

