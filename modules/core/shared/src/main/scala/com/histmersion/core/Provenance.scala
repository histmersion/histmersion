package com.histmersion.core

/** How a source came in — its stored `provenance` (the `sources.source` column): free text such as
  * `oa`, `calibre`, `archive_org`, `manual`, `ocr`, or [[ExternalFile]], stored as it is written. */
object Provenance:
  /** A file the user brought in from elsewhere. */
  val ExternalFile = "external-file"
