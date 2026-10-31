package com.histmersion.domain.automation.worklists

import com.histmersion.api.Queue
import com.histmersion.api.Queue.{Kind, QueueAdd, QueueItem, QueuePatch}
import com.histmersion.util.database.Transactor

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference

/** The work lists' kernel: one table of items, a list per owner — ACQUIRE (the library's), TASKS and LOG (the
  * engine's). Each owner adds its own rules on top (a log item's dated topic, an acquisition item's DOI); the kernel
  * keeps the items, their statuses and their stamps.
  */
trait QueueService:
  def list(list: Kind, scope: QueueScope): List[QueueItem]
  def get(id: QueueItemId): Option[QueueItem]

  /** The items with these ids (any list, any status), by id; an id with no item is left out. */
  def byIds(ids: List[QueueItemId]): List[QueueItem]

  /** The items of `list` (any status) whose text names `doi`, by id. */
  def naming(list: Kind, doi: String): List[QueueItem]

  /** A new item of `list`, as stored (its status checked by the caller). */
  def add(list: Kind, item: QueueAdd): QueueItem

  /** Only the parts present change (`Queue.applyPatch`); a done item keeps its first done time. */
  def patch(id: QueueItemId, patch: QueuePatch): Either[QueueError, QueueItem]

  /** The item as it was before it was removed. */
  def delete(id: QueueItemId): Either[QueueError, QueueItem]

  /** `items` written into `list` in one transaction, after emptying it when `mode` replaces. */
  def replace(list: Kind, items: List[QueueAdd], mode: ImportMode): ImportCounts

object QueueService:
  def apply(db: Transactor): QueueService = Implementation(db)

  final private class Implementation(db: Transactor) extends QueueService:
    def list(list: Kind, scope: QueueScope): List[QueueItem] = db
      .read(scope match
        case QueueScope.Open                            => QueueRepo.list(list, all = false)
        case QueueScope.All                             => QueueRepo.list(list, all = true)
        case QueueScope.ForJob(job)                     => QueueRepo.forJob(list, job)
        case QueueScope.ForGoal(goal, linked, withDone) => QueueRepo.forGoal(list, goal, linked.toList, withDone))
      .toList

    def get(id: QueueItemId): Option[QueueItem] = db.read(QueueRepo.get(id.value))
    def byIds(ids: List[QueueItemId]): List[QueueItem] = db.read(QueueRepo.byIds(ids.map(_.value))).toList
    def naming(list: Kind, doi: String): List[QueueItem] = db.read(QueueRepo.naming(list, doi)).toList
    def add(list: Kind, item: QueueAdd): QueueItem = db.transact(QueueRepo.insert(list, item))

    def patch(id: QueueItemId, p: QueuePatch): Either[QueueError, QueueItem] = db.transact:
      QueueRepo.get(id.value).toRight(QueueError.NotFound(id)).map { cur =>
        QueueRepo.update(Queue.applyPatch(cur, p))
        QueueRepo.get(id.value).get
      }

    def delete(id: QueueItemId): Either[QueueError, QueueItem] = db.transact:
      QueueRepo.get(id.value).toRight(QueueError.NotFound(id)).map { item =>
        QueueRepo.delete(id.value)
        item
      }

    def replace(list: Kind, items: List[QueueAdd], mode: ImportMode): ImportCounts = db.transact:
      val removed = if mode == ImportMode.Replace then QueueRepo.deleteList(list) else 0
      items.foreach(i => { val _ = QueueRepo.insert(list, i) })
      ImportCounts(items.size, removed)

  /** The work lists in memory; `clock` stands in for the database's `now()` (an item's made and done times). */
  final class InMemory(state: AtomicReference[WorkListState], clock: Clock) extends QueueService:
    private def now: Instant = Instant.now(clock)

    def list(list: Kind, scope: QueueScope): List[QueueItem] = state.get.listed(list, scope).toList
    def get(id: QueueItemId): Option[QueueItem] = state.get.get(id)

    def byIds(ids: List[QueueItemId]): List[QueueItem] =
      val wanted: Set[Int] = ids.map(i => i.value: Int).toSet
      state.get.items.filter(i => wanted(i.id)).toList

    def naming(list: Kind, doi: String): List[QueueItem] = state.get.naming(list, doi).toList
    def add(list: Kind, item: QueueAdd): QueueItem = state.updateAndGet(_.added(list, item, now)).items.last

    def patch(id: QueueItemId, p: QueuePatch): Either[QueueError, QueueItem] =
      val after = state.updateAndGet(s => s.get(id).fold(s)(cur => s.updated(Queue.applyPatch(cur, p), now)))
      after.get(id).toRight(QueueError.NotFound(id))

    def delete(id: QueueItemId): Either[QueueError, QueueItem] =
      val before = state.getAndUpdate(_.removed(id))
      before.get(id).toRight(QueueError.NotFound(id))

    def replace(list: Kind, items: List[QueueAdd], mode: ImportMode): ImportCounts =
      val at = now
      val before = state.getAndUpdate { s =>
        val start = if mode == ImportMode.Replace then s.emptied(list) else s
        start.addedAll(list, items, at)
      }
      ImportCounts(items.size, if mode == ImportMode.Replace then before.listed(list, QueueScope.All).size else 0)
