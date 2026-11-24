package com.histmersion
package domain.research.workflows
import com.histmersion.domain.automation.agents.SttpAiJson.given

import com.histmersion.domain.{CompileArticle, FilterInference, PlanArticles, ReviewArticle}
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The typed answers of research's LLM steps (compile, review, infer-filter, propose), one value each, beside its
  * pipelines (the engine's own are `AnswerSchemas`, the library's `LibraryAnswers`).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object ResearchAnswers:
  type AnswerKind = AnswerKind.T
  object AnswerKind extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val extract: ResponseSchema[CompileArticle.ExtractAnswer] = ResponseSchema.derived[CompileArticle.ExtractAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val draft: ResponseSchema[CompileArticle.DraftAnswer] = ResponseSchema.derived[CompileArticle.DraftAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val rules: ResponseSchema[ReviewArticle.RulesAnswer] = ResponseSchema.derived[ReviewArticle.RulesAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val coldReader: ResponseSchema[ReviewArticle.ColdReaderAnswer] =
    ResponseSchema.derived[ReviewArticle.ColdReaderAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val skeptic: ResponseSchema[ReviewArticle.SkepticAnswer] = ResponseSchema.derived[ReviewArticle.SkepticAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val recheck: ResponseSchema[ReviewArticle.RecheckAnswer] = ResponseSchema.derived[ReviewArticle.RecheckAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val fixer: ResponseSchema[ReviewArticle.FixerAnswer] = ResponseSchema.derived[ReviewArticle.FixerAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val filter: ResponseSchema[FilterInference.FilterAnswer] = ResponseSchema.derived[FilterInference.FilterAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
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
