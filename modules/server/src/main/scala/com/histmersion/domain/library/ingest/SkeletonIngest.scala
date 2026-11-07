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

  private def apiKey(citekey: com.histmersion.domain.library.Citekey): com.histmersion.api.library.Citekey =
    com.histmersion.api.library.Citekey(citekey.value)

  def enqueue(copy: Option[FetchedCopy], citekey: com.histmersion.domain.library.Citekey, meta: IngestMeta): Either[IngestJobs.Problem, IngestOutcome] =
    val exists = sources.exists(citekey)
    val result =
      if exists then patchExisting(citekey, meta)
      else create(citekey, meta)
    result.map { _ =>
      val edges = sources.addEdges(meta.edges.map(_.modify(_.src).setTo(apiKey(citekey))), Caller.Local)
      val where = copy.map(c => s", file spooled at ${c.where}").getOrElse(", no file")
      IngestOutcome(
        citekey,
        created = IngestOutcome.Created(!exists),
        jobId = None,
        detail = IngestOutcome.Detail(
          s"${if exists then "updated" else "created"} skeleton source" +
            s" ($edges new edge(s))$where — the body is converted by the OCR job (§1.8)"
        )
      )
    }

  private def create(citekey: com.histmersion.domain.library.Citekey, meta: IngestMeta): Either[IngestJobs.Problem, Unit] =
    com.histmersion.api.library.Source.Write
      .applyPut(
        apiKey(citekey),
        com.histmersion.api.library.Source.Put(
          title = com.histmersion.api.library.Title(meta.title.value),
          authors = meta.authors.map(a => com.histmersion.api.library.AuthorName(a.value)),
          doi = meta.doi.map(d => com.histmersion.api.library.Doi(d.value)),
          year = meta.year.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
          venue = meta.venue.map(v => com.histmersion.api.library.Source.Venue(v.value)),
          status = Some(Source.Status.Unassessed),
          summary = meta.summary.map(x => Source.Summary(x.value)),
          citedByCount = meta.citedByCount.map(c => Source.CitedByCount(c.value)),
          influentialCitations = meta.influentialCitations.map(c => Source.InfluentialCitations(c.value)),
          provenance = Some(Source.Provenance(meta.provenance.value))
        )
      )
      .left
      .map(p => IngestJobs.Problem(p.value))
      .map { s =>
        val _ = sources.put(s, Caller.Local)
      }

  private def patchExisting(citekey: com.histmersion.domain.library.Citekey, meta: IngestMeta): Either[IngestJobs.Problem, Unit] =
    sources
      .patch(
        citekey,
        com.histmersion.api.library.Source.Patch(
          metadata = Some(
            MetadataPatch(
              title = Some(meta.title).filter(_.value.nonEmpty).map(t => com.histmersion.api.library.Title(t.value)),
              authors = Option(meta.authors).filter(_.nonEmpty).map(_.map(a => com.histmersion.api.library.AuthorName(a.value))),
              year = meta.year.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
              venue = meta.venue.map(v => com.histmersion.api.library.Source.Venue(v.value)),
              doi = meta.doi.map(d => com.histmersion.api.library.Doi(d.value))
            )
          ),
          summary = meta.summary.map(x => Source.Summary(x.value))
        ),
        Caller.Local
      )
      .map(_ => ())
      .left
      .map(e => IngestJobs.Problem(e.message.value))
