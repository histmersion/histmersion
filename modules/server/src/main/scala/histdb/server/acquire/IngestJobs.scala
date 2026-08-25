package histdb.server.acquire

import histdb.core.*
import histdb.db.{AcquireRepo, SourceRepo}

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
  * `histdb ingest <doi>` fetches the PDF server-side and then hands the FILE to the same job path a
  * multipart upload takes: convert → body → assets. That queue is being built by another stream, so
  * this interface is the contract between us; when it lands, its implementation replaces
  * [[SkeletonIngest]] in `Main` and nothing else changes.
  */
trait IngestJobs:
  /** @param pdfPath the acquired file on the SERVER's filesystem, or `None` when no PDF was found
    *                (the source is still created from metadata, so it can be enriched and queued) */
  def enqueue(pdfPath: Option[Path], citekey: String, meta: IngestMeta): Either[String, IngestOutcome]

/** The synchronous fallback used until the job queue exists.
  *
  * It stores an **asset-less skeleton source**: the metadata, the reference edges and
  * `provenance = "oa"` (or whatever the channel was), with no body — converting the PDF is exactly
  * the part stream 2.4 owns. The file itself is left in the spool directory and its path is reported,
  * so the conversion can be run against it later without re-downloading.
  *
  * Crucially it does NOT replace a source that already exists: a re-ingest of an enriched paper must
  * not wipe its findings, notes and tags, so an existing citekey gets a metadata-only PATCH.
  */
final class SkeletonIngest(ds: DataSource, chunkChars: Int) extends IngestJobs:

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
          s" ($edges new edge(s))$where — body conversion is queued work (stream 2.4)")
    }

  private def create(citekey: String, meta: IngestMeta): Either[String, Unit] =
    Write.applyPut(citekey, SourcePut(
      title = meta.title,
      authors = meta.authors,
      doi = meta.doi,
      year = meta.year,
      venue = meta.venue,
      status = Some(SourceStatus.Unassessed.toString.toLowerCase),
      summary = meta.summary,
      citedByCount = meta.citedByCount,
      influentialCitations = meta.influentialCitations,
      provenance = Some(meta.provenance)
    )).map(s => { val _ = SourceRepo.put(ds, s, chunkChars) })

  private def patchExisting(citekey: String, meta: IngestMeta): Either[String, Unit] =
    SourceRepo.patch(ds, citekey, SourcePatch(
      metadata = Some(MetadataPatch(
        title = Some(meta.title).filter(_.nonEmpty),
        authors = Option(meta.authors).filter(_.nonEmpty),
        year = meta.year,
        venue = meta.venue,
        doi = meta.doi)),
      summary = meta.summary
    ), chunkChars).map(_ => ()).left.map(_.message)

/** Where a fetched file waits for the converter. Configured (`acquire.spool-dir`); empty falls back to
  * the JVM temp directory, which is what a container gets. */
object Spool:
  def write(dir: String, citekey: String, extension: String, bytes: Array[Byte]): Either[String, Path] =
    scala.util.Try {
      val base = Option(dir).map(_.trim).filter(_.nonEmpty)
        .map(Path.of(_))
        .getOrElse(Path.of(System.getProperty("java.io.tmpdir"), "histdb-acquire"))
      val out = base.resolve(citekey)
      Files.createDirectories(out)
      val f = out.resolve(s"paper.$extension")
      Files.write(f, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      f
    }.toEither.left.map(e => s"could not spool the file: ${e.getMessage}")
