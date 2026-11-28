package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.AgentMode

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class AutopilotSet(
    on: AutopilotSet.On,
    by: Option[AutopilotSet.By] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object AutopilotSet:
  export AgentMode.Step.Autopilot.On
  type By = By.T
  object By extends Newtype[String, Pure]
