package com.histmersion.domain.library.ingest

import com.histmersion.api.library.Source

/** A note a seed reads: the source its frontmatter and body describe, and when its file last changed. */
final case class SeedNote(source: Source, modifiedMillis: SeedNote.ModifiedMillis)

object SeedNote:
  type ModifiedMillis = ModifiedMillis.T
  object ModifiedMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
