package com.histmersion.domain.library.ingest

import com.histmersion.domain.media.assets.NewFigure

/** The folder of `wiki/sources/` notes a seed reads — one the person names, which only the composition root sees as a
  * path.
  */
trait SeedFolder:
  /** The notes' file names, in name order. */
  def notes: List[String]

  /** The note `name`: `None` when it has no frontmatter. Throws what reading it threw. */
  def read(name: String): Option[SeedNote]

  /** The figures of the old file pipeline beside the note, at most `maxBytes` each. */
  def figures(name: String, citekey: String, maxBytes: Long): List[NewFigure]
