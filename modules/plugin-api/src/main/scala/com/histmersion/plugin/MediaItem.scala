package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** One record of a collection: an object, a map, a photograph.
  *
  * @param key
  *   the provider's own id for it — stable, so a re-import finds it
  * @param title
  *   what the record calls it
  * @param pageUrl
  *   its page at the provider (the credit line's link)
  * @param imageUrl
  *   the rendition the host stores (the provider's own web-size copy, not the original)
  * @param thumbUrl
  *   the provider's small rendition for the card, when it has one (else the host makes one)
  * @param originalUrl
  *   the full-size original — recorded, never downloaded
  * @param rights
  *   the licence as stated, or why the record is not reusable (`Left`, e.g. "not open access")
  * @param geography
  *   the record's place fields (`find-spot`, `city`, `country` …) as (field, value), MOST SPECIFIC FIRST; values are
  *   given as written (the host skips uncertain ones)
  * @param period
  *   the object's date range, when the record gives one
  * @param ids
  *   gazetteer ids the record gives for where it is from, as (scheme, id): `("pleiades", "697725")`,
  *   `("geonames", "310858")`, `("wikidata", "Q5788")`
  * @param withheld
  *   why the record must not be shown whatever its licence (a human-remains flag, a file its provider marks non-free)
  * @param role
  *   the role its picture plays for the place or event it is found for, when the collection plays more than one
  *   ([[MediaCollection.roles]]); `None` is the collection's [[MediaCollection.role]]
  * @param rendition
  *   what the provider says about the file at `imageUrl` and its thumbnail (type, sizes), when it says it; without it
  *   the host measures the file it downloads
  */
final case class MediaItem(
    key: MediaItem.Key,
    title: MediaItem.Title,
    pageUrl: MediaItem.PageUrl,
    imageUrl: Option[MediaItem.ImageUrl],
    rights: Either[Problem, MediaRights],
    thumbUrl: Option[MediaItem.ThumbUrl] = None,
    originalUrl: Option[MediaItem.OriginalUrl] = None,
    geography: List[(MediaItem.Geography.Field, MediaItem.Geography.Value)] = Nil,
    period: Option[Period] = None,
    ids: List[(GazetteerRef.Scheme, GazetteerRef.Id)] = Nil,
    withheld: Option[MediaItem.Withheld] = None,
    role: Option[MediaItem.Role] = None,
    rendition: Option[MediaRendition] = None
)

object MediaItem:
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
