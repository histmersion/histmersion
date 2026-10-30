package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

final case class BundlePlaceName(name: String, lang: Option[String], period: Option[BundleRange])
    derives KindlingsCodecAsObject
