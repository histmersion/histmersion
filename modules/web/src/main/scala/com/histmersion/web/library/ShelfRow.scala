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
    key: ShelfRow.Key,
    target: Target,
    name: ShelfRow.Name,
    kind: ShelfRow.Kind,
    line: ShelfRow.Line,
    when: ShelfRow.When,
    unread: ShelfRow.Unread,
    bookmark: Option[Bookmark] = None,
    note: Option[Note] = None
)

object ShelfRow:
  type Ago = Ago.T
  object Ago extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Line = Line.T
  object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type When = When.T
  object When extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unread = Unread.T
  object Unread extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def readable(t: Target, isRead: com.histmersion.web.library.ShelfRow.Key => Boolean): Boolean =
    ItemKind.Readable.contains(t.kind) && t.identity.exists(i => !isRead(i))

  def of(b: Bookmark, isRead: com.histmersion.web.library.ShelfRow.Key => Boolean, ago: com.histmersion.web.library.ShelfRow.When => com.histmersion.web.library.ShelfRow.Ago): ShelfRow =
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

  def of(n: Note, isRead: com.histmersion.web.library.ShelfRow.Key => Boolean, ago: com.histmersion.web.library.ShelfRow.When => com.histmersion.web.library.ShelfRow.Ago): ShelfRow =
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

  def of(m: ReadMark, ago: com.histmersion.web.library.ShelfRow.When => com.histmersion.web.library.ShelfRow.Ago): ShelfRow =
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

  def defaultAgo(iso: com.histmersion.web.library.ShelfRow.When): com.histmersion.web.library.ShelfRow.Ago = Ago.of(iso)
