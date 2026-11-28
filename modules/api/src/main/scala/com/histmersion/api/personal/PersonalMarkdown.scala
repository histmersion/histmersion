package com.histmersion
package api.personal

import com.histmersion.api.checks.FormatIssue
import com.histmersion.lint.{tolerated, LintRule}

object PersonalMarkdown:
  def minute(at: String): String = at.take(16).replace('T', ' ')

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def targetLine(t: Target): String =
    val where = Target.where(t)
    (t.itemKey.map(k => s"${t.kind.label.value.toLowerCase} `$k`").toList ++
      Option.when(!((t.kind.isItem) == (true)))(t.kind.label.value.toLowerCase).toList ++
      Option.when(where.value.nonEmpty)(where.value).toList).mkString(" · ")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def bookmarkLine(b: Bookmark): String =
    val label =
      b.label.map(_.value).filter(l => ((b.target.kind.isItem) == (true)) && l.trim.nonEmpty && l != b.title.value)
    s"- **${b.name}**" + label.fold("")(_ =>
      s" (${b.title})"
    ) + s" — ${targetLine(b.target)} · ${minute(b.createdAt.value)} · id `${b.id.value}`"

  def bookmarks(bs: List[Bookmark]): String =
    if bs.isEmpty then ("_No bookmarks._")
    else ((s"**Bookmarks** — ${bs.size}" :: "" :: bs.map(bookmarkLine)).mkString("\n"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def excerpt(text: Note.Text, chars: FormatIssue.Excerpt.Length = FormatIssue.Excerpt.Length(120)): String =
    val first = (text).value.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("")
    if first.length <= (chars).value then (first) else (first.take((chars).value - 1).trim + "…")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def noteLine(n: Note): String =
    s"- **${n.title}** — ${targetLine(n.target)} · ${minute(n.updatedAt.value)} · id `${n.id.value}`\n  ${excerpt(n.text)}"

  def notes(ns: List[Note]): String =
    if ns.isEmpty then ("_No notes._")
    else ((s"**Notes** — ${ns.size}" :: "" :: ns.map(noteLine)).mkString("\n"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def note(n: Note): String =
    List(
      s"## Note on ${n.title}",
      "",
      s"_${targetLine(n.target)} · written ${minute(n.createdAt.value)}" +
        (if n.updatedAt != n.createdAt then s", changed ${minute(n.updatedAt.value)}"
         else "") + s" · id `${n.id.value}`_",
      "",
      n.text.value
    ).mkString("\n")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def readLine(m: ReadMark): String =
    val state =
      if (m.read) == ReadMark.Read.Read then s"read ${m.readAt.fold("")(a => minute(a.value))}" else "not read yet"
    s"- **${m.title}** — ${m.kind.label.value.toLowerCase} `${m.id.value}` · $state" + m.openedAt.fold("")(o =>
      s" · opened ${minute(o.value)}"
    )

  def reading(ms: List[ReadMark]): String =
    if ms.isEmpty then ("_Nothing marked._") else (ms.map(readLine).mkString("\n"))
