package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.corequota.Limits
import com.histmersion.domain.corequota.Purpose

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.plugin.Header

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.lint.{tolerated, LintRule}

/** The server's CORE budget as the plugin SPI lends it ([[UpstreamQuota]], `core`): the same [[CoreBudget]] the
  * new-source search's CORE index spends, under the limits `acquire.core.*` sets. A purpose is `lookup` or `search`.
  */
final class CoreQuotaLend(budget: CoreBudget, limits: Limits) extends UpstreamQuota:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def take(purpose: UpstreamQuota.TakePurpose): Either[Problem, Unit] =
    Purpose.values.find(_.wire.value == purpose.value) match
      case None =>
        Left(
          Problem.Unclassified(
            Legacy.Text(
              s"CORE's budget has no purpose '$purpose' (${Purpose.values.map(_.wire).mkString(", ")})"
            )
          )
        )
      case Some(p) => budget.take(limits, p).left.map(r => Problem.Unclassified(Legacy.Text(r.describe.value)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def observe(
      status: UpstreamQuota.ObserveStatus,
      headers: Map[Header.Name, Header.Value]
  ): Unit =
    budget.observe(
      limits,
      CoreQuota.StatusCode(status.value),
      headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def waitingUntil: Option[UpstreamQuota.WaitingUntil] =
    budget.state.block.map(b => UpstreamQuota.WaitingUntil(b.until.toString))
