package com.histmersion.api.media
import com.histmersion.api.given
final case class SourceCoverParams(
    citekey: String,
    provider: String = MediaProvider.Calibre,
    key: String,
    mime: Option[String] = None
)
