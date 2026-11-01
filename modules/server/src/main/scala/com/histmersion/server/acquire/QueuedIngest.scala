package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.server.IngestHandlers

import java.nio.file.Path
import javax.sql.DataSource

/** [[IngestJobs]] backed by stream 2.4's OCR job queue — the implementation the seam was waiting for.
  *
  * An acquisition (`histmersion ingest <doi>`, `archive import`, a Calibre push) ends with a PDF on the SERVER's
  * filesystem and a pile of metadata. Both halves matter, and they have different tempos:
  *
  *   1. the metadata is written straight away, so the source exists, is searchable and carries its `references` edges
  *      even while the conversion is still running — that is exactly what [[SkeletonIngest]] already does, so it is
  *      reused rather than reimplemented;
  *   2. the PDF is then handed to the SAME job path a multipart upload takes (convert → body → assets), and the caller
  *      gets the job id to poll.
  *
  * `force` is set on that job on purpose: the acquisition channel has already decided which citekey this file belongs
  * to, so the sha256 duplicate guard — which exists to catch a human uploading the same paper twice under two names —
  * must not veto it. Because the source now exists, the job PATCHES its body instead of replacing the row, so step 1's
  * metadata and any enrichment survive.
  *
  * With no PDF (metadata-only acquisition, or an OA fetch that found nothing) this degrades exactly to the skeleton
  * behaviour: a source to enrich and queue by hand, and no job.
  */
final class QueuedIngest(
    ingest: IngestHandlers,
    ds: DataSource,
    chunkChars: Int,
    toleranceKm: Double = com.histmersion.domain.catalogue.places.FindingPlacement.DefaultToleranceKm
) extends IngestJobs:

  private val skeleton = SkeletonIngest(ds, chunkChars, toleranceKm)

  def enqueue(pdfPath: Option[Path], citekey: String, meta: IngestMeta): Either[String, IngestOutcome] =
    for
      base <- skeleton.enqueue(None, citekey, meta) // metadata + edges, synchronously
      out <- pdfPath match
        case None =>
          Right(
            base.copy(detail =
              s"${if base.created then "created" else "updated"} source from metadata — " +
                "no file was acquired, so there is nothing to convert"
            )
          )
        case Some(file) => convert(file, citekey, meta, base)
    yield out

  private def convert(
      file: Path,
      citekey: String,
      meta: IngestMeta,
      base: IngestOutcome
  ): Either[String, IngestOutcome] =
    ingest
      .ingest(
        IngestParams(
          citekey = citekey,
          filename = Some(file.getFileName.toString),
          force = true, // the channel chose the citekey; the duplicate guard is for human uploads
          title = Some(meta.title).filter(_.nonEmpty),
          authors = meta.authors,
          year = meta.year,
          doi = meta.doi
        ),
        file.toFile
      )
      .left
      .map(_.message)
      .map(a =>
        base.copy(
          jobId = Some(a.jobId),
          detail = s"${if base.created then "created" else "updated"} source from metadata; " +
            s"queued OCR job ${a.jobId} for $file — poll `job_status`"
        )
      )
