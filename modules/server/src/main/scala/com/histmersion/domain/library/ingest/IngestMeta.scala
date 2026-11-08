package com.histmersion
package domain.library.ingest

import com.histmersion.api.library.Edge
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure

/** The metadata an ingest carries, whatever the acquisition channel was. */
final case class IngestMeta(
    title: IngestMeta.Title,
    authors: List[IngestMeta.Authors] = Nil,
    doi: Option[Doi] = None,
    year: Option[Year] = None,
    venue: Option[IngestMeta.Venue] = None,
    summary: Option[IngestMeta.Summary] = None,
    provenance: IngestMeta.Provenance = IngestMeta.Provenance("oa"),
    /** `references` (and other typed) edges discovered while acquiring. */
    edges: List[Edge] = Nil,
    citedByCount: Option[IngestMeta.CitedByCount] = None,
    influentialCitations: Option[IngestMeta.InfluentialCitations] = None
)

object IngestMeta:
  type Title = com.histmersion.domain.Title
  val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Provenance = Provenance.T
  object Provenance extends Newtype[String, Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends Newtype[Int, Pure]
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends Newtype[Int, Pure]
