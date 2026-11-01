package com.histmersion.server.agents

import com.histmersion.domain.EnrichSource
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema

/** The typed answers of the library's LLM steps (enrich-source), one value each, beside its pipelines until the library
  * contributes its workflows (the engine's own are `AnswerSchemas`, research's `ResearchAnswers`).
  */
object LibraryAnswers:
  val classification: ResponseSchema[EnrichSource.Classification] =
    ResponseSchema.derived[EnrichSource.Classification]()
  val findings: ResponseSchema[EnrichSource.Findings] = ResponseSchema.derived[EnrichSource.Findings]()
  val notes: ResponseSchema[EnrichSource.Notes] = ResponseSchema.derived[EnrichSource.Notes]()

  /** Its step schemas, by name. */
  val all: List[(String, ResponseSchema[?])] = List(
    "classification" -> classification,
    "findings" -> findings,
    "notes" -> notes
  )
