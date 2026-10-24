package com.histmersion.server.agents

import com.histmersion.api.Query

/** What one tool call of an agent step may return (`agents.tools`).
  * Every later turn of a step re-sends every earlier result, so a step that reads ten records pays for
  * them on each of its turns: a Codex review step averaged ~8 turns and ~140k characters of tool
  * output (2026-10-16). Pure: the [[Toolbox]] applies it to what the handlers return.
  *
  * A cut is never silent — it ends with a trailer saying what was cut and how to read on. */
object ToolResultCap:

  /** A chip link of the web UI (`[pie religion](/#topic=pie_religion)`): an agent reads the label and
    * never follows the link, so the target is dropped. Article links (`/article/<slug>`) stay. */
  private val ChipLink = """\[([^\]\n]*)\]\(/#[^)\s]*\)""".r

  /** Header lines of a source an agent does not act on (the bundle identity, how the file came). */
  private val IdentityLine = """(?m)^\*\*(?:Uid|Provenance):\*\* [^\n]*\n?""".r

  def plainLinks(md: String): String = ChipLink.replaceAllIn(md, m => java.util.regex.Matcher.quoteReplacement(m.group(1)))

  /** A source record as an agent reads it: chip labels without their links, no identity lines. */
  def compactSource(md: String): String =
    IdentityLine.replaceAllIn(plainLinks(md), "").replace("  \n\n", "\n\n")

  /** The trailer of a cut result. */
  def trailer(shown: Int, total: Int, hint: String): String =
    s"\n\n_…cut at $shown of $total characters — ${hint}_"

  /** `text` within `max` characters: cut at the last line break that keeps at least half of what fits
    * (else mid-line), and closed with a [[trailer]] carrying `hint`. Shorter text is returned as is. */
  def cap(text: String, max: Int, hint: String): String =
    if max <= 0 || text.length <= max then text
    else
      val room = math.max(1, max - 160 - hint.length)
      val nl   = text.lastIndexOf('\n', room)
      val cut  = if nl >= room / 2 then nl else room
      text.substring(0, cut).stripTrailing + trailer(cut, text.length, hint)

  private val NotesHead    = "\n\n## Notes\n\n"
  private val FindingsHead = "\n\n## Findings ("
  private val MentionHead  = "\n\n## Mentioned in articles ("

  /** A source's record (`get_source` without a section, or `all`) within `max`: the notes give way
    * first, so the header, the summary and the findings — what a reviewer checks a sentence against —
    * arrive whole; then the list of articles mentioning it; anything still over is cut like any result. */
  def source(md: String, max: Int): String =
    if max <= 0 || md.length <= max then md
    else
      val n = md.indexOf(NotesHead)
      val shrunk =
        if n < 0 then md
        else
          val start = n + NotesHead.length
          val end   = List(md.indexOf(FindingsHead, start), md.indexOf(MentionHead, start)).filter(_ >= 0).minOption.getOrElse(md.length)
          val notes = md.substring(start, end)
          val rest  = md.length - notes.length
          val hint  = "the whole notes: get_source section=\"notes\"; a word in the source: passages"
          val room  = max - rest - 200 - hint.length
          if room >= notes.length then md
          else md.substring(0, start) + notesExcerpt(notes, room, hint) + md.substring(end)
      val m = shrunk.indexOf(MentionHead)
      val lean =
        if shrunk.length <= max || m < 0 then shrunk
        else shrunk.substring(0, m) + "\n\n_(the list of articles mentioning this source is left out)_"
      cap(lean, max, "the findings alone: get_source section=\"findings\"; a word in the source: passages")

  private def notesExcerpt(notes: String, room: Int, hint: String): String =
    if room < 200 then s"_(${notes.length} characters of notes left out — $hint)_"
    else
      val nl  = notes.lastIndexOf('\n', room)
      val cut = if nl >= room / 2 then nl else room
      notes.substring(0, cut).stripTrailing + trailer(cut, notes.length, hint)

  /** A `query` result as an agent sees it: at most `maxRows` rows, marked as cut (the row cap's own
    * flag), then [[cap]]ped like any result. */
  def queryResult(r: Query.QueryResult, maxRows: Int, max: Int): String =
    val shown = if maxRows > 0 && r.rows.size > maxRows then r.copy(rows = r.rows.take(maxRows), rowCount = maxRows, truncated = true) else r
    val more  = if shown.rows.size < r.rows.size then
      s"\n\n_…${r.rows.size - shown.rows.size}${if r.truncated then "+" else ""} more rows not shown — select only the rows you need " +
        "(`WHERE citekey IN (…)`, `LIMIT`), never a whole table_"
    else ""
    cap(Query.markdown(shown) + more, max, "select fewer columns or rows (`WHERE … IN (…)`, `LIMIT`)")
