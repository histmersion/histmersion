package com.histmersion.api.personal

object PersonalMarkdown:

  type MinuteResult = MinuteResult.T
  object MinuteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinuteAt = MinuteAt.T
  object MinuteAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetLineResult = TargetLineResult.T
  object TargetLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BookmarkLineResult = BookmarkLineResult.T
  object BookmarkLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BookmarksResult = BookmarksResult.T
  object BookmarksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcerptResult = ExcerptResult.T
  object ExcerptResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcerptText = ExcerptText.T
  object ExcerptText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcerptChars = ExcerptChars.T
  object ExcerptChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteLineResult = NoteLineResult.T
  object NoteLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesResult = NotesResult.T
  object NotesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteResult = NoteResult.T
  object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadLineResult = ReadLineResult.T
  object ReadLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingResult = ReadingResult.T
  object ReadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def minute(at: com.histmersion.api.personal.PersonalMarkdown.MinuteAt): com.histmersion.api.personal.PersonalMarkdown.MinuteResult = at.take(16).replace('T', ' ')

  def targetLine(t: Target): com.histmersion.api.personal.PersonalMarkdown.TargetLineResult =
    val where = Target.where(t)
    (t.itemKey.map(k => s"${t.kind.label.toLowerCase} `$k`").toList ++
      Option.when(!t.kind.isItem)(t.kind.label.toLowerCase).toList ++
      Option.when(where.nonEmpty)(where).toList).mkString(" · ")

  def bookmarkLine(b: Bookmark): com.histmersion.api.personal.PersonalMarkdown.BookmarkLineResult =
    val label = b.label.map(_.value).filter(l => b.target.kind.isItem && l.trim.nonEmpty && l != b.title)
    s"- **${b.name}**" + label.fold("")(_ =>
      s" (${b.title})"
    ) + s" — ${targetLine(b.target)} · ${minute(b.createdAt)} · id `${b.id.value}`"

  def bookmarks(bs: List[Bookmark]): com.histmersion.api.personal.PersonalMarkdown.BookmarksResult =
    if bs.isEmpty then _root_.com.histmersion.api.personal.PersonalMarkdown.BookmarksResult("_No bookmarks._")
    else _root_.com.histmersion.api.personal.PersonalMarkdown.BookmarksResult((s"**Bookmarks** — ${bs.size}" :: "" :: bs.map(bookmarkLine)).mkString("\n"))

  def excerpt(text: com.histmersion.api.personal.PersonalMarkdown.ExcerptText, chars: com.histmersion.api.personal.PersonalMarkdown.ExcerptChars = _root_.com.histmersion.api.personal.PersonalMarkdown.ExcerptChars(120)): com.histmersion.api.personal.PersonalMarkdown.ExcerptResult =
    val first = text.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("")
    if first.length <= chars then first else first.take(chars - 1).trim + "…"

  def noteLine(n: Note): com.histmersion.api.personal.PersonalMarkdown.NoteLineResult =
    s"- **${n.title}** — ${targetLine(n.target)} · ${minute(n.updatedAt)} · id `${n.id.value}`\n  ${excerpt(n.text.value)}"

  def notes(ns: List[Note]): com.histmersion.api.personal.PersonalMarkdown.NotesResult =
    if ns.isEmpty then _root_.com.histmersion.api.personal.PersonalMarkdown.NotesResult("_No notes._")
    else _root_.com.histmersion.api.personal.PersonalMarkdown.NotesResult((s"**Notes** — ${ns.size}" :: "" :: ns.map(noteLine)).mkString("\n"))

  def note(n: Note): com.histmersion.api.personal.PersonalMarkdown.NoteResult =
    List(
      s"## Note on ${n.title}",
      "",
      s"_${targetLine(n.target)} · written ${minute(n.createdAt)}" +
        (if n.updatedAt != n.createdAt then s", changed ${minute(n.updatedAt)}" else "") + s" · id `${n.id.value}`_",
      "",
      n.text.value
    ).mkString("\n")

  def readLine(m: ReadMark): com.histmersion.api.personal.PersonalMarkdown.ReadLineResult =
    val state = if m.read then s"read ${m.readAt.fold("")(minute)}" else "not read yet"
    s"- **${m.title}** — ${m.kind.label.toLowerCase} `${m.id.value}` · $state" + m.openedAt.fold("")(o =>
      s" · opened ${minute(o)}"
    )

  def reading(ms: List[ReadMark]): com.histmersion.api.personal.PersonalMarkdown.ReadingResult =
    if ms.isEmpty then _root_.com.histmersion.api.personal.PersonalMarkdown.ReadingResult("_Nothing marked._") else _root_.com.histmersion.api.personal.PersonalMarkdown.ReadingResult(ms.map(readLine).mkString("\n"))
