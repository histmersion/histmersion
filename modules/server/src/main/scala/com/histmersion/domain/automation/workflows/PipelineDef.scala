package com.histmersion.domain.automation.workflows

import com.histmersion.domain.automation.agents.{Halt, RunCtx}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*

/** A pipeline: Scala code that calls [[RunCtx.code]] and [[RunCtx.llm]] steps in a FIXED order (the step number is the
  * order, so a resumed run lines its steps up with its checkpoints). Returns a one-line summary for the run's log
  * entry.
  */
trait PipelineDef:
  def pipeline: Pipeline
  def run(ctx: RunCtx): Either[Halt, com.histmersion.domain.automation.workflows.PipelineDef.RunResultPart2]

object PipelineDef:
  type RunResultPart2 = RunResultPart2.T
  object RunResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
