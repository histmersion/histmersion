package com.histmersion.domain.library.acquisition

import com.histmersion.api.Queue
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
  def of(items: List[Int]): Map[Int, AcquireCheckRow]

  /** The checks of every OPEN acquisition item. */
  def open: Map[Int, AcquireCheckRow]

  /** Record one check of `item`, its previous one replaced and the count kept. */
  def record(item: Int, at: Instant, generation: String, outcome: AcquireRecheck.Outcome, reason: String): Unit

  /** How many checks since `since`, per outcome. */
  def countSince(since: Instant): Map[String, Int]

  /** The sources holding these DOIs (lower-cased): the first citekey, and whether it has its original. */
  def heldByDoi(dois: List[String]): Map[String, AcquireRecheck.Held]

object AcquireCheckService:
  def apply(db: Transactor): AcquireCheckService = Implementation(db)

  final private class Implementation(db: Transactor) extends AcquireCheckService:
    def of(items: List[Int]): Map[Int, AcquireCheckRow] = db.read(AcquireCheckRepo.of(items))
    def open: Map[Int, AcquireCheckRow] = db.read(AcquireCheckRepo.open())
    def record(item: Int, at: Instant, generation: String, outcome: AcquireRecheck.Outcome, reason: String): Unit =
      db.transact(AcquireCheckRepo.record(item, at, generation, outcome, reason))
    def countSince(since: Instant): Map[String, Int] = db.read(AcquireCheckRepo.countSince(since))
    def heldByDoi(dois: List[String]): Map[String, AcquireRecheck.Held] = db.read(AcquireCheckRepo.heldByDoi(dois))

  /** The checks in memory; the acquisition list is the work lists' `queues`, the sources the library's `sources`. */
  final class InMemory(state: AtomicReference[AcquisitionState], queues: QueueService, sources: SourceService)
      extends AcquireCheckService:
    def of(items: List[Int]): Map[Int, AcquireCheckRow] = state.get.checks.filter((id, _) => items.contains(id))
    def open: Map[Int, AcquireCheckRow] =
      val ids = queues.list(Queue.Kind.Acquire, QueueScope.Open).map(_.id).toSet
      state.get.checks.filter((id, _) => ids(id))
    def record(item: Int, at: Instant, generation: String, outcome: AcquireRecheck.Outcome, reason: String): Unit =
      val _ = state.updateAndGet(
        _.modify(_.checks).using { cs =>
          val n = cs.get(item).fold(1)(_.checks + 1)
          cs.updated(item, AcquireCheckRow(item, at, generation, outcome.wire, reason, n))
        }
      )
    def countSince(since: Instant): Map[String, Int] =
      state.get.checks.values.filter(c => !c.at.isBefore(since)).groupBy(_.outcome).view.mapValues(_.size).toMap
    def heldByDoi(dois: List[String]): Map[String, AcquireRecheck.Held] =
      val wanted = dois.map(_.trim.toLowerCase).filter(_.nonEmpty).toSet
      sources.doiIndex
        .filter((d, _) => wanted(d))
        .map { (d, _) =>
          val ck = sources.citekeys.filter(c => sources.load(c).exists(_.doi.exists(_.toLowerCase == d))).min
          val has = sources.load(ck).exists(_.ocrMarkdown.exists(_.nonEmpty)) || sources.original(ck).isDefined
          d -> AcquireRecheck.Held(ck, has)
        }
