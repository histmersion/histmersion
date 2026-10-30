package com.histmersion.api

/** `PUT /api/media/sources/{citekey}/cover`: a cover the CLI read from the local Calibre library (the body is the
  * image), stored as the source's cover under `provider`/`key` (the Calibre id).
  */
final case class SourceCoverParams(
    citekey: String,
    provider: String = MediaProvider.Calibre,
    key: String,
    mime: Option[String] = None
)
