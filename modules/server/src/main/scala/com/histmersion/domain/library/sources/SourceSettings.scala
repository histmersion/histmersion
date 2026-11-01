package com.histmersion.domain.library.sources

/** What a source's write follows: the size of its full-text chunks, and the tolerance its findings are placed with. */
final case class SourceSettings(chunkChars: Int, toleranceKm: Double)
