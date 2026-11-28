package com.histmersion
package domain.library.workflows

import com.histmersion.domain.library.workflows.Classification
import com.histmersion.domain.library.workflows.Findings
import com.histmersion.domain.library.workflows.Notes
import com.histmersion.domain.automation.agents.SttpAiJson
import com.histmersion.domain.automation.agents.SttpAiJson.given
import com.histmersion.api.library.workflows as wire
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema

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

  val classification: ResponseSchema[Classification] =
    answer[Classification, wire.Classification](EnrichmentTransformers.classificationAnswer)
  val findings: ResponseSchema[Findings] =
    answer[Findings, wire.ProposedFindings](EnrichmentTransformers.findingsAnswer)
  val notes: ResponseSchema[Notes] = answer[Notes, wire.Notes](EnrichmentTransformers.notesAnswer)
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val ranking: ResponseSchema[AcquisitionSweepRules.Ranking] = ResponseSchema.derived[AcquisitionSweepRules.Ranking]()

  /** Its step schemas, by name. */
  val all: List[(Name, ResponseSchema[?])] = List(
    Name("classification") -> classification,
    Name("findings") -> findings,
    Name("notes") -> notes,
    Name("ranking") -> ranking
  )

  private def answer[D, W](codec: JsonValueCodec[D])(using schema: Schema[W]): ResponseSchema[D] =
    ResponseSchema
      .fromProduct((TapirSchemaToJsonSchema(schema, markOptionsAsNullable = true), SttpAiJson.codec(using codec), None))
      .asInstanceOf[ResponseSchema[D]]
