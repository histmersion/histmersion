package com.histmersion.domain.library.ingest

import com.histmersion.api.Source

/** A note a seed reads: the source its frontmatter and body describe, and when its file last changed. */
final case class SeedNote(source: Source, modifiedMillis: Long)
