package com.histmersion.domain.library.sources

import com.histmersion.domain.library.sources.SourceColumns.given

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.domain.library.Source

/** A source as the bundle reads it: its columns, its body's and its original's digests and its ISBNs. */
final private[sources] case class BSourceRow(
    uid: UUID,
    origin: UUID,
    citekey: com.histmersion.domain.library.Citekey,
    title: BSourceRow.Title,
    authors: List[BSourceRow.Authors],
    doi: Option[com.histmersion.domain.library.Doi],
    year: Option[com.histmersion.Year],
    venue: Option[BSourceRow.Venue],
    status: Source.Status,
    topics: List[BSourceRow.Topics],
    regions: List[BSourceRow.Regions],
    tags: List[BSourceRow.Tags],
    pNull: BSourceRow.PNull,
    pLo: Option[BSourceRow.PLo],
    pHi: Option[BSourceRow.PHi],
    summary: Option[BSourceRow.Summary],
    notes: Option[BSourceRow.Notes],
    provenance: Option[BSourceRow.Provenance],
    citedBy: Option[BSourceRow.CitedBy],
    influential: Option[BSourceRow.Influential],
    addedDate: BSourceRow.AddedDate,
    bodySha: Option[BSourceRow.BodySha],
    bodyBytes: Option[BSourceRow.BodyBytes],
    originalSha: Option[BSourceRow.OriginalSha],
    updatedAt: BSourceRow.UpdatedAt,
    isbns: List[BSourceRow.Isbns]
) derives DbCodec

object BSourceRow:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type PNull = PNull.T
  object PNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PLo = PLo.T
  object PLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PHi = PHi.T
  object PHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provenance = Provenance.T
  object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedBy = CitedBy.T
  object CitedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Influential = Influential.T
  object Influential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddedDate = AddedDate.T
  object AddedDate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodySha = BodySha.T
  object BodySha extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyBytes = BodyBytes.T
  object BodyBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalSha = OriginalSha.T
  object OriginalSha extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Isbns = Isbns.T
  object Isbns extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
