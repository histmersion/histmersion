package com.histmersion.domain.library.acquisition

import com.histmersion.api.automation.Queue
import com.histmersion.domain.AcquireRecheck
import com.histmersion.domain.automation.worklists.{QueueScope, QueueService}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** The acquisition list's re-check: one record per acquisition item that was checked, and what the corpus holds of the
  * DOIs the items name.
  */
trait AcquireCheckService:
  /** The checks of these items (any that has none is absent). */
  def of(items: List[com.histmersion.domain.library.acquisition.AcquireCheckService.OfItems]): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.OfResultPart1, AcquireCheckRow]

  /** The checks of every OPEN acquisition item. */
  def open: Map[com.histmersion.domain.library.acquisition.AcquireCheckService.OpenResultPart1, AcquireCheckRow]

  /** Record one check of `item`, its previous one replaced and the count kept. */
  def record(item: com.histmersion.domain.library.acquisition.AcquireCheckService.RecordItem, at: Instant, generation: com.histmersion.domain.library.acquisition.AcquireCheckService.RecordGeneration, outcome: AcquireRecheck.Outcome, reason: com.histmersion.domain.library.acquisition.AcquireCheckService.RecordReason): Unit

  /** How many checks since `since`, per outcome. */
  def countSince(since: Instant): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.CountSinceResultPart1, com.histmersion.domain.library.acquisition.AcquireCheckService.CountSinceResultPart2]

  /** The sources holding these DOIs (lower-cased): the first citekey, and whether it has its original. */
  def heldByDoi(dois: List[com.histmersion.domain.library.acquisition.AcquireCheckService.HeldByDoiDois]): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.HeldByDoiResultPart1, AcquireRecheck.Held]

object AcquireCheckService:
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfItems = OfItems.T
  object OfItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenResultPart1 = OpenResultPart1.T
  object OpenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordItem = RecordItem.T
  object RecordItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordGeneration = RecordGeneration.T
  object RecordGeneration extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordReason = RecordReason.T
  object RecordReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSinceResultPart1 = CountSinceResultPart1.T
  object CountSinceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSinceResultPart2 = CountSinceResultPart2.T
  object CountSinceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldByDoiResultPart1 = HeldByDoiResultPart1.T
  object HeldByDoiResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldByDoiDois = HeldByDoiDois.T
  object HeldByDoiDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AcquireCheckService = Implementation(db)

  final private class Implementation(db: Transactor) extends AcquireCheckService:
    def of(items: List[com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.OfItems]): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.OfResultPart1, AcquireCheckRow] = db.read(AcquireCheckRepo.of(items))
    def open: Map[com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.OpenResultPart1, AcquireCheckRow] = db.read(AcquireCheckRepo.open())
    def record(item: com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.RecordItem, at: Instant, generation: com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.RecordGeneration, outcome: AcquireRecheck.Outcome, reason: com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.RecordReason): Unit =
      db.transact(AcquireCheckRepo.record(item, at, generation, outcome, reason))
    def countSince(since: Instant): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.CountSinceResultPart1, com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.CountSinceResultPart2] = db.read(AcquireCheckRepo.countSince(since))
    def heldByDoi(dois: List[com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.HeldByDoiDois]): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.Implementation.HeldByDoiResultPart1, AcquireRecheck.Held] = db.read(AcquireCheckRepo.heldByDoi(dois))

  object Implementation:
    type OfResultPart1 = OfResultPart1.T
    object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfItems = OfItems.T
    object OfItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenResultPart1 = OpenResultPart1.T
    object OpenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordItem = RecordItem.T
    object RecordItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordGeneration = RecordGeneration.T
    object RecordGeneration extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordReason = RecordReason.T
    object RecordReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSinceResultPart1 = CountSinceResultPart1.T
    object CountSinceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSinceResultPart2 = CountSinceResultPart2.T
    object CountSinceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeldByDoiResultPart1 = HeldByDoiResultPart1.T
    object HeldByDoiResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeldByDoiDois = HeldByDoiDois.T
    object HeldByDoiDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The checks in memory; the acquisition list is the work lists' `queues`, the sources the library's `sources`. */
  final class InMemory(state: AtomicReference[AcquisitionState], queues: QueueService, sources: SourceService)
      extends AcquireCheckService:
    def of(items: List[com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.OfItems]): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.OfResultPart1, AcquireCheckRow] = state.get.checks.filter((id, _) => items.contains(id))
    def open: Map[com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.OpenResultPart1, AcquireCheckRow] =
      val ids = queues.list(Queue.Kind.Acquire, QueueScope.Open).map(_.id).toSet
      state.get.checks.filter((id, _) => ids(id))
    def record(item: com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.RecordItem, at: Instant, generation: com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.RecordGeneration, outcome: AcquireRecheck.Outcome, reason: com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.RecordReason): Unit =
      val _ = state.updateAndGet(
        _.modify(_.checks).using { cs =>
          val n = cs.get(item).fold(1)(_.checks + 1)
          cs.updated(item, AcquireCheckRow(item, at, generation, outcome.wire, reason, n))
        }
      )
    def countSince(since: Instant): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.CountSinceResultPart1, com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.CountSinceResultPart2] =
      state.get.checks.values.filter(c => !c.at.isBefore(since)).groupBy(_.outcome).view.mapValues(_.size).toMap
    def heldByDoi(dois: List[com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.HeldByDoiDois]): Map[com.histmersion.domain.library.acquisition.AcquireCheckService.InMemory.HeldByDoiResultPart1, AcquireRecheck.Held] =
      val wanted = dois.map(_.trim.toLowerCase).filter(_.nonEmpty).toSet
      sources.doiIndex
        .filter((d, _) => wanted(d))
        .map { (d, _) =>
          val ck = sources.citekeys.filter(c => sources.load(c).exists(_.doi.exists(_.toLowerCase == d))).min
          val has = sources.load(ck).exists(_.ocrMarkdown.exists(_.nonEmpty)) || sources.original(ck).isDefined
          d -> AcquireRecheck.Held(ck, has)
        }

  object InMemory:
    type OfResultPart1 = OfResultPart1.T
    object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfItems = OfItems.T
    object OfItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenResultPart1 = OpenResultPart1.T
    object OpenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordItem = RecordItem.T
    object RecordItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordGeneration = RecordGeneration.T
    object RecordGeneration extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordReason = RecordReason.T
    object RecordReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSinceResultPart1 = CountSinceResultPart1.T
    object CountSinceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSinceResultPart2 = CountSinceResultPart2.T
    object CountSinceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeldByDoiResultPart1 = HeldByDoiResultPart1.T
    object HeldByDoiResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeldByDoiDois = HeldByDoiDois.T
    object HeldByDoiDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
