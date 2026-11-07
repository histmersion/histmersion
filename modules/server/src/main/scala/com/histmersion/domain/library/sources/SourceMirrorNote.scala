package com.histmersion.domain.library.sources

import com.histmersion.domain.library.LibraryData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror

/** A source's note in the Markdown mirror: its frontmatter and its notes, as the sources' controllers render it. */
final class SourceMirrorNote(sources: SourcesControllers) extends Mirror.Note:
  val records: Mirror.Records = LibraryData.SourceNotes
  def note(id: com.histmersion.domain.library.sources.SourceMirrorNote.NoteId): Either[Api.Error, com.histmersion.util.data.mirror.Frontmatter.NoteBody] = sources.note(id)

object SourceMirrorNote:
  type NoteId = NoteId.T
  object NoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
