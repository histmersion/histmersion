package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
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
  ) = this(MediaItem.Key(key), MediaItem.Title(title), MediaItem.PageUrl(pageUrl), (imageUrl).map(refactorValue => MediaItem.ImageUrl(refactorValue)), (rights).left.map(refactorError => MediaItem.RightsPart1(refactorError)), (thumbUrl).map(refactorValue3 => MediaItem.ThumbUrl(refactorValue3)), (originalUrl).map(refactorValue4 => MediaItem.OriginalUrl(refactorValue4)), (geography).map(refactorValue5 => { val refactorTuple = refactorValue5; (MediaItem.GeographyPart1(refactorTuple._1), MediaItem.GeographyPart2(refactorTuple._2)) }), period, (ids).map(refactorValue6 => { val refactorTuple2 = refactorValue6; (MediaItem.IdsPart1(refactorTuple2._1), MediaItem.IdsPart2(refactorTuple2._2)) }), (withheld).map(refactorValue7 => MediaItem.Withheld(refactorValue7)), None, None)

object MediaItem:
  /** The item as a plugin built against SPI 2's first release made it: no role of its own, no rendition. */
  type ApplyKey = Key
  val ApplyKey: Key.type = Key
  type ApplyTitle = Title
  val ApplyTitle: Title.type = Title
  type ApplyPageUrl = PageUrl
  val ApplyPageUrl: PageUrl.type = PageUrl
  type ApplyImageUrl = ImageUrl
  val ApplyImageUrl: ImageUrl.type = ImageUrl
  type ApplyRightsPart1 = RightsPart1
  val ApplyRightsPart1: RightsPart1.type = RightsPart1
  type ApplyThumbUrl = ThumbUrl
  val ApplyThumbUrl: ThumbUrl.type = ThumbUrl
  type ApplyOriginalUrl = OriginalUrl
  val ApplyOriginalUrl: OriginalUrl.type = OriginalUrl
  type ApplyGeographyPart1 = GeographyPart1
  val ApplyGeographyPart1: GeographyPart1.type = GeographyPart1
  type ApplyGeographyPart2 = GeographyPart2
  val ApplyGeographyPart2: GeographyPart2.type = GeographyPart2
  type ApplyIdsPart1 = IdsPart1
  val ApplyIdsPart1: IdsPart1.type = IdsPart1
  type ApplyIdsPart2 = IdsPart2
  val ApplyIdsPart2: IdsPart2.type = IdsPart2
  type ApplyWithheld = Withheld
  val ApplyWithheld: Withheld.type = Withheld

  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type PageUrl = PageUrl.T
  object PageUrl extends RefinedType[String, Pure]
  type ImageUrl = ImageUrl.T
  object ImageUrl extends RefinedType[String, Pure]
  type RightsPart1 = RightsPart1.T
  object RightsPart1 extends RefinedType[String, Pure]
  type ThumbUrl = ThumbUrl.T
  object ThumbUrl extends RefinedType[String, Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends RefinedType[String, Pure]
  type GeographyPart1 = GeographyPart1.T
  object GeographyPart1 extends RefinedType[String, Pure]
  type GeographyPart2 = GeographyPart2.T
  object GeographyPart2 extends RefinedType[String, Pure]
  type IdsPart1 = IdsPart1.T
  object IdsPart1 extends RefinedType[String, Pure]
  type IdsPart2 = IdsPart2.T
  object IdsPart2 extends RefinedType[String, Pure]
  type Withheld = Withheld.T
  object Withheld extends RefinedType[String, Pure]
  type Role = Role.T
  object Role extends RefinedType[String, Pure]

  def apply(
      key: ApplyKey,
      title: ApplyTitle,
      pageUrl: ApplyPageUrl,
      imageUrl: Option[ApplyImageUrl],
      rights: Either[ApplyRightsPart1, MediaRights],
      thumbUrl: Option[ApplyThumbUrl],
      originalUrl: Option[ApplyOriginalUrl],
      geography: List[(ApplyGeographyPart1, ApplyGeographyPart2)],
      period: Option[Period],
      ids: List[(ApplyIdsPart1, ApplyIdsPart2)],
      withheld: Option[ApplyWithheld]
  ): MediaItem =
    new MediaItem(key, title, pageUrl, imageUrl, rights, thumbUrl, originalUrl, geography, period, ids, withheld)
