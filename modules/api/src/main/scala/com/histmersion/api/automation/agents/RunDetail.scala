package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.agents.StepInfo
import com.histmersion.api.automation.agents.MessageInfo
import com.histmersion.api.automation.agents.RunInfo

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RunDetail(run: RunInfo, steps: List[StepInfo], messages: List[MessageInfo])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema,
      CanEqual
