package com.histmersion
package domain.library.ingest

import com.histmersion.api.library.Source
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A note a seed reads: the source its frontmatter and body describe, and when its file last changed. */
final case class SeedNote(source: Source, modifiedMillis: SeedNote.ModifiedMillis)

object SeedNote:
  type ModifiedMillis = ModifiedMillis.T
  object ModifiedMillis extends RefinedType[Long, Pure]
