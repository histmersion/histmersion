package com.histmersion
package domain.library.workflows

import com.histmersion.domain.enrichsource.Classification
import com.histmersion.domain.enrichsource.Findings
import com.histmersion.domain.enrichsource.Notes
import com.histmersion.domain.automation.agents.SttpAiJson.given

import com.histmersion.domain.EnrichSource
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The typed answers of the library's LLM steps (enrich-source, the acquisition sweep's ranking), one value each,
  * beside its pipelines until the library contributes its workflows (the engine's own are `AnswerSchemas`, research's
  * `ResearchAnswers`).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object LibraryAnswers:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val classification: ResponseSchema[Classification] =
    ResponseSchema.derived[Classification]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val findings: ResponseSchema[Findings] = ResponseSchema.derived[Findings]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val notes: ResponseSchema[Notes] = ResponseSchema.derived[Notes]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val ranking: ResponseSchema[AcquisitionSweepRules.Ranking] = ResponseSchema.derived[AcquisitionSweepRules.Ranking]()

  /** Its step schemas, by name. */
  val all: List[(Name, ResponseSchema[?])] = List(
    Name("classification") -> classification,
    Name("findings") -> findings,
    Name("notes") -> notes,
    Name("ranking") -> ranking
  )
