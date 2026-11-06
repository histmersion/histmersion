package com.histmersion.domain.automation.agents

import com.histmersion.server.agents.Codex

/** What one tool call of an agent step may return (`agents.tools`). Every later turn of a step re-sends every earlier
  * result, so a step that reads ten records pays for them on each of its turns: a Codex review step averaged ~8 turns
  * and ~140k characters of tool output (2026-10-16). Pure: the [[Toolbox]] applies it to what the handlers return.
  *
  * A cut is never silent — it ends with a trailer saying what was cut and how to read on.
  */
object ToolResultCap:

  /** A chip link of the web UI (`[pie religion](/#topic=pie_religion)`): an agent reads the label and never follows the
    * link, so the target is dropped. Article links (`/article/<slug>`) stay.
    */
  type PlainLinksResult = PlainLinksResult.T
  object PlainLinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainLinksMd = PlainLinksMd.T
  object PlainLinksMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompactSourceResult = CompactSourceResult.T
  object CompactSourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompactSourceMd = CompactSourceMd.T
  object CompactSourceMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrailerResult = TrailerResult.T
  object TrailerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrailerShown = TrailerShown.T
  object TrailerShown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrailerTotal = TrailerTotal.T
  object TrailerTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrailerHint = TrailerHint.T
  object TrailerHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapResult = CapResult.T
  object CapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapText = CapText.T
  object CapText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapMax = CapMax.T
  object CapMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapHint = CapHint.T
  object CapHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceResult = SourceResult.T
  object SourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceMd = SourceMd.T
  object SourceMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceMax = SourceMax.T
  object SourceMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesExcerptResult = NotesExcerptResult.T
  object NotesExcerptResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesExcerptNotes = NotesExcerptNotes.T
  object NotesExcerptNotes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesExcerptRoom = NotesExcerptRoom.T
  object NotesExcerptRoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesExcerptHint = NotesExcerptHint.T
  object NotesExcerptHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val ChipLink = """\[([^\]\n]*)\]\(/#[^)\s]*\)""".r

  /** Header lines of a source an agent does not act on (the bundle identity, how the file came). */
  private val IdentityLine = """(?m)^\*\*(?:Uid|Provenance):\*\* [^\n]*\n?""".r

  def plainLinks(md: com.histmersion.domain.automation.agents.ToolResultCap.PlainLinksMd): com.histmersion.domain.automation.agents.ToolResultCap.PlainLinksResult =
    ChipLink.replaceAllIn(md, m => java.util.regex.Matcher.quoteReplacement(m.group(1)))

  /** A source record as an agent reads it: chip labels without their links, no identity lines. */
  def compactSource(md: com.histmersion.domain.automation.agents.ToolResultCap.CompactSourceMd): com.histmersion.domain.automation.agents.ToolResultCap.CompactSourceResult =
    IdentityLine.replaceAllIn(plainLinks(md), "").replace("  \n\n", "\n\n")

  /** The trailer of a cut result. */
  def trailer(shown: com.histmersion.domain.automation.agents.ToolResultCap.TrailerShown, total: com.histmersion.domain.automation.agents.ToolResultCap.TrailerTotal, hint: com.histmersion.domain.automation.agents.ToolResultCap.TrailerHint): com.histmersion.domain.automation.agents.ToolResultCap.TrailerResult =
    s"\n\n_…cut at $shown of $total characters — ${hint}_"

  /** `text` within `max` characters: cut at the last line break that keeps at least half of what fits (else mid-line),
    * and closed with a [[trailer]] carrying `hint`. Shorter text is returned as is.
    */
  def cap(text: com.histmersion.domain.automation.agents.ToolResultCap.CapText, max: com.histmersion.domain.automation.agents.ToolResultCap.CapMax, hint: com.histmersion.domain.automation.agents.ToolResultCap.CapHint): com.histmersion.domain.automation.agents.ToolResultCap.CapResult =
    if max <= 0 || text.length <= max then text
    else
      val room = math.max(1, max - 160 - hint.length)
      val nl = text.lastIndexOf('\n', room)
      val cut = if nl >= room / 2 then nl else room
      text.substring(0, cut).stripTrailing + trailer(cut, text.length, hint)

  private val NotesHead = "\n\n## Notes\n\n"
  private val FindingsHead = "\n\n## Findings ("
  private val MentionHead = "\n\n## Mentioned in articles ("

  /** A source's record (`get_source` without a section, or `all`) within `max`: the notes give way first, so the
    * header, the summary and the findings — what a reviewer checks a sentence against — arrive whole; then the list of
    * articles mentioning it; anything still over is cut like any result.
    */
  def source(md: com.histmersion.domain.automation.agents.ToolResultCap.SourceMd, max: com.histmersion.domain.automation.agents.ToolResultCap.SourceMax): com.histmersion.domain.automation.agents.ToolResultCap.SourceResult =
    if max <= 0 || md.length <= max then md
    else
      val n = md.indexOf(NotesHead)
      val shrunk =
        if n < 0 then md
        else
          val start = n + NotesHead.length
          val end = List(md.indexOf(FindingsHead, start), md.indexOf(MentionHead, start))
            .filter(_ >= 0)
            .minOption
            .getOrElse(md.length)
          val notes = md.substring(start, end)
          val rest = md.length - notes.length
          val hint = "the whole notes: get_source section=\"notes\"; a word in the source: passages"
          val room = max - rest - 200 - hint.length
          if room >= notes.length then md
          else md.substring(0, start) + notesExcerpt(notes, room, hint) + md.substring(end)
      val m = shrunk.indexOf(MentionHead)
      val lean =
        if shrunk.length <= max || m < 0 then shrunk
        else shrunk.substring(0, m) + "\n\n_(the list of articles mentioning this source is left out)_"
      cap(lean, max, "the findings alone: get_source section=\"findings\"; a word in the source: passages")

  private def notesExcerpt(notes: com.histmersion.domain.automation.agents.ToolResultCap.NotesExcerptNotes, room: com.histmersion.domain.automation.agents.ToolResultCap.NotesExcerptRoom, hint: com.histmersion.domain.automation.agents.ToolResultCap.NotesExcerptHint): com.histmersion.domain.automation.agents.ToolResultCap.NotesExcerptResult =
    if room < 200 then s"_(${notes.length} characters of notes left out — $hint)_"
    else
      val nl = notes.lastIndexOf('\n', room)
      val cut = if nl >= room / 2 then nl else room
      notes.substring(0, cut).stripTrailing + trailer(cut, notes.length, hint)
