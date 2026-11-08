package com.histmersion
package api.personal

import com.histmersion.api.checks.FormatIssue

object PersonalMarkdown:
  def minute(at: String): String = at.take(16).replace('T', ' ')

  def targetLine(t: Target): String =
    val where = Target.where(t)
    (t.itemKey.map(k => s"${t.kind.label.value.toLowerCase} `$k`").toList ++
      Option.when(!((t.kind.isItem) == (true)))(t.kind.label.value.toLowerCase).toList ++
      Option.when(where.value.nonEmpty)(where.value).toList).mkString(" · ")

  def bookmarkLine(b: Bookmark): String =
    val label = b.label.map(_.value).filter(l => ((b.target.kind.isItem) == (true)) && l.trim.nonEmpty && l != b.title.value)
    s"- **${b.name}**" + label.fold("")(_ =>
      s" (${b.title})"
    ) + s" — ${targetLine(b.target)} · ${minute(b.createdAt.value)} · id `${b.id.value}`"

  def bookmarks(bs: List[Bookmark]): String =
    if bs.isEmpty then ("_No bookmarks._")
    else ((s"**Bookmarks** — ${bs.size}" :: "" :: bs.map(bookmarkLine)).mkString("\n"))

  def excerpt(text: Note.Text, chars: FormatIssue.Excerpt.Length = FormatIssue.Excerpt.Length(120)): String =
    val first = (text).value.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("")
    if first.length <= (chars).value then (first) else (first.take((chars).value - 1).trim + "…")

  def noteLine(n: Note): String =
    s"- **${n.title}** — ${targetLine(n.target)} · ${minute(n.updatedAt.value)} · id `${n.id.value}`\n  ${excerpt(n.text)}"

  def notes(ns: List[Note]): String =
    if ns.isEmpty then ("_No notes._")
    else ((s"**Notes** — ${ns.size}" :: "" :: ns.map(noteLine)).mkString("\n"))

  def note(n: Note): String =
    List(
      s"## Note on ${n.title}",
      "",
      s"_${targetLine(n.target)} · written ${minute(n.createdAt.value)}" +
        (if n.updatedAt.value != n.createdAt.value then s", changed ${minute(n.updatedAt.value)}" else "") + s" · id `${n.id.value}`_",
      "",
      n.text.value
    ).mkString("\n")

  def readLine(m: ReadMark): String =
    val state = if ((m.read) == ReadMark.Read(true)) then s"read ${m.readAt.fold("")(a => minute(a.value))}" else "not read yet"
    s"- **${m.title}** — ${m.kind.label.value.toLowerCase} `${m.id.value}` · $state" + m.openedAt.fold("")(o =>
      s" · opened ${minute(o.value)}"
    )

  def reading(ms: List[ReadMark]): String =
    if ms.isEmpty then ("_Nothing marked._") else (ms.map(readLine).mkString("\n"))
