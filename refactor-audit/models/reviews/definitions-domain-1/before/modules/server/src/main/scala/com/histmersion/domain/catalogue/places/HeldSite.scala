package com.histmersion.domain.catalogue.places

/** A Wikidata site as the in-memory catalogue keeps it for the refinement of its type: whether it is on the map (a
  * finding or a picture is placed there), the class its import kept, the type refined and when it was asked (epoch ms).
  */
final case class HeldSite(
    id: String,
    onMap: Boolean,
    importClass: Option[String],
    siteType: Option[String] = None,
    checkedAt: Option[Long] = None
)
