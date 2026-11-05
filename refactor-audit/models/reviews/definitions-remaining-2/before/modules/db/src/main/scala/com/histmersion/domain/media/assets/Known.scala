package com.histmersion.domain.media.assets

/** A stored picture of a provider, by its key: its uid and the licence it was stored under. */
final case class Known(uid: String, license: Option[String])
