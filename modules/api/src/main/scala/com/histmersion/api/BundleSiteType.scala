package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** What a SITE is (`places.site_type`, V085), refined from outside, and when it was last asked — for any place, the
  * gazetteer's seeds included (they are not carried; their ids are the same everywhere). An import keeps the newer
  * answer (`checkedAt`).
  */
final case class BundleSiteType(placeId: String, siteType: Option[String], checkedAt: Option[String])
    derives KindlingsCodecAsObject
