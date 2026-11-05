package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference

/** A metered upstream's budget as it is kept across restarts (CORE's), one per upstream. */
trait UpstreamQuotaService:
  def load(upstream: com.histmersion.domain.library.acquisition.UpstreamQuotaService.LoadUpstream): Option[CoreQuota.State]
  def save(upstream: com.histmersion.domain.library.acquisition.UpstreamQuotaService.SaveUpstream, state: CoreQuota.State): Unit

object UpstreamQuotaService:
  type LoadUpstream = LoadUpstream.T
  object LoadUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveUpstream = SaveUpstream.T
  object SaveUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): UpstreamQuotaService = Implementation(db)

  final private class Implementation(db: Transactor) extends UpstreamQuotaService:
    def load(upstream: com.histmersion.domain.library.acquisition.UpstreamQuotaService.Implementation.LoadUpstream): Option[CoreQuota.State] = db.read(UpstreamQuotaRepo.load(upstream))
    def save(upstream: com.histmersion.domain.library.acquisition.UpstreamQuotaService.Implementation.SaveUpstream, state: CoreQuota.State): Unit = db.transact(UpstreamQuotaRepo.save(upstream, state))

  object Implementation:
    type LoadUpstream = LoadUpstream.T
    object LoadUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveUpstream = SaveUpstream.T
    object SaveUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The budgets in memory: what a row keeps of one (the recent call slots are not kept). */
  final class InMemory(state: AtomicReference[AcquisitionState]) extends UpstreamQuotaService:
    def load(upstream: com.histmersion.domain.library.acquisition.UpstreamQuotaService.InMemory.LoadUpstream): Option[CoreQuota.State] = state.get.quotas.get(upstream)
    def save(upstream: com.histmersion.domain.library.acquisition.UpstreamQuotaService.InMemory.SaveUpstream, s: CoreQuota.State): Unit =
      val _ = state.updateAndGet(_.modify(_.quotas).using(_.updated(upstream, s.modify(_.recent).setTo(Vector.empty))))

  object InMemory:
    type LoadUpstream = LoadUpstream.T
    object LoadUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveUpstream = SaveUpstream.T
    object SaveUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
