package com.histmersion.domain.automation.agents

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*

/** An LLM answer after code validation: the value to checkpoint, and what was dropped or doubted on the way (run
  * warnings, not failures).
  */
final case class Checked[O](value: O, warnings: List[String] = Nil)
