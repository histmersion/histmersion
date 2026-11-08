package com.histmersion
package plugin.testkit

import com.histmersion.plugin.*
import com.histmersion.pluginhost.PluginAi

/** One model call to an AI provider as the server makes it: the provider's own check of the model first, then the call
  * — an exception or a `null` being the provider's failure.
  *
  * @param turn
  *   `None` when the check said no
  */
final case class AiReport(check: Either[PluginAi.Problem, Unit], turn: Option[Either[AiFailure, AiTurn]], log: List[LogLine])
