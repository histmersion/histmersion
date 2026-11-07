package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.domain.library.acquisition.CoreBudget

/** The server's CORE budget as the plugin SPI lends it ([[UpstreamQuota]], `core`): the same [[CoreBudget]] the
  * new-source search's CORE index spends, under the limits `acquire.core.*` sets. A purpose is `lookup` or `search`.
  */
final class CoreQuotaLend(budget: CoreBudget, limits: CoreQuota.Limits) extends UpstreamQuota:
  def take(purpose: com.histmersion.domain.library.acquisition.CoreQuotaLend.TakePurpose): Either[com.histmersion.domain.library.acquisition.CoreQuotaLend.Problem, Unit] =
    CoreQuota.Purpose.values.find(_.wire == purpose) match
      case None =>
        Left(s"CORE's budget has no purpose '$purpose' (${CoreQuota.Purpose.values.map(_.wire).mkString(", ")})")
      case Some(p) => budget.take(limits, p).left.map(_.describe)
  def observe(status: com.histmersion.domain.library.acquisition.CoreQuotaLend.ObserveStatus, headers: Map[com.histmersion.domain.library.acquisition.CoreQuotaLend.ObserveHeadersPart1, com.histmersion.domain.library.acquisition.CoreQuotaLend.ObserveHeadersPart2]): Unit = budget.observe(limits, status, headers)
  override def waitingUntil: Option[String] = budget.state.block.map(_.until.toString)

object CoreQuotaLend:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TakePurpose = TakePurpose.T
  object TakePurpose extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ObserveStatus = ObserveStatus.T
  object ObserveStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ObserveHeadersPart1 = ObserveHeadersPart1.T
  object ObserveHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ObserveHeadersPart2 = ObserveHeadersPart2.T
  object ObserveHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
