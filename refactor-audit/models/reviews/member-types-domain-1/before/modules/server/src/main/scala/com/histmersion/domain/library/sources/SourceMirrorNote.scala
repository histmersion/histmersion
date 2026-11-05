package com.histmersion.domain.library.sources

import com.histmersion.domain.library.LibraryData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror

/** A source's note in the Markdown mirror: its frontmatter and its notes, as the sources' controllers render it. */
final class SourceMirrorNote(sources: SourcesControllers) extends Mirror.Note:
  val records: Mirror.Records = LibraryData.SourceNotes
  def note(id: String): Either[Api.Error, String] = sources.note(id)
