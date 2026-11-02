package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.given

final case class BundleSiteType(placeId: String, siteType: Option[String], checkedAt: Option[String])
    derives KindlingsCodecAsObject
