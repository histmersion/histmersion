package com.histmersion.api.personal

/** Bookmarks and notes as Markdown: what `histmersion bookmarks|notes` print and the `bookmarks` and `notes`
  * tools answer. */
object PersonalMarkdown:

  /** `2026-10-30T12:40:00.123456Z` → `2026-10-30 12:40`. Pure. */
  def minute(at: String): String = at.take(16).replace('T', ' ')

  /** What a target is, as a line names it: `source anthony2007_horse`, `place on the map 37.10° N, …`. Pure. */
  def targetLine(t: Target): String =
    val where = Target.where(t)
    (t.itemKey.map(k => s"${t.kind.label.toLowerCase} `$k`").toList ++
      Option.when(!t.kind.isItem)(t.kind.label.toLowerCase).toList ++
      Option.when(where.nonEmpty)(where).toList).mkString(" · ")

  def bookmarkLine(b: Bookmark): String =
    val label = b.label.map(_.value).filter(l => b.target.kind.isItem && l.trim.nonEmpty && l != b.title)
    s"- **${b.name}**" + label.fold("")(_ => s" (${b.title})") + s" — ${targetLine(b.target)} · ${minute(b.createdAt)} · id `${b.id.value}`"

  def bookmarks(bs: List[Bookmark]): String =
    if bs.isEmpty then "_No bookmarks._"
    else (s"**Bookmarks** — ${bs.size}" :: "" :: bs.map(bookmarkLine)).mkString("\n")

  /** The first line of a note's text, at most `chars` long. Pure. */
  def excerpt(text: String, chars: Int = 120): String =
    val first = text.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("")
    if first.length <= chars then first else first.take(chars - 1).trim + "…"

  def noteLine(n: Note): String =
    s"- **${n.title}** — ${targetLine(n.target)} · ${minute(n.updatedAt)} · id `${n.id.value}`\n  ${excerpt(n.text.value)}"

  def notes(ns: List[Note]): String =
    if ns.isEmpty then "_No notes._"
    else (s"**Notes** — ${ns.size}" :: "" :: ns.map(noteLine)).mkString("\n")

  def note(n: Note): String =
    List(s"## Note on ${n.title}", "", s"_${targetLine(n.target)} · written ${minute(n.createdAt)}" +
      (if n.updatedAt != n.createdAt then s", changed ${minute(n.updatedAt)}" else "") + s" · id `${n.id.value}`_",
      "", n.text.value).mkString("\n")

  def readLine(m: ReadMark): String =
    val state = if m.read then s"read ${m.readAt.fold("")(minute)}" else "not read yet"
    s"- **${m.title}** — ${m.kind.label.toLowerCase} `${m.id.value}` · $state" + m.openedAt.fold("")(o => s" · opened ${minute(o)}")

  def reading(ms: List[ReadMark]): String =
    if ms.isEmpty then "_Nothing marked._" else ms.map(readLine).mkString("\n")
