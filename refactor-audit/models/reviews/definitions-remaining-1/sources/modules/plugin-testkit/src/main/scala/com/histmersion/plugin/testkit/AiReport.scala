package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One model call to an AI provider as the server makes it: the provider's own check of the model first, then the call
  * — an exception or a `null` being the provider's failure.
  *
  * @param turn
  *   `None` when the check said no
  */
final case class AiReport(check: Either[AiReport.CheckPart1, Unit], turn: Option[Either[AiFailure, AiTurn]], log: List[LogLine])

object AiReport:
  type CheckPart1 = CheckPart1.T
  object CheckPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
