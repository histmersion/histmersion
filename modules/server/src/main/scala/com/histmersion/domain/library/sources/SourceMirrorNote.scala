package com.histmersion.domain.library.sources

import com.histmersion.api.ApiError
import com.histmersion.domain.library.LibraryData
import com.histmersion.util.data.mirror.{MirrorNote, MirrorRecords}

/** A source's note in the Markdown mirror: its frontmatter and its notes, as the sources' controllers render it. */
final class SourceMirrorNote(sources: SourcesControllers) extends MirrorNote:
  val records: MirrorRecords = LibraryData.SourceNotes
  def note(id: String): Either[ApiError, String] = sources.note(id)
