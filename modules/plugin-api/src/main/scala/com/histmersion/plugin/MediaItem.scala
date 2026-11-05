package com.histmersion.plugin

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
    rights: Either[MediaItem.RightsPart1, MediaRights],
    thumbUrl: Option[MediaItem.ThumbUrl] = None,
    originalUrl: Option[MediaItem.OriginalUrl] = None,
    geography: List[(MediaItem.GeographyPart1, MediaItem.GeographyPart2)] = Nil,
    period: Option[Period] = None,
    ids: List[(MediaItem.IdsPart1, MediaItem.IdsPart2)] = Nil,
    withheld: Option[MediaItem.Withheld] = None,
    role: Option[MediaItem.Role] = None,
    rendition: Option[MediaRendition] = None
):
  /** The item as a plugin built against SPI 2's first release made it: no role of its own, no rendition. */
  def this(
      key: String,
      title: String,
      pageUrl: String,
      imageUrl: Option[String],
      rights: Either[String, MediaRights],
      thumbUrl: Option[String],
      originalUrl: Option[String],
      geography: List[(String, String)],
      period: Option[Period],
      ids: List[(String, String)],
      withheld: Option[String]
  ) = this(key, title, pageUrl, imageUrl, rights, thumbUrl, originalUrl, geography, period, ids, withheld, None, None)

object MediaItem:
  /** The item as a plugin built against SPI 2's first release made it: no role of its own, no rendition. */
  type ApplyKey = ApplyKey.T
  object ApplyKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyTitle = ApplyTitle.T
  object ApplyTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPageUrl = ApplyPageUrl.T
  object ApplyPageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyImageUrl = ApplyImageUrl.T
  object ApplyImageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyRightsPart1 = ApplyRightsPart1.T
  object ApplyRightsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyThumbUrl = ApplyThumbUrl.T
  object ApplyThumbUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyOriginalUrl = ApplyOriginalUrl.T
  object ApplyOriginalUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyGeographyPart1 = ApplyGeographyPart1.T
  object ApplyGeographyPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyGeographyPart2 = ApplyGeographyPart2.T
  object ApplyGeographyPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyIdsPart1 = ApplyIdsPart1.T
  object ApplyIdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyIdsPart2 = ApplyIdsPart2.T
  object ApplyIdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyWithheld = ApplyWithheld.T
  object ApplyWithheld extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUrl = PageUrl.T
  object PageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrl = ImageUrl.T
  object ImageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RightsPart1 = RightsPart1.T
  object RightsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbUrl = ThumbUrl.T
  object ThumbUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeographyPart1 = GeographyPart1.T
  object GeographyPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeographyPart2 = GeographyPart2.T
  object GeographyPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsPart1 = IdsPart1.T
  object IdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsPart2 = IdsPart2.T
  object IdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Withheld = Withheld.T
  object Withheld extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(
      key: com.histmersion.plugin.MediaItem.ApplyKey,
      title: com.histmersion.plugin.MediaItem.ApplyTitle,
      pageUrl: com.histmersion.plugin.MediaItem.ApplyPageUrl,
      imageUrl: Option[com.histmersion.plugin.MediaItem.ApplyImageUrl],
      rights: Either[com.histmersion.plugin.MediaItem.ApplyRightsPart1, MediaRights],
      thumbUrl: Option[com.histmersion.plugin.MediaItem.ApplyThumbUrl],
      originalUrl: Option[com.histmersion.plugin.MediaItem.ApplyOriginalUrl],
      geography: List[(com.histmersion.plugin.MediaItem.ApplyGeographyPart1, com.histmersion.plugin.MediaItem.ApplyGeographyPart2)],
      period: Option[Period],
      ids: List[(com.histmersion.plugin.MediaItem.ApplyIdsPart1, com.histmersion.plugin.MediaItem.ApplyIdsPart2)],
      withheld: Option[com.histmersion.plugin.MediaItem.ApplyWithheld]
  ): MediaItem =
    new MediaItem(key, title, pageUrl, imageUrl, rights, thumbUrl, originalUrl, geography, period, ids, withheld)
