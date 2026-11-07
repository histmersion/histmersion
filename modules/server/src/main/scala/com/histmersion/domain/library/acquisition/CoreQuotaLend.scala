package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.domain.library.acquisition.CoreBudget

/** The server's CORE budget as the plugin SPI lends it ([[UpstreamQuota]], `core`): the same [[CoreBudget]] the
  * new-source search's CORE index spends, under the limits `acquire.core.*` sets. A purpose is `lookup` or `search`.
  */
final class CoreQuotaLend(budget: CoreBudget, limits: CoreQuota.Limits) extends UpstreamQuota:
  def take(purpose: UpstreamQuota.TakePurpose): Either[UpstreamQuota.TakeResultPart1, Unit] =
    CoreQuota.Purpose.values.find(_.wire.value == purpose.value) match
      case None =>
        Left(UpstreamQuota.TakeResultPart1(s"CORE's budget has no purpose '$purpose' (${CoreQuota.Purpose.values.map(_.wire).mkString(", ")})"))
      case Some(p) => budget.take(limits, p).left.map(r => UpstreamQuota.TakeResultPart1(r.describe.value))
  def observe(status: UpstreamQuota.ObserveStatus, headers: Map[UpstreamQuota.ObserveHeadersPart1, UpstreamQuota.ObserveHeadersPart2]): Unit =
    budget.observe(
      limits,
      CoreQuota.StatusCode(status.value),
      headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
    )
  override def waitingUntil: Option[UpstreamQuota.WaitingUntilResult] =
    budget.state.block.map(b => UpstreamQuota.WaitingUntilResult(b.until.toString))
