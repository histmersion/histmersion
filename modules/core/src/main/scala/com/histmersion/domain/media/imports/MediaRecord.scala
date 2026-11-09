package com.histmersion
package domain.media.imports

import com.histmersion.util.problem.Problem
import com.histmersion.domain.Attribution
import com.histmersion.Period
import com.histmersion.domain.catalogue.places.ExternalIds
import io.github.iltotore.iron.constraint.any.Pure

/** One object or map of an open-access collection (#1446), as its API describes it — the pure, provider-neutral shape
  * every collection parser produces and the import judges.
  *
  * @param key
  *   the provider's own id for it (`252448`, `1916.131`, `2007627513`)
  * @param pageUrl
  *   the record's page at the provider (the credit line's link)
  * @param imageUrl
  *   the rendition to store (the provider's own web-size copy, not the original)
  * @param thumbUrl
  *   the provider's small rendition for the card, when it has one (else one is made here)
  * @param originalUrl
  *   the full-size original, recorded and never downloaded
  * @param rights
  *   what the record says about reuse — read from it, never assumed — or why it cannot be used
  * @param geography
  *   the record's find-spot / place fields, MOST SPECIFIC FIRST, as (field, value)
  * @param period
  *   the object's date range, signed years (BCE negative), when the record gives one
  * @param ids
  *   gazetteer ids the record itself gives for where it is from, as (scheme, id) — `("pleiades", "697725")`,
  *   `("geonames", "310858")`, `("wikidata", "Q5788")` ([[com.histmersion.domain.catalogue.places.ExternalIds]]); a
  *   place carrying one of them is matched by it
  * @param withheld
  *   why the record is not shown whatever its licence (Open Context's human-remains flag)
  * @param role
  *   the role its picture plays, when its collection plays more than one; none is the collection's
  */
final case class MediaRecord(
    key: MediaRecord.Key,
    title: MediaRecord.Title,
    pageUrl: MediaRecord.PageUrl,
    imageUrl: Option[MediaRecord.ImageUrl],
    thumbUrl: Option[MediaRecord.ThumbUrl],
    originalUrl: Option[MediaRecord.OriginalUrl],
    rights: Either[Problem, Attribution],
    geography: List[(MediaRecord.Geography.Field, MediaRecord.Geography.Value)],
    period: Option[Period],
    ids: List[(ExternalIds.Scheme, ExternalIds.ExternalId)] = Nil,
    withheld: Option[MediaRecord.Withheld] = None,
    role: Option[MediaRecord.Role] = None
)

object MediaRecord:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type PageUrl = PageUrl.T
  object PageUrl extends Newtype[String, Pure]
  type ImageUrl = ImageUrl.T
  object ImageUrl extends Newtype[String, Pure]
  type ThumbUrl = ThumbUrl.T
  object ThumbUrl extends Newtype[String, Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends Newtype[String, Pure]
  object Geography:
    type Field = Field.T
    object Field extends Newtype[String, Pure]
    type Value = Value.T
    object Value extends Newtype[String, Pure]

  type Withheld = Withheld.T
  object Withheld extends Newtype[String, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
