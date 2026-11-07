package com.histmersion.domain.library.workflows

import com.histmersion.domain.EnrichSource
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema

/** The typed answers of the library's LLM steps (enrich-source, the acquisition sweep's ranking), one value each,
  * beside its pipelines until the library contributes its workflows (the engine's own are `AnswerSchemas`, research's
  * `ResearchAnswers`).
  */
object LibraryAnswers:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val classification: ResponseSchema[EnrichSource.Classification] =
    ResponseSchema.derived[EnrichSource.Classification]()
  val findings: ResponseSchema[EnrichSource.Findings] = ResponseSchema.derived[EnrichSource.Findings]()
  val notes: ResponseSchema[EnrichSource.Notes] = ResponseSchema.derived[EnrichSource.Notes]()
  val ranking: ResponseSchema[AcquisitionSweepRules.Ranking] = ResponseSchema.derived[AcquisitionSweepRules.Ranking]()

  /** Its step schemas, by name. */
  val all: List[(com.histmersion.domain.library.workflows.LibraryAnswers.Name, ResponseSchema[?])] = List(
    Name("classification") -> classification,
    Name("findings") -> findings,
    Name("notes") -> notes,
    Name("ranking") -> ranking
  )
