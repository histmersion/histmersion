package com.histmersion.domain.media.assets

/** The licence fields a re-read refreshes on a stored picture (the bytes stay). */
final case class Rights(license: String, licenseUrl: Option[String], author: Option[String], credit: String)
