package com.histmersion
package domain.library.ingest
import com.histmersion.util.problem.Problem

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.Caller
import com.histmersion.domain.library.sources.SourceService

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year

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

  private def apiKey(citekey: Citekey): com.histmersion.api.library.Citekey =
    com.histmersion.api.library.Citekey(citekey.value)

  def enqueue(
      copy: Option[FetchedCopy],
      citekey: Citekey,
      meta: IngestMeta
  ): Either[Problem, IngestOutcome] =
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

  private def create(citekey: Citekey, meta: IngestMeta): Either[Problem, Unit] =
    Source.Write
      .applyPut(
        apiKey(citekey),
        Source.Put(
          title = Title(meta.title.value),
          authors = meta.authors.map(a => AuthorName(a.value)),
          doi = meta.doi.map(d => Doi(d.value)),
          year = meta.year.map(y => Year(y.value.toLong)),
          venue = meta.venue.map(v => Source.Venue(v.value)),
          status = Some(Source.Status.Unassessed),
          summary = meta.summary.map(x => Source.Summary(x.value)),
          citedByCount = meta.citedByCount.map(c => Source.CitedByCount(c.value)),
          influentialCitations = meta.influentialCitations.map(c => Source.InfluentialCitations(c.value)),
          provenance = Some(Source.Provenance(meta.provenance.value))
        )
      )
      .map { s =>
        val _ = sources.put(s, Caller.Local)
      }

  private def patchExisting(citekey: Citekey, meta: IngestMeta): Either[Problem, Unit] =
    sources
      .patch(
        citekey,
        Source.Patch(
          metadata = Some(
            MetadataPatch(
              title = Some(meta.title).filter(_.value.nonEmpty).map(t => Title(t.value)),
              authors = Option(meta.authors).filter(_.nonEmpty).map(_.map(a => AuthorName(a.value))),
              year = meta.year.map(y => Year(y.value.toLong)),
              venue = meta.venue.map(v => Source.Venue(v.value)),
              doi = meta.doi.map(d => Doi(d.value))
            )
          ),
          summary = meta.summary.map(x => Source.Summary(x.value))
        ),
        Caller.Local
      )
      .map(_ => ())
      .left
      .map(e => e.problems.head)
