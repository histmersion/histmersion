package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueueItem, QueuePatch}
import com.histmersion.api.automation.Goal.Plans.GoalLink
import com.histmersion.util.database.Transactor
import com.histmersion.domain.Creator

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.library.Doi
import com.histmersion.util.database.WritePermit

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
  def naming(list: Kind, doi: Doi): List[QueueItem]

  /** A new item of `list`, as stored (its status checked by the caller). */
  def add(list: Kind, item: QueueAdd, creator: Creator.New)(using WritePermit): QueueItem

  /** Only the parts present change (`Queue.applyPatch`); a done item keeps its first done time. */
  def patch(id: QueueItemId, patch: QueuePatch)(using WritePermit): Either[QueueError, QueueItem]

  /** The item as it was before it was removed. */
  def delete(id: QueueItemId)(using WritePermit): Either[QueueError, QueueItem]

  /** `items` written into `list` in one transaction, after emptying it when `mode` replaces. */
  def replace(list: Kind, items: List[QueueAdd], mode: ImportMode, creator: Creator.New)(using
      WritePermit
  ): ImportCounts

object QueueService:
  def apply(db: Transactor): QueueService = Implementation(db)

  final private class Implementation(db: Transactor) extends QueueService:
    def list(list: Kind, scope: QueueScope): List[QueueItem] = db
      .read(scope match
        case QueueScope.Open                            => QueueRepo.list(list, all = false)
        case QueueScope.All                             => QueueRepo.list(list, all = true)
        case QueueScope.ForJob(job)                     => QueueRepo.forJob(list, QueueRepo.JobText(job.value))
        case QueueScope.ForGoal(goal, linked, withDone) =>
          QueueRepo.forGoal(
            list,
            QueueRepo.GoalText(goal.value),
            linked.toList.map(l => GoalLink.Ref(l.value)),
            withDone.value
          ))
      .toList

    def get(id: QueueItemId): Option[QueueItem] = db.read(QueueRepo.get(QueueRepo.QueueItemId(id.value)))
    def byIds(ids: List[QueueItemId]): List[QueueItem] =
      db.read(QueueRepo.byIds(ids.map(i => QueueRepo.QueueItemId(i.value)))).toList
    def naming(list: Kind, doi: Doi): List[QueueItem] = db.read(QueueRepo.naming(list, doi)).toList
    def add(list: Kind, item: QueueAdd, creator: Creator.New)(using WritePermit): QueueItem =
      db.write(QueueRepo.insert(list, item, creator))

    def patch(id: QueueItemId, p: QueuePatch)(using WritePermit): Either[QueueError, QueueItem] = db.write:
      val rid = QueueRepo.QueueItemId(id.value)
      // a row removed between the read and the write is not found, never a success over nothing
      QueueRepo.get(rid).toRight(QueueError.NotFound(id)).flatMap { cur =>
        if QueueRepo.update(Queue.applyPatch(cur, p)) then QueueRepo.get(rid).toRight(QueueError.NotFound(id))
        else Left(QueueError.NotFound(id))
      }

    def delete(id: QueueItemId)(using WritePermit): Either[QueueError, QueueItem] = db.write:
      val rid = QueueRepo.QueueItemId(id.value)
      QueueRepo.get(rid).toRight(QueueError.NotFound(id)).flatMap { item =>
        Either.cond(QueueRepo.delete(rid), item, QueueError.NotFound(id))
      }

    def replace(list: Kind, items: List[QueueAdd], mode: ImportMode, creator: Creator.New)(using
        WritePermit
    ): ImportCounts = db.write:
      val removed: Int = if mode == ImportMode.Replace then QueueRepo.deleteList(list).value else 0
      items.foreach { i =>
        val _ = QueueRepo.insert(list, i, creator)
      }
      ImportCounts(ImportCounts.Written(items.size), ImportCounts.Removed(removed))

  /** The work lists in memory; `clock` stands in for the database's `now()` (an item's made and done times). */
  final class InMemory(state: AtomicReference[WorkListState], clock: Clock) extends QueueService:
    private def now: Instant = Instant.now(clock)

    def list(list: Kind, scope: QueueScope): List[QueueItem] = state.get.listed(list, scope).toList
    def get(id: QueueItemId): Option[QueueItem] = state.get.get(id)

    def byIds(ids: List[QueueItemId]): List[QueueItem] =
      val wanted = ids.map(_.value: Int).toSet
      state.get.items.filter(i => wanted(i.id.value)).toList

    def naming(list: Kind, doi: Doi): List[QueueItem] = state.get.naming(list, doi).toList
    def add(list: Kind, item: QueueAdd, creator: Creator.New)(using WritePermit): QueueItem =
      state.updateAndGet(_.added(list, item, creator, now)).items.last

    def patch(id: QueueItemId, p: QueuePatch)(using WritePermit): Either[QueueError, QueueItem] =
      val after = state.updateAndGet(s => s.get(id).fold(s)(cur => s.updated(Queue.applyPatch(cur, p), now)))
      after.get(id).toRight(QueueError.NotFound(id))

    def delete(id: QueueItemId)(using WritePermit): Either[QueueError, QueueItem] =
      val before = state.getAndUpdate(_.removed(id))
      before.get(id).toRight(QueueError.NotFound(id))

    def replace(list: Kind, items: List[QueueAdd], mode: ImportMode, creator: Creator.New)(using
        WritePermit
    ): ImportCounts =
      val at = now
      val before = state.getAndUpdate { s =>
        val start = if mode == ImportMode.Replace then s.emptied(list) else s
        start.addedAll(list, items, creator, at)
      }
      ImportCounts(
        ImportCounts.Written(items.size),
        ImportCounts.Removed(if mode == ImportMode.Replace then before.listed(list, QueueScope.All).size else 0)
      )
