package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** One name of a place (`place_names`). */
final case class BundlePlaceName(name: String, lang: Option[String], period: Option[BundleRange])
    derives KindlingsCodecAsObject
