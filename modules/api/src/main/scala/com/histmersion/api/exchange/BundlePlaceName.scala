package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.given

final case class BundlePlaceName(name: String, lang: Option[String], period: Option[BundleRange])
    derives KindlingsCodecAsObject
