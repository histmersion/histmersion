package com.histmersion.domain.library.sources

import com.histmersion.domain.library.LibraryData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror

/** A source's note in the Markdown mirror: its frontmatter and its notes, as the sources' controllers render it. */
final class SourceMirrorNote(sources: SourcesControllers) extends Mirror.Note:
  val records: Mirror.Records = LibraryData.SourceNotes
  def note(id: com.histmersion.util.data.mirror.MirrorPlacement.RecordId): Either[Api.Error, com.histmersion.util.data.mirror.Frontmatter.NoteBody] =
    com.histmersion.domain.library.Citekey
      .parse(id.value)
      .left
      .map(p => com.histmersion.api.Write.invalid(com.histmersion.api.Write.InvalidMessage(p.value)))
      .flatMap(ck => sources.note(ck).map(t => com.histmersion.util.data.mirror.Frontmatter.NoteBody(t.value)))
