package com.histmersion.api.personal

object PersonalMarkdown:
  type Instant = Instant.T
  object Instant extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def minute(at: com.histmersion.api.personal.PersonalMarkdown.Instant): com.histmersion.api.Markdown.Text = _root_.com.histmersion.api.Markdown.Text((at).value.take(16).replace('T', ' '))

  def targetLine(t: Target): com.histmersion.api.Markdown.Text =
    val where = Target.where(t)
    (t.itemKey.map(k => s"${t.kind.label.toLowerCase} `$k`").toList ++
      Option.when(!((t.kind.isItem) == (true)))(t.kind.label.toLowerCase).toList ++
      Option.when(where.nonEmpty)(where).toList).mkString(" · ")

  def bookmarkLine(b: Bookmark): com.histmersion.api.Markdown.Text =
    val label = b.label.map(_.value).filter(l => ((b.target.kind.isItem) == (true)) && l.trim.nonEmpty && l != b.title)
    s"- **${b.name}**" + label.fold("")(_ =>
      s" (${b.title})"
    ) + s" — ${targetLine(b.target)} · ${minute(b.createdAt)} · id `${b.id.value}`"

  def bookmarks(bs: List[Bookmark]): com.histmersion.api.Markdown.Text =
    if bs.isEmpty then _root_.com.histmersion.api.Markdown.Text("_No bookmarks._")
    else _root_.com.histmersion.api.Markdown.Text((s"**Bookmarks** — ${bs.size}" :: "" :: bs.map(bookmarkLine)).mkString("\n"))

  def excerpt(text: com.histmersion.api.personal.Note.Text, chars: com.histmersion.api.checks.FormatIssue.Excerpt.Length = _root_.com.histmersion.api.checks.FormatIssue.Excerpt.Length(120)): com.histmersion.api.Markdown.Text =
    val first = (text).value.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("")
    if first.length <= (chars).value then _root_.com.histmersion.api.Markdown.Text(first) else _root_.com.histmersion.api.Markdown.Text(first.take((chars).value - 1).trim + "…")

  def noteLine(n: Note): com.histmersion.api.Markdown.Text =
    s"- **${n.title}** — ${targetLine(n.target)} · ${minute(n.updatedAt)} · id `${n.id.value}`\n  ${excerpt(n.text.value)}"

  def notes(ns: List[Note]): com.histmersion.api.Markdown.Text =
    if ns.isEmpty then _root_.com.histmersion.api.Markdown.Text("_No notes._")
    else _root_.com.histmersion.api.Markdown.Text((s"**Notes** — ${ns.size}" :: "" :: ns.map(noteLine)).mkString("\n"))

  def note(n: Note): com.histmersion.api.Markdown.Text =
    List(
      s"## Note on ${n.title}",
      "",
      s"_${targetLine(n.target)} · written ${minute(n.createdAt)}" +
        (if n.updatedAt != n.createdAt then s", changed ${minute(n.updatedAt)}" else "") + s" · id `${n.id.value}`_",
      "",
      n.text.value
    ).mkString("\n")

  def readLine(m: ReadMark): com.histmersion.api.Markdown.Text =
    val state = if ((m.read) == _root_.com.histmersion.api.personal.ReadMark.Read(true)) then s"read ${m.readAt.fold("")(minute)}" else "not read yet"
    s"- **${m.title}** — ${m.kind.label.toLowerCase} `${m.id.value}` · $state" + m.openedAt.fold("")(o =>
      s" · opened ${minute(o)}"
    )

  def reading(ms: List[ReadMark]): com.histmersion.api.Markdown.Text =
    if ms.isEmpty then _root_.com.histmersion.api.Markdown.Text("_Nothing marked._") else _root_.com.histmersion.api.Markdown.Text(ms.map(readLine).mkString("\n"))
