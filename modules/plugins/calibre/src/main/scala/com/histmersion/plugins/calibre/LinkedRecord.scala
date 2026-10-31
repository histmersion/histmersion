package com.histmersion.plugins.calibre


/** A source linked to a Calibre entry, with what the corpus would send there. */
final case class LinkedRecord(citekey: String, calibreId: Int, meta: CalibrePush)
