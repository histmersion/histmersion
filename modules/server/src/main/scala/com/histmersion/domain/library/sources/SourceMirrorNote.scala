package com.histmersion
package domain.library.sources

import com.histmersion.domain.library.LibraryData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.util.data.mirror.Frontmatter
import com.histmersion.domain.library.Citekey
import com.histmersion.api.Write
import com.histmersion.lint.{LintRule, tolerated}

/** A source's note in the Markdown mirror: its frontmatter and its notes, as the sources' controllers render it. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class SourceMirrorNote(sources: SourcesControllers) extends Mirror.Note:
  val records: Mirror.Records = LibraryData.SourceNotes
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def note(id: MirrorPlacement.RecordId): Either[Api.Error, Frontmatter.NoteBody] =
    Citekey
      .parse(id.value)
      .left
      .map(p => Write.invalid(p))
      .flatMap(ck => sources.note(ck).map(t => Frontmatter.NoteBody(t.value)))
