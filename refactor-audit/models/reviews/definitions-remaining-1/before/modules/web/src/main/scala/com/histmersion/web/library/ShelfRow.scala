package com.histmersion.web.library

import com.histmersion.api.personal.{Bookmark, Note, PersonalMarkdown, ReadMark, Target, UnreadRow}
import com.histmersion.api.ItemKind
import com.histmersion.api.YearFmt
import com.histmersion.web.marks.Ago

/** One row of a shelf of My library, as it reads: what it points at, its name, what it is (`kind`), the line under it
  * (where it is, a note's first line, the authors), when, and whether it is a source or an article not marked read.
  * Pure.
  */
final case class ShelfRow(
    key: String,
    target: Target,
    name: String,
    kind: String,
    line: String,
    when: String,
    unread: Boolean,
    bookmark: Option[Bookmark] = None,
    note: Option[Note] = None
)

object ShelfRow:

  private def readable(t: Target, isRead: String => Boolean): Boolean =
    ItemKind.Readable.contains(t.kind) && t.identity.exists(i => !isRead(i))

  def of(b: Bookmark, isRead: String => Boolean, ago: String => String): ShelfRow =
    val where = Target.where(b.target)
    val line = List(
      Option.when(b.label.exists(_.value != b.title) && b.target.kind.isItem)(b.title),
      Option(where).filter(_.nonEmpty)
    ).flatten.mkString(" · ")
    ShelfRow(
      s"bookmark:${b.id.value}",
      b.target,
      b.name,
      b.target.kind.label,
      line,
      ago(b.createdAt),
      readable(b.target, isRead),
      bookmark = Some(b)
    )

  def of(n: Note, isRead: String => Boolean, ago: String => String): ShelfRow =
    val where = Target.where(n.target)
    ShelfRow(
      s"note:${n.id.value}",
      n.target,
      n.title,
      s"Note on a ${n.target.kind.label.toLowerCase}",
      List(PersonalMarkdown.excerpt(n.text.value), where).filter(_.nonEmpty).mkString(" · "),
      ago(n.updatedAt),
      readable(n.target, isRead),
      note = Some(n)
    )

  def of(m: ReadMark, ago: String => String): ShelfRow =
    ShelfRow(
      s"opened:${m.kind.wire}:${m.id.value}",
      Target.item(m.kind, m.id.value),
      m.title,
      m.kind.label,
      if m.read then "Read" else "Not read yet",
      m.openedAt.fold("")(o => s"opened ${ago(o)}"),
      !m.read
    )

  def of(u: UnreadRow): ShelfRow =
    ShelfRow(
      s"unread:${u.kind.wire}:${u.id.value}",
      Target.item(u.kind, u.id.value),
      u.title,
      u.kind.label,
      List(TableRow.authors(u.authors), u.year.fold("")(_.toString), YearFmt.period(u.periodStart, u.periodEnd))
        .filter(_.nonEmpty)
        .mkString(" · "),
      "",
      unread = true
    )

  /** "Recently opened": the marks opened, last first. Pure. */
  def recent(marks: Iterable[ReadMark]): List[ReadMark] =
    marks.filter(_.openedAt.isDefined).toList.sortBy(_.openedAt.getOrElse("")).reverse

  def defaultAgo(iso: String): String = Ago.of(iso)
