package com.histmersion
package domain.personal.reading

import com.histmersion.api.personal.{ItemKey as WireKey, ReadMark as WireReadMark}
import com.histmersion.domain.personal.PersonalState.StoredMark
import com.histmersion.domain.personal.{MarkTarget, MarkTime}
import com.histmersion.domain.{Described, ItemRef}
import com.histmersion.domain.Title

/** Whether the person has read a record, and when they last opened it, as they see it: the record's key and title now.
  */
final case class ReadMark(item: MarkTarget.Item, title: Title, readAt: Option[MarkTime], openedAt: Option[MarkTime]):
  def wire: WireReadMark =
    WireReadMark(
      item.kind,
      WireKey(item.ref.key.value),
      WireReadMark.Title(title.value),
      WireReadMark.Read(readAt.isDefined),
      readAt.map(t => WireReadMark.ReadAt(t.iso.value)),
      openedAt.map(t => WireReadMark.OpenedAt(t.iso.value))
    )

object ReadMark:
  def of(stored: StoredMark, described: Map[ItemRef, Described]): ReadMark =
    MarkTarget.Item(stored.ref).shown(described, stored.targetLabel) match
      case (item: MarkTarget.Item, title) => ReadMark(item, title, stored.readAt, stored.openedAt)
      case (_, title) => ReadMark(MarkTarget.Item(stored.ref), title, stored.readAt, stored.openedAt)
