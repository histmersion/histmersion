package com.histmersion.plugins.calibre

/** One field `calibre update` changes: what Calibre has now and what it writes. */
final case class CalibreChange(field: CalibreField, from: String, to: String)
