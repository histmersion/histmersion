package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

final case class BundleSiteType(placeId: String, siteType: Option[String], checkedAt: Option[String])
    derives KindlingsCodecAsObject
