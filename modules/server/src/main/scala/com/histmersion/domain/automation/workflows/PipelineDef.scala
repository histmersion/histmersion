package com.histmersion
package domain.automation.workflows

import com.histmersion.domain.automation.agents.{Halt, RunCtx}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A pipeline: Scala code that calls [[RunCtx.code]] and [[RunCtx.llm]] steps in a FIXED order (the step number is the
  * order, so a resumed run lines its steps up with its checkpoints). Returns a one-line summary for the run's log
  * entry.
  */
trait PipelineDef:
  def pipeline: Pipeline
  def run(ctx: RunCtx): Either[Halt, PipelineDef.Summary]

object PipelineDef:
  type Summary = Summary.T
  object Summary extends RefinedType[String, Pure]
