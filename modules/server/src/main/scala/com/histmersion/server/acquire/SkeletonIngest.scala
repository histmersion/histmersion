package com.histmersion.server.acquire

import com.histmersion.values.SourceStatus

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{AcquireRepo, SourceRepo}

import java.nio.file.Path
import javax.sql.DataSource

/** The synchronous, metadata-only half of an acquisition.
  *
  * It stores an **asset-less skeleton source**: the metadata, the reference edges and `provenance = "oa"` (or whatever
  * the channel was), with no body — converting the PDF is exactly the part stream 2.4 owns. The file itself is left in
  * the spool directory and its path is reported, so the conversion can be run against it later without re-downloading.
  *
  * Crucially it does NOT replace a source that already exists: a re-ingest of an enriched paper must not wipe its
  * findings, notes and tags, so an existing citekey gets a metadata-only PATCH.
  */
final class SkeletonIngest(
    ds: DataSource,
    chunkChars: Int,
    toleranceKm: Double = com.histmersion.domain.catalogue.places.FindingPlacement.DefaultToleranceKm
) extends IngestJobs:

  def enqueue(pdfPath: Option[Path], citekey: String, meta: IngestMeta): Either[String, IngestOutcome] =
    val exists = AcquireRepo.exists(ds, citekey)
    val result =
      if exists then patchExisting(citekey, meta)
      else create(citekey, meta)
    result.map { _ =>
      val edges = AcquireRepo.addEdges(ds, meta.edges.map(_.copy(src = citekey)))
      val where = pdfPath.map(p => s", file spooled at $p").getOrElse(", no file")
      IngestOutcome(
        citekey,
        created = !exists,
        jobId = None,
        detail = s"${if exists then "updated" else "created"} skeleton source" +
          s" ($edges new edge(s))$where — the body is converted by the OCR job (§1.8)"
      )
    }

  private def create(citekey: String, meta: IngestMeta): Either[String, Unit] =
    Write
      .applyPut(
        citekey,
        SourcePut(
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
        )
      )
      .map { s =>
        val _ = SourceRepo.put(ds, s, chunkChars, toleranceKm)
      }

  private def patchExisting(citekey: String, meta: IngestMeta): Either[String, Unit] =
    SourceRepo
      .patch(
        ds,
        citekey,
        SourcePatch(
          metadata = Some(
            MetadataPatch(
              title = Some(meta.title).filter(_.nonEmpty),
              authors = Option(meta.authors).filter(_.nonEmpty),
              year = meta.year,
              venue = meta.venue,
              doi = meta.doi
            )
          ),
          summary = meta.summary
        ),
        chunkChars,
        toleranceKm
      )
      .map(_ => ())
      .left
      .map(_.message)
