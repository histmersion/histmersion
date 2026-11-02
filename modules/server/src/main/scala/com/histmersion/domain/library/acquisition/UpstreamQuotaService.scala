package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference

/** A metered upstream's budget as it is kept across restarts (CORE's), one per upstream. */
trait UpstreamQuotaService:
  def load(upstream: String): Option[CoreQuota.State]
  def save(upstream: String, state: CoreQuota.State): Unit

object UpstreamQuotaService:
  def apply(db: Transactor): UpstreamQuotaService = Implementation(db)

  final private class Implementation(db: Transactor) extends UpstreamQuotaService:
    def load(upstream: String): Option[CoreQuota.State] = db.read(UpstreamQuotaRepo.load(upstream))
    def save(upstream: String, state: CoreQuota.State): Unit = db.transact(UpstreamQuotaRepo.save(upstream, state))

  /** The budgets in memory: what a row keeps of one (the recent call slots are not kept). */
  final class InMemory(state: AtomicReference[AcquisitionState]) extends UpstreamQuotaService:
    def load(upstream: String): Option[CoreQuota.State] = state.get.quotas.get(upstream)
    def save(upstream: String, s: CoreQuota.State): Unit =
      val _ = state.updateAndGet(_.modify(_.quotas).using(_.updated(upstream, s.modify(_.recent).setTo(Vector.empty))))
