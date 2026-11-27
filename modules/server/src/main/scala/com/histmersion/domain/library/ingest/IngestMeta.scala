package com.histmersion
package domain.library.ingest

import com.histmersion.api.library.Edge
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The metadata an ingest carries, whatever the acquisition channel was. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Title = domain.Title
  val Title: domain.Title.type = domain.Title
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
