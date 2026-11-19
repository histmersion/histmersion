package com.histmersion
package domain.library.sources

import com.histmersion.domain.library.sources.SourceColumns.given

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.domain.library.Source
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime

/** A source as the bundle reads it: its columns, its body's and its original's digests and its ISBNs. */
final private[sources] case class BSourceRow(
    uid: UUID,
    origin: UUID,
    citekey: Citekey,
    title: BSourceRow.Title,
    authors: List[BSourceRow.Authors],
    doi: Option[BSourceRow.Doi],
    year: Option[Year],
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
  type Doi = Doi.T
  object Doi extends Newtype[String, Pure]
  type Title = com.histmersion.domain.Title
  val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type Topics = Topics.T
  object Topics extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type PNull = PNull.T
  object PNull extends Newtype[Boolean, Pure]
  export DeepTime.{SignedYear as PHi, SignedYear as PLo}
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]
  type Provenance = Provenance.T
  object Provenance extends Newtype[String, Pure]
  type CitedBy = CitedBy.T
  object CitedBy extends Newtype[Int, Pure]
  type Influential = Influential.T
  object Influential extends Newtype[Int, Pure]
  type AddedDate = AddedDate.T
  object AddedDate extends Newtype[String, Pure]
  type BodySha = Sha256
  val BodySha: Sha256.type = Sha256
  type BodyBytes = ByteCount
  val BodyBytes: ByteCount.type = ByteCount
  type OriginalSha = Sha256
  val OriginalSha: Sha256.type = Sha256
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type Isbns = Isbns.T
  object Isbns extends Newtype[String, Pure]
