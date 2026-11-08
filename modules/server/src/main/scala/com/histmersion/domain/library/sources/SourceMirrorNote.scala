package com.histmersion
package domain.library.sources

import com.histmersion.domain.library.LibraryData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.util.data.mirror.Frontmatter
import com.histmersion.domain.library.Citekey
import com.histmersion.api.Write

/** A source's note in the Markdown mirror: its frontmatter and its notes, as the sources' controllers render it. */
final class SourceMirrorNote(sources: SourcesControllers) extends Mirror.Note:
  val records: Mirror.Records = LibraryData.SourceNotes
  def note(id: MirrorPlacement.RecordId): Either[Api.Error, Frontmatter.NoteBody] =
    Citekey
      .parse(id.value)
      .left
      .map(p => Write.invalid(Write.InvalidMessage(p.value)))
      .flatMap(ck => sources.note(ck).map(t => Frontmatter.NoteBody(t.value)))
