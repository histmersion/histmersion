package com.histmersion.api

final case class SourceCoverParams(
    citekey: String,
    provider: String = MediaProvider.Calibre,
    key: String,
    mime: Option[String] = None
)
