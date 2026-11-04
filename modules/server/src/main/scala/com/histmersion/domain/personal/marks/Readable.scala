package com.histmersion.domain.personal.marks

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.{ItemKey, ItemKind, ItemRef, ItemUid}
import com.histmersion.{Offset, PageSize}

import java.util.concurrent.atomic.AtomicReference

/** Implemented by a domain whose records a person marks read (sources, articles): what of them is not read yet. The
  * person's marks stay personal's; the owner is told only which records they name.
  */
trait Readable:
  def kind: ItemKind

  /** A page of this kind's records that none of `read` names (by key or by uid), by title, then key. */
  def unread(read: Set[ItemRef], size: PageSize, from: Offset): UnreadItems

object Readable:

  /** The records of one kind held in memory (tests), listed by the rule the tables keep. */
  final class InMemory(
      val kind: ItemKind,
      items: AtomicReference[List[(Option[ItemUid], UnreadItem)]] = AtomicReference(Nil)
  ) extends Readable:

    def add(item: UnreadItem, uid: Option[ItemUid]): Unit =
      items.updateAndGet(all => (uid, item) :: all.filterNot(_._2.key == item.key)): Unit

    def rename(from: ItemKey, to: ItemKey): Unit =
      items.updateAndGet(_.map((uid, i) => (uid, if i.key == from then i.modify(_.key).setTo(to) else i))): Unit

    def remove(key: ItemKey): Unit = items.updateAndGet(_.filterNot(_._2.key == key)): Unit

    def unread(read: Set[ItemRef], size: PageSize, from: Offset): UnreadItems =
      val keys = read.map(_.key)
      val uids = read.flatMap(_.uid)
      val left = items.get
        .filterNot((uid, item) => keys(item.key) || uid.exists(uids))
        .map(_._2)
        .sortBy(i => (i.title.value.toLowerCase, i.key.value: String))
      UnreadItems(left.slice(from.value, from.value + size.value), left.size)
