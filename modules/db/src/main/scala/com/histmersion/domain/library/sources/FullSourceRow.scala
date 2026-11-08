package com.histmersion
package domain.library.sources

import com.histmersion.domain.library.sources.SourceColumns.given

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A source's row as a write path reads it back (Magnum maps a SELECT's columns positionally). */
final private[sources] case class FullSourceRow(
    citekey: Citekey,
    title: FullSourceRow.Title,
    authors: List[FullSourceRow.Authors],
    doi: Option[FullSourceRow.Doi],
    year: Option[Year],
    venue: Option[FullSourceRow.Venue],
    status: Source.Status,
    topics: List[FullSourceRow.Topics],
    regions: List[FullSourceRow.Regions],
    tags: List[FullSourceRow.Tags],
    periodStart: Option[Year],
    periodEnd: Option[Year],
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
  type Doi = Doi.T
  object Doi extends RefinedType[String, Pure]
  type Title = com.histmersion.domain.Title
  val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
  type Authors = Authors.T
  object Authors extends RefinedType[String, Pure]
  type Venue = Venue.T
  object Venue extends RefinedType[String, Pure]
  type Topics = Topics.T
  object Topics extends RefinedType[String, Pure]
  type Regions = Regions.T
  object Regions extends RefinedType[String, Pure]
  type Tags = Tags.T
  object Tags extends RefinedType[String, Pure]
  type Summary = Summary.T
  object Summary extends RefinedType[String, Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends RefinedType[Int, Pure]
  type InfluentialCitations = InfluentialCitations.T
  object InfluentialCitations extends RefinedType[Int, Pure]
  type Provenance = Provenance.T
  object Provenance extends RefinedType[String, Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends RefinedType[String, Pure]
  type OcrMarkdown = OcrMarkdown.T
  object OcrMarkdown extends RefinedType[String, Pure]
  type Notes = Notes.T
  object Notes extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
