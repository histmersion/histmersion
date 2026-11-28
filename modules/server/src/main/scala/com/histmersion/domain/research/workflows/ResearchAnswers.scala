package com.histmersion
package domain.research.workflows

import com.histmersion.domain.research.workflows.ExtractAnswer
import com.histmersion.domain.research.workflows.DraftAnswer
import com.histmersion.domain.research.workflows.RulesAnswer
import com.histmersion.domain.research.workflows.ColdReaderAnswer
import com.histmersion.domain.research.workflows.SkepticAnswer
import com.histmersion.domain.research.workflows.RecheckAnswer
import com.histmersion.domain.research.workflows.FixerAnswer
import com.histmersion.domain.FilterAnswer
import com.histmersion.domain.ArticlesAnswer
import com.histmersion.domain.automation.agents.SttpAiJson
import com.histmersion.domain.automation.agents.SttpAiJson.given
import com.histmersion.api.research.workflows as wire

import com.histmersion.domain.{CompileArticle, ReviewArticle}
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The typed answers of research's LLM steps (compile, review, infer-filter, propose), one value each, beside its
  * pipelines (the engine's own are `AnswerSchemas`, the library's `LibraryAnswers`).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object ResearchAnswers:
  type AnswerKind = AnswerKind.T
  object AnswerKind extends Newtype[String, Pure]

  val extract: ResponseSchema[ExtractAnswer] =
    SttpAiJson.answerOf[ExtractAnswer, wire.ExtractionAnswer](ResearchTransformers.extractAnswer)
  val draft: ResponseSchema[DraftAnswer] =
    SttpAiJson.answerOf[DraftAnswer, wire.WrittenDraft](ResearchTransformers.draftAnswer)
  val rules: ResponseSchema[RulesAnswer] =
    SttpAiJson.answerOf[RulesAnswer, wire.GuideReview](ResearchTransformers.rulesAnswer)
  val coldReader: ResponseSchema[ColdReaderAnswer] =
    SttpAiJson.answerOf[ColdReaderAnswer, wire.ColdReaderAnswer](ResearchTransformers.coldReaderAnswer)
  val skeptic: ResponseSchema[SkepticAnswer] =
    SttpAiJson.answerOf[SkepticAnswer, wire.SkepticAnswer](ResearchTransformers.skepticAnswer)
  val recheck: ResponseSchema[RecheckAnswer] =
    SttpAiJson.answerOf[RecheckAnswer, wire.RecheckAnswer](ResearchTransformers.recheckAnswer)
  val fixer: ResponseSchema[FixerAnswer] =
    SttpAiJson.answerOf[FixerAnswer, wire.FixerAnswer](ResearchTransformers.fixerAnswer)
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val filter: ResponseSchema[FilterAnswer] = ResponseSchema.derived[FilterAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val articles: ResponseSchema[ArticlesAnswer] = ResponseSchema.derived[ArticlesAnswer]()

  /** Its step schemas, by name. */
  val all: List[(AnswerKind, ResponseSchema[?])] = List(
    AnswerKind("extract") -> extract,
    AnswerKind("draft") -> draft,
    AnswerKind("rules") -> rules,
    AnswerKind("cold-reader") -> coldReader,
    AnswerKind("skeptic") -> skeptic,
    AnswerKind("recheck") -> recheck,
    AnswerKind("fixer") -> fixer,
    AnswerKind("filter") -> filter,
    AnswerKind("articles") -> articles
  )
