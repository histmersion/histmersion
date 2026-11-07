package com.histmersion.domain.library.ingest

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.Caller
import com.histmersion.domain.library.sources.SourceService

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Source

/** The synchronous, metadata-only half of an acquisition.
  *
  * It stores an **asset-less skeleton source**: the metadata, the reference edges and `provenance = "oa"` (or whatever
  * the channel was), with no body — converting the PDF is the OCR job's. The file itself is left in the spool and where
  * it is is reported, so the conversion can be run against it later without re-downloading.
  *
  * Crucially it does NOT replace a source that already exists: a re-ingest of an enriched paper must not wipe its
  * findings, notes and tags, so an existing citekey gets a metadata-only PATCH.
  */
final class SkeletonIngest(sources: SourceService) extends IngestJobs:

  def enqueue(copy: Option[FetchedCopy], citekey: com.histmersion.domain.library.Citekey, meta: IngestMeta): Either[com.histmersion.domain.library.ingest.SkeletonIngest.Problem, IngestOutcome] =
    val exists = sources.exists(citekey)
    val result =
      if exists then patchExisting(citekey, meta)
      else create(citekey, meta)
    result.map { _ =>
      val edges = sources.addEdges(meta.edges.map(_.modify(_.src).setTo(citekey)), Caller.Local)
      val where = copy.map(c => s", file spooled at ${c.where}").getOrElse(", no file")
      IngestOutcome(
        citekey,
        created = !exists,
        jobId = None,
        detail = s"${if exists then "updated" else "created"} skeleton source" +
          s" ($edges new edge(s))$where — the body is converted by the OCR job (§1.8)"
      )
    }

  private def create(citekey: com.histmersion.domain.library.Citekey, meta: IngestMeta): Either[com.histmersion.domain.library.ingest.SkeletonIngest.Problem, Unit] =
    com.histmersion.api.library.Source.Write
      .applyPut(
        com.histmersion.api.library.Citekey(citekey),
        com.histmersion.api.library.Source.Put(
          title = com.histmersion.api.library.Title(meta.title),
          authors = (meta.authors).map(com.histmersion.api.library.AuthorName.apply),
          doi = (meta.doi).map(com.histmersion.api.library.Doi.apply),
          year = (meta.year).map(com.histmersion.api.catalogue.Year.apply),
          venue = (meta.venue).map(com.histmersion.api.library.Source.Venue.apply),
          status = Some(Source.Status.Unassessed),
          summary = meta.summary,
          citedByCount = meta.citedByCount,
          influentialCitations = meta.influentialCitations,
          provenance = Some(meta.provenance)
        )
      )
      .map { s =>
        val _ = sources.put(s, Caller.Local)
      }

  private def patchExisting(citekey: com.histmersion.domain.library.Citekey, meta: IngestMeta): Either[com.histmersion.domain.library.ingest.SkeletonIngest.Problem, Unit] =
    sources
      .patch(
        citekey,
        com.histmersion.api.library.Source.Patch(
          metadata = Some(
            MetadataPatch(
              title = (Some(meta.title).filter(_.nonEmpty)).map(com.histmersion.api.library.Title.apply),
              authors = (Option(meta.authors).filter(_.nonEmpty)).map(_.map(com.histmersion.api.library.AuthorName.apply)),
              year = (meta.year).map(com.histmersion.api.catalogue.Year.apply),
              venue = (meta.venue).map(com.histmersion.api.library.Source.Venue.apply),
              doi = (meta.doi).map(com.histmersion.api.library.Doi.apply)
            )
          ),
          summary = meta.summary
        ),
        Caller.Local
      )
      .map(_ => ())
      .left
      .map(_.message)

object SkeletonIngest:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
