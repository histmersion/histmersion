package com.histmersion
package domain.library.ingest

import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A note a seed reads: the source its frontmatter and body describe, and when its file last changed. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class SeedNote(source: Source, modifiedMillis: SeedNote.ModifiedMillis)

object SeedNote:
  type ModifiedMillis = ModifiedMillis.T
  object ModifiedMillis extends Newtype[Long, Pure]
