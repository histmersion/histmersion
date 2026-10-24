package com.histmersion.server.acquire

import com.histmersion.values.Citekey

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{AcquireRepo, SourceRepo}

import java.nio.file.{Files, Path, StandardOpenOption}
import javax.sql.DataSource

/** The metadata an ingest carries, whatever the acquisition channel was. */
final case class IngestMeta(
    title: String,
    authors: List[String] = Nil,
    doi: Option[String] = None,
    year: Option[Int] = None,
    venue: Option[String] = None,
    summary: Option[String] = None,
    provenance: String = "oa",
    /** `references` (and other typed) edges discovered while acquiring. */
    edges: List[Edge] = Nil,
    citedByCount: Option[Int] = None,
    influentialCitations: Option[Int] = None
)

/** @param created false when the citekey already existed and was enriched rather than replaced */
final case class IngestOutcome(citekey: String, created: Boolean, jobId: Option[String], detail: String)

/** The seam to **stream 2.4's ingest job queue**.
  *
  * `histmersion ingest <doi>` fetches the PDF server-side and then hands the FILE to the same job path an
  * upload takes: convert → body → assets. That queue landed with §1.8, so the implementation wired in
  * `Main` is [[QueuedIngest]]; [[SkeletonIngest]] remains the metadata-only half it builds on (and the
  * whole story when no file was acquired).
  */
trait IngestJobs:
  /** @param pdfPath the acquired file on the SERVER's filesystem, or `None` when no PDF was found
    *                (the source is still created from metadata, so it can be enriched and queued) */
  def enqueue(pdfPath: Option[Path], citekey: String, meta: IngestMeta): Either[String, IngestOutcome]

/** The synchronous, metadata-only half of an acquisition.
  *
  * It stores an **asset-less skeleton source**: the metadata, the reference edges and
  * `provenance = "oa"` (or whatever the channel was), with no body — converting the PDF is exactly
  * the part stream 2.4 owns. The file itself is left in the spool directory and its path is reported,
  * so the conversion can be run against it later without re-downloading.
  *
  * Crucially it does NOT replace a source that already exists: a re-ingest of an enriched paper must
  * not wipe its findings, notes and tags, so an existing citekey gets a metadata-only PATCH.
  */
final class SkeletonIngest(ds: DataSource, chunkChars: Int,
    toleranceKm: Double = com.histmersion.db.PlaceRepo.DefaultToleranceKm) extends IngestJobs:

  def enqueue(pdfPath: Option[Path], citekey: String, meta: IngestMeta): Either[String, IngestOutcome] =
    val exists = AcquireRepo.exists(ds, citekey)
    val result =
      if exists then patchExisting(citekey, meta)
      else create(citekey, meta)
    result.map { _ =>
      val edges = AcquireRepo.addEdges(ds, meta.edges.map(_.copy(src = citekey)))
      val where = pdfPath.map(p => s", file spooled at $p").getOrElse(", no file")
      IngestOutcome(citekey, created = !exists, jobId = None,
        detail = s"${if exists then "updated" else "created"} skeleton source" +
          s" ($edges new edge(s))$where — the body is converted by the OCR job (§1.8)")
    }

  private def create(citekey: String, meta: IngestMeta): Either[String, Unit] =
    Write.applyPut(citekey, SourcePut(
      title = meta.title,
      authors = meta.authors,
      doi = meta.doi,
      year = meta.year,
      venue = meta.venue,
      status = Some(SourceStatus.Unassessed),
      summary = meta.summary,
      citedByCount = meta.citedByCount,
      influentialCitations = meta.influentialCitations,
      provenance = Some(meta.provenance)
    )).map(s => { val _ = SourceRepo.put(ds, s, chunkChars, toleranceKm) })

  private def patchExisting(citekey: String, meta: IngestMeta): Either[String, Unit] =
    SourceRepo.patch(ds, citekey, SourcePatch(
      metadata = Some(MetadataPatch(
        title = Some(meta.title).filter(_.nonEmpty),
        authors = Option(meta.authors).filter(_.nonEmpty),
        year = meta.year,
        venue = meta.venue,
        doi = meta.doi)),
      summary = meta.summary
    ), chunkChars, toleranceKm).map(_ => ()).left.map(_.message)

/** Where a fetched file waits for the converter. Configured (`acquire.spool-dir`); empty falls back to
  * the JVM temp directory, which is what a container gets. */
object Spool:
  /** The configured spool directory (`acquire.spool-dir`), or `<java.io.tmpdir>/histmersion-acquire`
    * when it is empty — absolute and normalised. A fetched file lives at `<base>/<citekey>/paper.<ext>`. */
  def base(dir: String): Path =
    Option(dir).map(_.trim).filter(_.nonEmpty)
      .map(Path.of(_))
      .getOrElse(Path.of(System.getProperty("java.io.tmpdir"), "histmersion-acquire"))
      .toAbsolutePath.normalize

  /** `base.resolve(citekey)` is a path built from a caller-supplied string, so it gets BOTH guards:
    * the citekey must parse ([[Citekey]]), and the resolved directory must still be under the base.
    * The second is not redundant — it holds whatever a future caller does to the first, and a
    * path-traversal guard should not rest on one regex being right. */
  def write(dir: String, citekey: String, extension: String, bytes: Array[Byte]): Either[String, Path] =
    Citekey.parse(citekey).flatMap { _ =>
      scala.util.Try {
        val base = Spool.base(dir)
        val out = base.resolve(citekey).toAbsolutePath.normalize
        if !out.startsWith(base) then
          throw IllegalArgumentException(s"'$citekey' would escape the spool directory")
        Files.createDirectories(out)
        val f = out.resolve(s"paper.${AssetLinks.safeName(extension)}")
        Files.write(f, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        f
      }.toEither.left.map(e => s"could not spool the file: ${e.getMessage}")
    }
