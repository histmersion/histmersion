package com.histmersion
package domain.research.workflows

import com.histmersion.domain.{CompileArticle, FilterInference, PlanArticles, ReviewArticle}
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema
import io.github.iltotore.iron.constraint.any.Pure

/** The typed answers of research's LLM steps (compile, review, infer-filter, propose), one value each, beside its
  * pipelines (the engine's own are `AnswerSchemas`, the library's `LibraryAnswers`).
  */
object ResearchAnswers:
  type AnswerKind = AnswerKind.T
  object AnswerKind extends Newtype[String, Pure]

  val extract: ResponseSchema[CompileArticle.ExtractAnswer] = ResponseSchema.derived[CompileArticle.ExtractAnswer]()
  val draft: ResponseSchema[CompileArticle.DraftAnswer] = ResponseSchema.derived[CompileArticle.DraftAnswer]()
  val rules: ResponseSchema[ReviewArticle.RulesAnswer] = ResponseSchema.derived[ReviewArticle.RulesAnswer]()
  val coldReader: ResponseSchema[ReviewArticle.ColdReaderAnswer] =
    ResponseSchema.derived[ReviewArticle.ColdReaderAnswer]()
  val skeptic: ResponseSchema[ReviewArticle.SkepticAnswer] = ResponseSchema.derived[ReviewArticle.SkepticAnswer]()
  val recheck: ResponseSchema[ReviewArticle.RecheckAnswer] = ResponseSchema.derived[ReviewArticle.RecheckAnswer]()
  val fixer: ResponseSchema[ReviewArticle.FixerAnswer] = ResponseSchema.derived[ReviewArticle.FixerAnswer]()
  val filter: ResponseSchema[FilterInference.FilterAnswer] = ResponseSchema.derived[FilterInference.FilterAnswer]()
  val articles: ResponseSchema[PlanArticles.ArticlesAnswer] = ResponseSchema.derived[PlanArticles.ArticlesAnswer]()

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
