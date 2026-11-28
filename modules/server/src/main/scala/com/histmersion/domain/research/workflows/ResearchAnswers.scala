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
import com.histmersion.domain.automation.agents.SttpAiJson.given

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

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val extract: ResponseSchema[ExtractAnswer] = ResponseSchema.derived[ExtractAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val draft: ResponseSchema[DraftAnswer] = ResponseSchema.derived[DraftAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val rules: ResponseSchema[RulesAnswer] = ResponseSchema.derived[RulesAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val coldReader: ResponseSchema[ColdReaderAnswer] =
    ResponseSchema.derived[ColdReaderAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val skeptic: ResponseSchema[SkepticAnswer] = ResponseSchema.derived[SkepticAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val recheck: ResponseSchema[RecheckAnswer] = ResponseSchema.derived[RecheckAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val fixer: ResponseSchema[FixerAnswer] = ResponseSchema.derived[FixerAnswer]()
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
