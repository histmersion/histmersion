package com.histmersion
package api.automation

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Wording
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Autopilot:

  /** Why a goal is active, queued or waiting: the narration of the focus's decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
