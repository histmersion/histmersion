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
  ) = this(_root_.com.histmersion.plugin.MediaItem.Key(key), _root_.com.histmersion.plugin.MediaItem.Title(title), _root_.com.histmersion.plugin.MediaItem.PageUrl(pageUrl), (imageUrl).map(refactorValue => _root_.com.histmersion.plugin.MediaItem.ImageUrl(refactorValue)), (rights).left.map(refactorError => _root_.com.histmersion.plugin.MediaItem.RightsPart1(refactorError)), (thumbUrl).map(refactorValue3 => _root_.com.histmersion.plugin.MediaItem.ThumbUrl(refactorValue3)), (originalUrl).map(refactorValue4 => _root_.com.histmersion.plugin.MediaItem.OriginalUrl(refactorValue4)), (geography).map(refactorValue5 => { val refactorTuple = refactorValue5; (_root_.com.histmersion.plugin.MediaItem.GeographyPart1(refactorTuple._1), _root_.com.histmersion.plugin.MediaItem.GeographyPart2(refactorTuple._2)) }), period, (ids).map(refactorValue6 => { val refactorTuple2 = refactorValue6; (_root_.com.histmersion.plugin.MediaItem.IdsPart1(refactorTuple2._1), _root_.com.histmersion.plugin.MediaItem.IdsPart2(refactorTuple2._2)) }), (withheld).map(refactorValue7 => _root_.com.histmersion.plugin.MediaItem.Withheld(refactorValue7)), None, None)

object MediaItem:
  /** The item as a plugin built against SPI 2's first release made it: no role of its own, no rendition. */
  type ApplyKey = _root_.com.histmersion.plugin.MediaItem.Key
  val ApplyKey: _root_.com.histmersion.plugin.MediaItem.Key.type = _root_.com.histmersion.plugin.MediaItem.Key
  type ApplyTitle = _root_.com.histmersion.plugin.MediaItem.Title
  val ApplyTitle: _root_.com.histmersion.plugin.MediaItem.Title.type = _root_.com.histmersion.plugin.MediaItem.Title
  type ApplyPageUrl = _root_.com.histmersion.plugin.MediaItem.PageUrl
  val ApplyPageUrl: _root_.com.histmersion.plugin.MediaItem.PageUrl.type = _root_.com.histmersion.plugin.MediaItem.PageUrl
  type ApplyImageUrl = _root_.com.histmersion.plugin.MediaItem.ImageUrl
  val ApplyImageUrl: _root_.com.histmersion.plugin.MediaItem.ImageUrl.type = _root_.com.histmersion.plugin.MediaItem.ImageUrl
  type ApplyRightsPart1 = _root_.com.histmersion.plugin.MediaItem.RightsPart1
  val ApplyRightsPart1: _root_.com.histmersion.plugin.MediaItem.RightsPart1.type = _root_.com.histmersion.plugin.MediaItem.RightsPart1
  type ApplyThumbUrl = _root_.com.histmersion.plugin.MediaItem.ThumbUrl
  val ApplyThumbUrl: _root_.com.histmersion.plugin.MediaItem.ThumbUrl.type = _root_.com.histmersion.plugin.MediaItem.ThumbUrl
  type ApplyOriginalUrl = _root_.com.histmersion.plugin.MediaItem.OriginalUrl
  val ApplyOriginalUrl: _root_.com.histmersion.plugin.MediaItem.OriginalUrl.type = _root_.com.histmersion.plugin.MediaItem.OriginalUrl
  type ApplyGeographyPart1 = _root_.com.histmersion.plugin.MediaItem.GeographyPart1
  val ApplyGeographyPart1: _root_.com.histmersion.plugin.MediaItem.GeographyPart1.type = _root_.com.histmersion.plugin.MediaItem.GeographyPart1
  type ApplyGeographyPart2 = _root_.com.histmersion.plugin.MediaItem.GeographyPart2
  val ApplyGeographyPart2: _root_.com.histmersion.plugin.MediaItem.GeographyPart2.type = _root_.com.histmersion.plugin.MediaItem.GeographyPart2
  type ApplyIdsPart1 = _root_.com.histmersion.plugin.MediaItem.IdsPart1
  val ApplyIdsPart1: _root_.com.histmersion.plugin.MediaItem.IdsPart1.type = _root_.com.histmersion.plugin.MediaItem.IdsPart1
  type ApplyIdsPart2 = _root_.com.histmersion.plugin.MediaItem.IdsPart2
  val ApplyIdsPart2: _root_.com.histmersion.plugin.MediaItem.IdsPart2.type = _root_.com.histmersion.plugin.MediaItem.IdsPart2
  type ApplyWithheld = _root_.com.histmersion.plugin.MediaItem.Withheld
  val ApplyWithheld: _root_.com.histmersion.plugin.MediaItem.Withheld.type = _root_.com.histmersion.plugin.MediaItem.Withheld

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
