package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class SourcePut(
    title: String,
    authors: List[String] = Nil,
    doi: Option[String] = None,
    year: Option[Int] = None,
    venue: Option[String] = None,
    status: Option[com.histmersion.domain.library.Source.Status] = None,
    topics: List[String] = Nil,
    regions: List[String] = Nil,
    tags: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    summary: Option[String] = None,
    body: Option[String] = None,
    notes: Option[String] = None,
    citedByCount: Option[Int] = None,
    influentialCitations: Option[Int] = None,
    provenance: Option[String] = None,
    calibreId: Option[String] = None,
    findings: List[FindingInput] = Nil,
    edges: List[EdgeInput] = Nil
) derives KindlingsSchema
object SourcePut:
  given Schema[SourcePut] = summon[KindlingsSchema[SourcePut]].schema

  given Codec.AsObject[SourcePut] = StrictJson.codec(KindlingsCodecAsObject.derived[SourcePut])

  def of(s: Source): SourcePut = SourcePut(
    title = s.title,
    authors = s.authors,
    doi = s.doi,
    year = s.year,
    venue = s.venue,
    status = Some(s.status),
    topics = s.topics,
    regions = s.regions,
    tags = s.tags,
    periodStart = s.period.map(_.start),
    periodEnd = s.period.map(_.end),
    summary = s.summary,
    body = s.ocrMarkdown,
    notes = s.notes,
    citedByCount = s.citedByCount,
    influentialCitations = s.influentialCitations,
    provenance = s.provenance,
    calibreId = s.calibreId,
    findings = s.findings.map(FindingInput.of),
    edges = s.edges.flatMap(e => com.histmersion.domain.library.Edge.Kind.fromWire(e.kind).map(EdgeInput(e.dst, _)))
  )
