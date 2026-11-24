package com.histmersion
package domain.automation.agents

import com.histmersion.domain.automation.agents.VerbatimFailure
import com.histmersion.api.automation.Agents.ModelRef
import com.histmersion.plugin.*
import com.histmersion.pluginhost.PluginAi
import sttp.ai.core.agent.*
import sttp.ai.core.model.{AIModel, Capability}
import sttp.client4.Backend
import sttp.shared.Identity
import com.histmersion.lint.{LintRule, tolerated}

/** The model type a plugin provider's calls are built with: every capability, as the provider (not sttp-ai) decides
  * what it can do.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
private[automation] object PluginModel extends AIModel with Capability.All:

  def value: String = "plugin"
