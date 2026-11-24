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
import io.scalaland.chimney.dsl.*
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait AcquireCheckService:
  def of(items: List[AcquireAttemptRow.Id]): Map[AcquireAttemptRow.Id, List[AcquireAttempt]]

  def open: Map[AcquireAttemptRow.Id, List[AcquireAttempt]]

  def record(item: AcquireAttemptRow.Id, attempt: AcquireAttempt, kept: AcquireAttempt.Kept)(using WritePermit): Unit

  def countSince(since: Instant): Map[AcquireRecheck.Outcome.Wire, RowCount]

  def heldByDoi(dois: List[Doi]): Map[Doi, AcquireRecheck.Held]

object AcquireCheckService:
  def apply(db: Transactor): AcquireCheckService = Implementation(db)

  final private class Implementation(db: Transactor) extends AcquireCheckService:
    def of(items: List[AcquireAttemptRow.Id]): Map[AcquireAttemptRow.Id, List[AcquireAttempt]] =
      db.read(AcquireCheckRepo.of(items))
    def open: Map[AcquireAttemptRow.Id, List[AcquireAttempt]] = db.read(AcquireCheckRepo.open())
    def record(item: AcquireAttemptRow.Id, attempt: AcquireAttempt, kept: AcquireAttempt.Kept)(using
        WritePermit
    ): Unit =
      db.write(AcquireCheckRepo.record(item, attempt, kept))
    def countSince(since: Instant): Map[AcquireRecheck.Outcome.Wire, RowCount] =
      db.read(AcquireCheckRepo.countSince(since))
    def heldByDoi(dois: List[Doi]): Map[Doi, AcquireRecheck.Held] = db.read(AcquireCheckRepo.heldByDoi(dois))

  final class InMemory(state: AtomicReference[AcquisitionState], queues: QueueService, sources: SourceService)
      extends AcquireCheckService:
    def of(items: List[AcquireAttemptRow.Id]): Map[AcquireAttemptRow.Id, List[AcquireAttempt]] =
      val wanted = items.toSet
      state.get.attempts.filter((k, as) => wanted(k) && as.nonEmpty)
    def open: Map[AcquireAttemptRow.Id, List[AcquireAttempt]] =
      of(queues.list(Queue.Kind.Acquire, QueueScope.Open).map(_.id.transformInto[AcquireAttemptRow.Id]))
    def record(item: AcquireAttemptRow.Id, attempt: AcquireAttempt, kept: AcquireAttempt.Kept)(using
        WritePermit
    ): Unit =
      val _ = state.updateAndGet(
        _.modify(_.attempts).using(all =>
          all.updated(item, kept.keep((attempt :: all.getOrElse(item, Nil)).sortBy(_.at).reverse))
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def countSince(since: Instant): Map[AcquireRecheck.Outcome.Wire, RowCount] =
      state.get.attempts.values.flatten
        .filter(a => !a.at.isBefore(since))
        .flatMap(a => AcquireRecheck.Outcome.of(a.outcome).map(_.wire))
        .groupBy(identity)
        .view
        .mapValues(ws => RowCount(ws.size))
        .toMap
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
