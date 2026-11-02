package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.given

final case class BundlePlaceType(typeLabel: String, period: Option[BundleRange], citekey: Option[String])
    derives KindlingsCodecAsObject
