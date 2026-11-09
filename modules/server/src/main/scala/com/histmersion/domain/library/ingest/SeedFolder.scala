package com.histmersion
package domain.library.ingest

import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** The folder of `wiki/sources/` notes a seed reads — one the person names, which only the composition root sees as a
  * path.
  */
trait SeedFolder:
  /** The notes' file names, in name order. */
  def notes: List[SeedFolder.Note]

  /** The note `name`: `None` when it has no frontmatter. Throws what reading it threw. */
  def read(name: SeedFolder.Name): Option[SeedNote]

  /** The figures of the old file pipeline beside the note, at most `maxBytes` each. */
  def figures(name: SeedFolder.Name, citekey: Citekey, maxBytes: SeedFolder.MaxBytes): List[NewFigure]

object SeedFolder:
  type MaxBytes = MaxBytes.T
  object MaxBytes extends Newtype[Long, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
