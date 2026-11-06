package com.histmersion.domain.automation
enum Pipeline(
    val wire: Pipeline.Wire,
    val version: Pipeline.Version,
    val targetKind: TargetKind,
    val roles: List[AgentRole],
    val describe: Pipeline.Describe
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
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Version = Version.T
  object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Describe = Describe.T
  object Describe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.domain.automation.Pipeline.ParseS): Option[Pipeline] = values.find(_.wire == s.trim.toLowerCase)

  /** The pipeline a manager's item that starts no agent run of its own is counted under (the review's, as the managers
    * have always counted them).
    */
  val Fallback: Pipeline = ReviewArticle
