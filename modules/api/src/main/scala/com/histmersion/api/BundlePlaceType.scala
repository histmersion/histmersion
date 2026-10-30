package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** A dated type of a place (`place_types`). */
final case class BundlePlaceType(typeLabel: String, period: Option[BundleRange], citekey: Option[String])
    derives KindlingsCodecAsObject
