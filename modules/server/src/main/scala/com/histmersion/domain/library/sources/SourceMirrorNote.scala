package com.histmersion.domain.library.sources

import com.histmersion.domain.library.LibraryData
import com.histmersion.util.data.mirror.{MirrorNote, MirrorRecords}
import com.histmersion.api.Api

/** A source's note in the Markdown mirror: its frontmatter and its notes, as the sources' controllers render it. */
final class SourceMirrorNote(sources: SourcesControllers) extends MirrorNote:
  val records: MirrorRecords = LibraryData.SourceNotes
  def note(id: String): Either[Api.Error, String] = sources.note(id)
