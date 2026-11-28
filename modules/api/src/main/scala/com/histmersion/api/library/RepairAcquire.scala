package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

object RepairAcquire:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  val Move = "move"
  val Remove = "remove"
  val Keep = "keep"
