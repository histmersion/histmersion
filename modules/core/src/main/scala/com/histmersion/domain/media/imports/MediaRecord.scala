package com.histmersion.domain.media.imports

import com.histmersion.domain.Attribution
import com.histmersion.Period

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
    rights: Either[MediaRecord.RightsPart1, Attribution],
    geography: List[(MediaRecord.GeographyPart1, MediaRecord.GeographyPart2)],
    period: Option[Period],
    ids: List[(MediaRecord.IdsPart1, MediaRecord.IdsPart2)] = Nil,
    withheld: Option[MediaRecord.Withheld] = None,
    role: Option[MediaRecord.Role] = None
)

object MediaRecord:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUrl = PageUrl.T
  object PageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrl = ImageUrl.T
  object ImageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbUrl = ThumbUrl.T
  object ThumbUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RightsPart1 = RightsPart1.T
  object RightsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
