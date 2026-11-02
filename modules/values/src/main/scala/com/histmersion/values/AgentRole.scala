package com.histmersion.values

/** What a step asks a model to be. Each role maps to a `provider:model` in config. */
enum AgentRole(val wire: String):
  case Probe extends AgentRole("probe")
  case Extractor extends AgentRole("extractor")
  case Reviewer extends AgentRole("reviewer")
  case ColdReader extends AgentRole("cold-reader")
  case Skeptic extends AgentRole("skeptic")
  case Fixer extends AgentRole("fixer")

  /** plan-goal: sub-goals, queries and candidate works for a goal; acquisition-sweep: ranks them. */
  case Planner extends AgentRole("planner")

  /** compile-article: drafts the article (or the section to append) from the verified claims. */
  case Writer extends AgentRole("writer")

object AgentRole:
  def parse(s: String): Option[AgentRole] = values.find(_.wire == s.trim.toLowerCase)

/** The pipelines the engine can run. Each one is Scala code (steps, gates, validation) plus one prompt per LLM step.
  */
enum Pipeline(
    val wire: String,
    val version: Int,
    val targetKind: TargetKind,
    val roles: List[AgentRole],
    val describe: String
):
  case EnrichSource
      extends Pipeline(
        "enrich-source",
        2,
        TargetKind.Source,
        List(AgentRole.Extractor),
        "read one source and propose its summary, classification, period, findings (each with a verbatim quote) and notes; code verifies and patches"
      )
  case ReviewArticle
      extends Pipeline(
        "review-article",
        6,
        TargetKind.Article,
        List(AgentRole.Reviewer, AgentRole.ColdReader, AgentRole.Skeptic, AgentRole.Fixer),
        "the adversarial review panel on one article (rules reviewer, cold reader, skeptic in parallel), merged by code into the article's review ledger, the skeptic ruling on each open pair of its critique checklist; with fix, a fixer revises it (code adds the citations a paragraph already names and guards closed findings), each reviewer verifies the findings it claims fixed and the edited passages, until no major finding remains — then a full panel confirms, deferring what it finds new to the next run"
      )
  case PlanGoal
      extends Pipeline(
        "plan-goal",
        1,
        TargetKind.Goal,
        List(AgentRole.Planner),
        "propose sub-goals, search queries and candidate works (DOIs checked upstream by code) for one goal; nothing is created until a person approves the plan"
      )
  case CompileArticle
      extends Pipeline(
        "compile-article",
        2,
        TargetKind.Article,
        List(AgentRole.Extractor, AgentRole.Writer),
        "compile a new article, or a section to append to an existing one, from the sources a filter returns: verified claim table, evidence model, draft, code self-check, then a person approves before anything is written, and a review-article run with fix follows"
      )
  case CurrencyCheck
      extends Pipeline(
        "currency-check",
        1,
        TargetKind.Goal,
        Nil,
        "assess and followups over a goal's sources (no model): retractions applied as assess does, disagreement edges to sources in the corpus proposed for approval, citing papers outside it queued for acquisition"
      )
  case AcquisitionSweep
      extends Pipeline(
        "acquisition-sweep",
        1,
        TargetKind.Goal,
        List(AgentRole.Planner),
        "discover new works for a goal's queries, a model ranks them against the goal, and the relevant ones become acquisition items — never one already in the corpus or the queue; a watcher that opted in ingests the legal open-access ones (capped per day, legal open access only)"
      )
  case InferFilter
      extends Pipeline(
        "infer-filter",
        1,
        TargetKind.Article,
        List(AgentRole.Planner),
        "propose the coverage filter (query, topics, tags) of an article that records none; code runs it against the corpus and the article's own citations, and a person approves it (agents.approvals.article-filter) before it is recorded"
      )
  case HintEffect
      extends Pipeline(
        "hint-effect",
        1,
        TargetKind.Manager,
        List(AgentRole.Planner),
        "read one hint a person gave a process manager and propose the structured action it asks for (hold, prioritise or skip items, or a settings change); code checks the action names real items and settings, and it is applied only when a person approves it"
      )
  case ProposeArticles
      extends Pipeline(
        "propose-articles",
        1,
        TargetKind.Goal,
        List(AgentRole.Planner),
        "propose the articles a research area should have, each with a slug, title and filter; code checks every slug is free, every filter returns enough sources and none overlaps an article of the area by half, and a person ALWAYS approves the plan before the seeding manager compiles them"
      )

object Pipeline:
  def parse(s: String): Option[Pipeline] = values.find(_.wire == s.trim.toLowerCase)

  /** The pipeline a manager's item that starts no agent run of its own is counted under (the review's, as the managers
    * have always counted them).
    */
  val Fallback: Pipeline = ReviewArticle

/** What a pipeline works on: a source (by citekey), an article (by slug), a goal (by slug) or a process manager (by
  * id).
  */
enum TargetKind(val wire: String):
  case Source extends TargetKind("source")
  case Article extends TargetKind("article")
  case Goal extends TargetKind("goal")
  case Manager extends TargetKind("manager")

object TargetKind:
  def parse(s: String): Option[TargetKind] = values.find(_.wire == s.trim.toLowerCase)
