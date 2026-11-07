package com.histmersion.domain.library.ingest

import com.histmersion.domain.media.assets.NewFigure

/** The folder of `wiki/sources/` notes a seed reads — one the person names, which only the composition root sees as a
  * path.
  */
trait SeedFolder:
  /** The notes' file names, in name order. */
  def notes: List[com.histmersion.domain.library.ingest.SeedFolder.NotesResult]

  /** The note `name`: `None` when it has no frontmatter. Throws what reading it threw. */
  def read(name: com.histmersion.domain.library.ingest.SeedFolder.Name): Option[SeedNote]

  /** The figures of the old file pipeline beside the note, at most `maxBytes` each. */
  def figures(name: com.histmersion.domain.library.ingest.SeedFolder.Name, citekey: com.histmersion.domain.library.Citekey, maxBytes: com.histmersion.domain.library.ingest.SeedFolder.MaxBytes): List[NewFigure]

object SeedFolder:
  type MaxBytes = MaxBytes.T
  object MaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesResult = NotesResult.T
  object NotesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
