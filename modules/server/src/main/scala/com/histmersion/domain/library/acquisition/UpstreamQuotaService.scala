package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.CoreQuota
import com.histmersion.domain.automation.ProviderKind
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}

/** A metered upstream's budget as it is kept across restarts (CORE's), one per upstream. */
trait UpstreamQuotaService:
  def load(upstream: UpstreamQuotaService.Upstream): Option[CoreQuota.State]
  def save(upstream: UpstreamQuotaService.Upstream, state: CoreQuota.State)(using WritePermit): Unit

object UpstreamQuotaService:
  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]

  def apply(db: Transactor): UpstreamQuotaService = Implementation(db)

  final private class Implementation(db: Transactor) extends UpstreamQuotaService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def load(upstream: Upstream): Option[CoreQuota.State] =
      db.read(UpstreamQuotaRepo.load(ProviderKind.Wire(upstream.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def save(upstream: Upstream, state: CoreQuota.State)(using WritePermit): Unit =
      db.write(UpstreamQuotaRepo.save(ProviderKind.Wire(upstream.value), state))

  /** The budgets in memory: what a row keeps of one (the recent call slots are not kept). */
  final class InMemory(state: AtomicReference[AcquisitionState]) extends UpstreamQuotaService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def load(upstream: Upstream): Option[CoreQuota.State] =
      state.get.quotas.get(AcquisitionState.QuotasKey(upstream.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def save(upstream: Upstream, s: CoreQuota.State)(using WritePermit): Unit =
      val _ = state.updateAndGet(
        _.modify(_.quotas)
          .using(_.updated(AcquisitionState.QuotasKey(upstream.value), s.modify(_.recent).setTo(Vector.empty)))
      )
