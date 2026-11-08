package com.histmersion
package domain.library.acquisition

import com.histmersion.api.automation.Queue
import com.histmersion.domain.AcquireRecheck
import com.histmersion.domain.automation.worklists.{QueueScope, QueueService}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.library.Doi

/** The acquisition list's re-check: one record per acquisition item that was checked, and what the corpus holds of the
  * DOIs the items name.
  */
trait AcquireCheckService:
  /** The checks of these items (any that has none is absent). */
  def of(items: List[AcquireCheckRow.Item]): Map[AcquireCheckRow.Item, AcquireCheckRow]

  /** The checks of every OPEN acquisition item. */
  def open: Map[AcquireCheckRow.Item, AcquireCheckRow]

  /** Record one check of `item`, its previous one replaced and the count kept. */
  def record(
      item: AcquireCheckRow.Item,
      at: Instant,
      generation: AcquireRecheck.LastCheck.Generation,
      outcome: AcquireRecheck.Outcome,
      reason: AcquireCheckRow.Reason
  ): Unit

  /** How many checks since `since`, per outcome. */
  def countSince(since: Instant): Map[AcquireRecheck.Outcome.Wire, RowCount]

  /** The sources holding these DOIs (lower-cased): the first citekey, and whether it has its original. */
  def heldByDoi(dois: List[Doi]): Map[Doi, AcquireRecheck.Held]

object AcquireCheckService:
  def apply(db: Transactor): AcquireCheckService = Implementation(db)

  final private class Implementation(db: Transactor) extends AcquireCheckService:
    def of(items: List[AcquireCheckRow.Item]): Map[AcquireCheckRow.Item, AcquireCheckRow] =
      db.read(AcquireCheckRepo.of(items))
    def open: Map[AcquireCheckRow.Item, AcquireCheckRow] = db.read(AcquireCheckRepo.open())
    def record(
        item: AcquireCheckRow.Item,
        at: Instant,
        generation: AcquireRecheck.LastCheck.Generation,
        outcome: AcquireRecheck.Outcome,
        reason: AcquireCheckRow.Reason
    ): Unit =
      db.transact(AcquireCheckRepo.record(item, at, generation, outcome, reason))
    def countSince(since: Instant): Map[AcquireRecheck.Outcome.Wire, RowCount] =
      db.read(AcquireCheckRepo.countSince(since))
    def heldByDoi(dois: List[Doi]): Map[Doi, AcquireRecheck.Held] = db.read(AcquireCheckRepo.heldByDoi(dois))

  /** The checks in memory; the acquisition list is the work lists' `queues`, the sources the library's `sources`. */
  final class InMemory(state: AtomicReference[AcquisitionState], queues: QueueService, sources: SourceService)
      extends AcquireCheckService:
    def of(items: List[AcquireCheckRow.Item]): Map[AcquireCheckRow.Item, AcquireCheckRow] =
      state.get.checks.values.filter(r => items.contains(r.item)).map(r => r.item -> r).toMap
    def open: Map[AcquireCheckRow.Item, AcquireCheckRow] =
      val ids = queues.list(Queue.Kind.Acquire, QueueScope.Open).map(i => AcquireCheckRow.Item(i.id.value)).toSet
      state.get.checks.values.filter(r => ids(r.item)).map(r => r.item -> r).toMap
    def record(
        item: AcquireCheckRow.Item,
        at: Instant,
        generation: AcquireRecheck.LastCheck.Generation,
        outcome: AcquireRecheck.Outcome,
        reason: AcquireCheckRow.Reason
    ): Unit =
      val _ = state.updateAndGet(
        _.modify(_.checks).using { cs =>
          val key = AcquisitionState.ChecksKey(item.value)
          val n = cs.get(key).fold(AcquireCheckRow.Checks(1))(c => AcquireCheckRow.Checks(c.checks.value + 1))
          cs.updated(key, AcquireCheckRow(item, at, generation, outcome.wire, reason, n))
        }
      )
    def countSince(since: Instant): Map[AcquireRecheck.Outcome.Wire, RowCount] =
      state.get.checks.values
        .filter(c => !c.at.isBefore(since))
        .groupBy(_.outcome)
        .view
        .mapValues(cs => RowCount(cs.size))
        .toMap
    def heldByDoi(dois: List[Doi]): Map[Doi, AcquireRecheck.Held] =
      val wanted = dois.map(_.value.trim.toLowerCase).filter(_.nonEmpty).toSet
      sources.doiIndex
        .filter((d, _) => wanted(d.value))
        .map { (d, _) =>
          val ck = sources.citekeys
            .filter(c => sources.load(c).exists(_.doi.exists(_.value.toLowerCase == d.value)))
            .minBy[String](_.value)
          val has = sources.load(ck).exists(_.ocrMarkdown.exists(_.value.nonEmpty)) || sources.original(ck).isDefined
          d -> AcquireRecheck.Held(ck, AcquireRecheck.Held.HasOriginal(has))
        }
