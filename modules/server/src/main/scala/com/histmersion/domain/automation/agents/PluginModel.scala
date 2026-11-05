package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.agents.VerbatimFailure
import com.histmersion.api.automation.Agents.ModelRef
import com.histmersion.plugin.*
import com.histmersion.pluginhost.PluginAi
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.ai.core.model.{AIModel, Capability}
import sttp.apispec.circe.*
import sttp.client4.Backend
import sttp.shared.Identity

/** The model type a plugin provider's calls are built with: every capability, as the provider (not sttp-ai) decides
  * what it can do.
  */
private[automation] object PluginModel extends AIModel with Capability.All:
  type ValueResult = ValueResult.T
  object ValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def value: com.histmersion.domain.automation.agents.PluginModel.ValueResult = "plugin"
