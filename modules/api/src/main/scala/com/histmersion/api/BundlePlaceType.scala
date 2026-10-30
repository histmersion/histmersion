package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

final case class BundlePlaceType(typeLabel: String, period: Option[BundleRange], citekey: Option[String])
    derives KindlingsCodecAsObject
