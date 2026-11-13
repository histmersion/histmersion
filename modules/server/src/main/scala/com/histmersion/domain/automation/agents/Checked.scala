package com.histmersion
package domain.automation.agents

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** An LLM answer after code validation: the value to checkpoint, and what was dropped or doubted on the way (run
  * warnings, not failures).
  */
final case class Checked[O](value: O, warnings: List[Checked.Warnings] = Nil)

object Checked:
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]
