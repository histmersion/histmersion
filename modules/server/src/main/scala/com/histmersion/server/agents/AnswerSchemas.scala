package com.histmersion.server.agents

import com.histmersion.core.{CompileArticle, EnrichSource, FilterInference, HintProposals, PlanArticles, PlanGoal, ReviewArticle,
  Scheduling}
import com.histmersion.core.Agents.ProbeAnswer
import sttp.ai.core.agent.ResponseSchema

/** The typed answer of every LLM step the server runs, one value each. A pipeline names its step's
  * schema here rather than deriving it at the call, so what a provider is sent can be checked over ALL
  * of them (AnswerSchemasSuite: the strict form Codex needs, a `null` decoding as an absent field) —
  * and that suite fails when a `ResponseSchema.derived` appears anywhere else. */
object AnswerSchemas:
  val probe: ResponseSchema[ProbeAnswer]                          = ResponseSchema.derived[ProbeAnswer]()
  val classification: ResponseSchema[EnrichSource.Classification] = ResponseSchema.derived[EnrichSource.Classification]()
  val findings: ResponseSchema[EnrichSource.Findings]             = ResponseSchema.derived[EnrichSource.Findings]()
  val notes: ResponseSchema[EnrichSource.Notes]                   = ResponseSchema.derived[EnrichSource.Notes]()
  val extract: ResponseSchema[CompileArticle.ExtractAnswer]       = ResponseSchema.derived[CompileArticle.ExtractAnswer]()
  val draft: ResponseSchema[CompileArticle.DraftAnswer]           = ResponseSchema.derived[CompileArticle.DraftAnswer]()
  val rules: ResponseSchema[ReviewArticle.RulesAnswer]            = ResponseSchema.derived[ReviewArticle.RulesAnswer]()
  val coldReader: ResponseSchema[ReviewArticle.ColdReaderAnswer]  = ResponseSchema.derived[ReviewArticle.ColdReaderAnswer]()
  val skeptic: ResponseSchema[ReviewArticle.SkepticAnswer]        = ResponseSchema.derived[ReviewArticle.SkepticAnswer]()
  val recheck: ResponseSchema[ReviewArticle.RecheckAnswer]        = ResponseSchema.derived[ReviewArticle.RecheckAnswer]()
  val fixer: ResponseSchema[ReviewArticle.FixerAnswer]            = ResponseSchema.derived[ReviewArticle.FixerAnswer]()
  val plan: ResponseSchema[PlanGoal.PlanAnswer]                   = ResponseSchema.derived[PlanGoal.PlanAnswer]()
  val ranking: ResponseSchema[Scheduling.Ranking]                 = ResponseSchema.derived[Scheduling.Ranking]()
  val filter: ResponseSchema[FilterInference.FilterAnswer]            = ResponseSchema.derived[FilterInference.FilterAnswer]()
  val articles: ResponseSchema[PlanArticles.ArticlesAnswer]    = ResponseSchema.derived[PlanArticles.ArticlesAnswer]()
  val hintEffect: ResponseSchema[HintProposals.HintAnswer]     = ResponseSchema.derived[HintProposals.HintAnswer]()

  /** Every step schema, by name. */
  val all: List[(String, ResponseSchema[?])] = List(
    "probe" -> probe, "classification" -> classification, "findings" -> findings, "notes" -> notes,
    "extract" -> extract, "draft" -> draft, "rules" -> rules, "cold-reader" -> coldReader, "skeptic" -> skeptic,
    "recheck" -> recheck, "fixer" -> fixer, "plan" -> plan, "ranking" -> ranking, "filter" -> filter,
    "articles" -> articles, "hint-effect" -> hintEffect)
