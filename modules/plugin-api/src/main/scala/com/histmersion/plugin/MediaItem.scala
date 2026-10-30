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
  *   why the record must not be shown whatever its licence (a human-remains flag)
  */
final case class MediaItem(
    key: String,
    title: String,
    pageUrl: String,
    imageUrl: Option[String],
    rights: Either[String, MediaRights],
    thumbUrl: Option[String] = None,
    originalUrl: Option[String] = None,
    geography: List[(String, String)] = Nil,
    period: Option[Period] = None,
    ids: List[(String, String)] = Nil,
    withheld: Option[String] = None
)
