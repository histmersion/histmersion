package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.Codex
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What one tool call of an agent step may return (`agents.tools`). Every later turn of a step re-sends every earlier
  * result, so a step that reads ten records pays for them on each of its turns: a Codex review step averaged ~8 turns
  * and ~140k characters of tool output (2026-10-16). Pure: the [[Toolbox]] applies it to what the handlers return.
  *
  * A cut is never silent — it ends with a trailer saying what was cut and how to read on.
  */
object ToolResultCap:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  /** A chip link of the web UI (`[pie religion](/#topic=pie_religion)`): an agent reads the label and never follows the
    * link, so the target is dropped. Article links (`/article/<slug>`) stay.
    */
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CompactSourceMd = Markdown
  val CompactSourceMd: Markdown.type = Markdown
  type CapMax = CapMax.T
  object CapMax extends Newtype[Int, Pure]
  type CapHint = String
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type SourceMax = CapMax
  val SourceMax: CapMax.type = CapMax
  type NotesExcerptHint = String

  private val ChipLink = """\[([^\]\n]*)\]\(/#[^)\s]*\)""".r

  /** Header lines of a source an agent does not act on (the bundle identity, how the file came). */
  private val IdentityLine = """(?m)^\*\*(?:Uid|Provenance):\*\* [^\n]*\n?""".r

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def plainLinks(md: Markdown): String =
    ChipLink.replaceAllIn(md.value, m => java.util.regex.Matcher.quoteReplacement(m.group(1)))

  /** A source record as an agent reads it: chip labels without their links, no identity lines. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def compactSource(md: CompactSourceMd): String =
    IdentityLine.replaceAllIn(plainLinks(md), "").replace("  \n\n", "\n\n")

  /** The trailer of a cut result. */
  def trailer(shown: Int, total: Int, hint: String): String =
    s"\n\n_…cut at $shown of $total characters — ${hint}_"

  /** `text` within `max` characters: cut at the last line break that keeps at least half of what fits (else mid-line),
    * and closed with a [[trailer]] carrying `hint`. Shorter text is returned as is.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cap(text: String, max: CapMax, hint: CapHint): String =
    if max.value <= 0 || text.length <= max.value then text
    else
      val room = math.max(1, max.value - 160 - hint.length)
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def source(md: Markdown, max: SourceMax): String =
    val text = md.value
    val limit = max.value
    if limit <= 0 || text.length <= limit then text
    else
      val n = text.indexOf(NotesHead)
      val shrunk =
        if n < 0 then text
        else
          val start = n + NotesHead.length
          val end = List(text.indexOf(FindingsHead, start), text.indexOf(MentionHead, start))
            .filter(_ >= 0)
            .minOption
            .getOrElse(text.length)
          val notes = text.substring(start, end)
          val rest = text.length - notes.length
          val hint = "the whole notes: get_source section=\"notes\"; a word in the source: passages"
          val room = limit - rest - 200 - hint.length
          if room >= notes.length then text
          else text.substring(0, start) + notesExcerpt(notes, room, hint) + text.substring(end)
      val m = shrunk.indexOf(MentionHead)
      val lean =
        if shrunk.length <= limit || m < 0 then shrunk
        else shrunk.substring(0, m) + "\n\n_(the list of articles mentioning this source is left out)_"
      cap(lean, max, "the findings alone: get_source section=\"findings\"; a word in the source: passages")

  private def notesExcerpt(notes: String, room: Int, hint: NotesExcerptHint): String =
    if room < 200 then s"_(${notes.length} characters of notes left out — $hint)_"
    else
      val nl = notes.lastIndexOf('\n', room)
      val cut = if nl >= room / 2 then nl else room
      notes.substring(0, cut).stripTrailing + trailer(cut, notes.length, hint)
