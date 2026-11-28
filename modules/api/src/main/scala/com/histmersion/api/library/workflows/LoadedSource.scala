package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class LoadedSource(
    citekey: Citekey,
    title: LoadedSource.Title,
    authors: List[AuthorName],
    year: Option[LoadedSource.Year],
    venue: Option[LoadedSource.Venue],
    status: Source.Status.Wire,
    hasSummary: LoadedSource.HasSummary,
    hasNotes: LoadedSource.HasNotes,
    hasPeriod: LoadedSource.HasPeriod,
    period: Option[Period],
    topics: List[LoadedSource.Topic],
    regions: List[LoadedSource.Region],
    existingClaims: List[LoadedSource.ExistingClaims],
    updatedAt: Option[Source.UpdatedAt],
    head: LoadedSource.Head,
    windows: List[Frame],
    tags: List[LoadedSource.Tag] = Nil,
    doi: Option[Doi] = None
) derives JsonCodec

object LoadedSource:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type HasSummary = HasSummary.T
  object HasSummary extends Newtype[Boolean, Pure]
  type HasNotes = HasNotes.T
  object HasNotes extends Newtype[Boolean, Pure]
  type HasPeriod = HasPeriod.T
  object HasPeriod extends Newtype[Boolean, Pure]
  type ExistingClaims = ExistingClaims.T
  object ExistingClaims extends Newtype[String, Pure]
  type Head = Head.T
  object Head extends Newtype[String, Pure]
  type Year = Year.T
  object Year extends Newtype[Int, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
