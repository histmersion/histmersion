package com.histmersion
package web.library

import com.histmersion.api.personal.{Bookmark, Note, PersonalMarkdown, ReadMark, Target, UnreadRow}
import com.histmersion.api.ItemKind
import com.histmersion.api.YearFmt
import com.histmersion.web.marks.Ago
import com.histmersion.lint.{tolerated, LintRule}

/** One row of a shelf of My library, as it reads: what it points at, its name, what it is (`kind`), the line under it
  * (where it is, a note's first line, the authors), when, and whether it is a source or an article not marked read.
  * Pure.
  */
@tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
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
  private def readable(t: Target, isRead: Target.Identity => Boolean): Boolean =
    ItemKind.Readable.contains(t.kind) && t.identity.exists(i => !isRead(i))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def of(b: Bookmark, isRead: Target.Identity => Boolean, ago: String => String): ShelfRow =
    val where = Target.where(b.target)
    val line = List(
      Option.when(b.label.exists(_.value != b.title.value) && b.target.kind.isItem)(b.title.value),
      Option(where.value).filter(_.nonEmpty)
    ).flatten.mkString(" · ")
    ShelfRow(
      s"bookmark:${b.id.value}",
      b.target,
      b.name.value,
      b.target.kind.label.value,
      line,
      ago(b.createdAt.value),
      readable(b.target, isRead),
      bookmark = Some(b)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def of(n: Note, isRead: Target.Identity => Boolean, ago: String => String): ShelfRow =
    val where = Target.where(n.target)
    ShelfRow(
      s"note:${n.id.value}",
      n.target,
      n.title.value,
      s"Note on a ${n.target.kind.label.value.toLowerCase}",
      List(PersonalMarkdown.excerpt(n.text), where.value).filter(_.nonEmpty).mkString(" · "),
      ago(n.updatedAt.value),
      readable(n.target, isRead),
      note = Some(n)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def of(m: ReadMark, ago: String => String): ShelfRow =
    ShelfRow(
      s"opened:${m.kind.wire}:${m.id.value}",
      Target.item(m.kind, m.id),
      m.title.value,
      m.kind.label.value,
      if m.read.value then "Read" else "Not read yet",
      m.openedAt.fold("")(o => s"opened ${ago(o.value)}"),
      !m.read.value
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(u: UnreadRow): ShelfRow =
    ShelfRow(
      s"unread:${u.kind.wire}:${u.id.value}",
      Target.item(u.kind, u.id),
      u.title.value,
      u.kind.label.value,
      List(
        TableRow.authors(u.authors.map(_.value)),
        u.year.fold("")(_.toString),
        YearFmt.period(u.periodStart, u.periodEnd)
      )
        .filter(_.nonEmpty)
        .mkString(" · "),
      "",
      unread = true
    )

  /** "Recently opened": the marks opened, last first. Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def recent(marks: Iterable[ReadMark]): List[ReadMark] =
    marks.filter(_.openedAt.isDefined).toList.sortBy(_.openedAt.fold("")(_.value)).reverse

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def defaultAgo(iso: String): String = Ago.of(Ago.IsoDate(iso)).value
