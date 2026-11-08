package com.histmersion
package domain.library.ingest

import com.histmersion.domain.library.sources.SourceService

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.Ingest
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Doi
import com.histmersion.util.jobs.Job

/** [[IngestJobs]] backed by the OCR job queue.
  *
  * An acquisition (`histmersion ingest <doi>`, `archive import`, a Calibre push) ends with a file in the acquisition
  * spool and a pile of metadata. Both halves matter, and they have different tempos:
  *
  *   1. the metadata is written straight away, so the source exists, is searchable and carries its `references` edges
  *      even while the conversion is still running — that is exactly what [[SkeletonIngest]] already does, so it is
  *      reused rather than reimplemented;
  *   2. the file is then handed to the SAME job path an upload takes (convert → body → assets), and the caller gets the
  *      job id to poll.
  *
  * `force` is set on that job on purpose: the acquisition channel has already decided which citekey this file belongs
  * to, so the sha256 duplicate guard — which exists to catch a human uploading the same paper twice under two names —
  * must not veto it. Because the source now exists, the job PATCHES its body instead of replacing the row, so step 1's
  * metadata and any enrichment survive.
  *
  * With no file (metadata-only acquisition, or an OA fetch that found nothing) this degrades exactly to the skeleton
  * behaviour: a source to enrich and queue by hand, and no job.
  */
final class QueuedIngest(ingest: IngestControllers, sources: SourceService, spooled: FetchedCopy => Option[Upload])
    extends IngestJobs:

  private val skeleton = SkeletonIngest(sources)

  def enqueue(
      copy: Option[FetchedCopy],
      citekey: Citekey,
      meta: IngestMeta
  ): Either[IngestJobs.Problem, IngestOutcome] =
    for
      base <- skeleton.enqueue(None, citekey, meta) // metadata + edges, synchronously
      out <- copy match
        case None =>
          Right(
            base
              .modify(_.detail)
              .setTo(
                IngestOutcome.Detail(
                  s"${if base.created.value then "created" else "updated"} source from metadata — " +
                    "no file was acquired, so there is nothing to convert"
                )
              )
          )
        case Some(file) => convert(file, citekey, meta, base)
    yield out

  private def convert(
      file: FetchedCopy,
      citekey: Citekey,
      meta: IngestMeta,
      base: IngestOutcome
  ): Either[IngestJobs.Problem, IngestOutcome] =
    // a copy gone from the spool reads as an empty upload, which the ingest refuses
    val upload =
      spooled(file).getOrElse(Upload.inline(Upload.Name(file.key.name.value), Upload.Bytes(Array.emptyByteArray)))
    ingest
      .ingest(
        Ingest.Params(
          citekey = com.histmersion.api.library.Citekey(citekey.value),
          filename = Some(Ingest.Params.Filename(file.key.name.value)),
          force = Ingest.Params.Force(true), // the channel chose the citekey; the duplicate guard is for human uploads
          title = Some(meta.title).filter(_.value.nonEmpty).map(t => Title(t.value)),
          authors = meta.authors.map(a => AuthorName(a.value)),
          year = meta.year.map(y => Year(y.value.toLong)),
          doi = meta.doi.map(d => Doi(d.value))
        ),
        upload
      )
      .left
      .map(e => IngestJobs.Problem(e.message.value))
      .map(a =>
        base
          .modify(_.jobId)
          .setTo(Some(Job.Id.assume(a.jobId.value)))
          .modify(_.detail)
          .setTo(
            IngestOutcome.Detail(
              s"${if base.created.value then "created" else "updated"} source from metadata; " +
                s"queued OCR job ${a.jobId} for ${file.where} — poll `job_status`"
            )
          )
      )
