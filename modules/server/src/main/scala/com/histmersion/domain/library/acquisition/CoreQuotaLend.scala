package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.domain.library.acquisition.CoreBudget

/** The server's CORE budget as the plugin SPI lends it ([[UpstreamQuota]], `core`): the same [[CoreBudget]] the
  * new-source search's CORE index spends, under the limits `acquire.core.*` sets. A purpose is `lookup` or `search`.
  */
final class CoreQuotaLend(budget: CoreBudget, limits: CoreQuota.Limits) extends UpstreamQuota:
  def take(purpose: String): Either[String, Unit] =
    CoreQuota.Purpose.values.find(_.wire == purpose) match
      case None =>
        Left(s"CORE's budget has no purpose '$purpose' (${CoreQuota.Purpose.values.map(_.wire).mkString(", ")})")
      case Some(p) => budget.take(limits, p).left.map(_.describe)
  def observe(status: Int, headers: Map[String, String]): Unit = budget.observe(limits, status, headers)
  override def waitingUntil: Option[String] = budget.state.block.map(_.until.toString)
