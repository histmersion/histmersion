package com.histmersion.domain.library.ingest

import com.histmersion.api.library.Edge

/** The metadata an ingest carries, whatever the acquisition channel was. */
final case class IngestMeta(
    title: IngestMeta.Title,
    authors: List[IngestMeta.Authors] = Nil,
    doi: Option[com.histmersion.domain.library.Doi] = None,
    year: Option[com.histmersion.Year] = None,
    venue: Option[IngestMeta.Venue] = None,
    summary: Option[IngestMeta.Summary] = None,
    provenance: IngestMeta.Provenance = "oa",
    /** `references` (and other typed) edges discovered while acquiring. */
    edges: List[Edge] = Nil,
    citedByCount: Option[IngestMeta.CitedByCount] = None,
    influentialCitations: Option[IngestMeta.InfluentialCitations] = None
)

object IngestMeta:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provenance = Provenance.T
  object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
