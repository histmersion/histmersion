package com.histmersion.domain.library.sources

import com.histmersion.domain.library.sources.SourceColumns.given

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source
import com.histmersion.util.database.NewtypeColumns.given

/** A source's row as a write path reads it back (Magnum maps a SELECT's columns positionally). */
final private[sources] case class FullSourceRow(
    citekey: com.histmersion.domain.library.Citekey,
    title: FullSourceRow.Title,
    authors: List[FullSourceRow.Authors],
    doi: Option[com.histmersion.domain.library.Doi],
    year: Option[com.histmersion.Year],
    venue: Option[FullSourceRow.Venue],
    status: Source.Status,
    topics: List[FullSourceRow.Topics],
    regions: List[FullSourceRow.Regions],
    tags: List[FullSourceRow.Tags],
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    summary: Option[FullSourceRow.Summary],
    citedByCount: Option[FullSourceRow.CitedByCount],
    influentialCitations: Option[FullSourceRow.InfluentialCitations],
    provenance: Option[FullSourceRow.Provenance],
    calibreId: Option[FullSourceRow.CalibreId],
    ocrMarkdown: Option[FullSourceRow.OcrMarkdown],
    notes: Option[FullSourceRow.Notes],
    updatedAt: FullSourceRow.UpdatedAt
) derives DbCodec

object FullSourceRow:
  type Title = com.histmersion.domain.Title
  val Title: _root_.com.histmersion.domain.Title.type = _root_.com.histmersion.domain.Title
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provenance = Provenance.T
  object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OcrMarkdown = OcrMarkdown.T
  object OcrMarkdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
