package com.histmersion
package domain.automation

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum Pipeline(
    val wire: Pipeline.Wire,
    val version: Pipeline.Version,
    val targetKind: TargetKind,
    val roles: List[AgentRole],
    val describe: Pipeline.Describe
):
  case EnrichSource
      extends Pipeline(
        Pipeline.Wire("enrich-source"),
        Pipeline.Version(2),
        TargetKind.Source,
        List(AgentRole.Extractor),
        Pipeline.Describe("read one source and propose its summary, classification, period, findings (each with a verbatim quote) and notes; code verifies and patches")
      )
  case ReviewArticle
      extends Pipeline(
        Pipeline.Wire("review-article"),
        Pipeline.Version(6),
        TargetKind.Article,
        List(AgentRole.Reviewer, AgentRole.ColdReader, AgentRole.Skeptic, AgentRole.Fixer),
        Pipeline.Describe("the adversarial review panel on one article (rules reviewer, cold reader, skeptic in parallel), merged by code into the article's review ledger, the skeptic ruling on each open pair of its critique checklist; with fix, a fixer revises it (code adds the citations a paragraph already names and guards closed findings), each reviewer verifies the findings it claims fixed and the edited passages, until no major finding remains — then a full panel confirms, deferring what it finds new to the next run")
      )
  case PlanGoal
      extends Pipeline(
        Pipeline.Wire("plan-goal"),
        Pipeline.Version(1),
        TargetKind.Goal,
        List(AgentRole.Planner),
        Pipeline.Describe("propose sub-goals, search queries and candidate works (DOIs checked upstream by code) for one goal; nothing is created until a person approves the plan")
      )
  case CompileArticle
      extends Pipeline(
        Pipeline.Wire("compile-article"),
        Pipeline.Version(2),
        TargetKind.Article,
        List(AgentRole.Extractor, AgentRole.Writer),
        Pipeline.Describe("compile a new article, or a section to append to an existing one, from the sources a filter returns: verified claim table, evidence model, draft, code self-check, then a person approves before anything is written, and a review-article run with fix follows")
      )
  case CurrencyCheck
      extends Pipeline(
        Pipeline.Wire("currency-check"),
        Pipeline.Version(1),
        TargetKind.Goal,
        Nil,
        Pipeline.Describe("assess and followups over a goal's sources (no model): retractions applied as assess does, disagreement edges to sources in the corpus proposed for approval, citing papers outside it queued for acquisition")
      )
  case AcquisitionSweep
      extends Pipeline(
        Pipeline.Wire("acquisition-sweep"),
        Pipeline.Version(1),
        TargetKind.Goal,
        List(AgentRole.Planner),
        Pipeline.Describe("discover new works for a goal's queries, a model ranks them against the goal, and the relevant ones become acquisition items — never one already in the corpus or the queue; a watcher that opted in ingests the legal open-access ones (capped per day, legal open access only)")
      )
  case InferFilter
      extends Pipeline(
        Pipeline.Wire("infer-filter"),
        Pipeline.Version(1),
        TargetKind.Article,
        List(AgentRole.Planner),
        Pipeline.Describe("propose the coverage filter (query, topics, tags) of an article that records none; code runs it against the corpus and the article's own citations, and a person approves it (agents.approvals.article-filter) before it is recorded")
      )
  case HintEffect
      extends Pipeline(
        Pipeline.Wire("hint-effect"),
        Pipeline.Version(1),
        TargetKind.Manager,
        List(AgentRole.Planner),
        Pipeline.Describe("read one hint a person gave a process manager and propose the structured action it asks for (hold, prioritise or skip items, or a settings change); code checks the action names real items and settings, and it is applied only when a person approves it")
      )
  case ProposeArticles
      extends Pipeline(
        Pipeline.Wire("propose-articles"),
        Pipeline.Version(1),
        TargetKind.Goal,
        List(AgentRole.Planner),
        Pipeline.Describe("propose the articles a research area should have, each with a slug, title and filter; code checks every slug is free, every filter returns enough sources and none overlaps an article of the area by half, and a person ALWAYS approves the plan before the seeding manager compiles them")
      )

object Pipeline:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
  type Version = Version.T
  object Version extends RefinedType[Int, Pure]
  type Describe = Describe.T
  object Describe extends RefinedType[String, Pure]


  def parse(s: Wire): Option[Pipeline] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  /** The pipeline a manager's item that starts no agent run of its own is counted under (the review's, as the managers
    * have always counted them).
    */
  val Fallback: Pipeline = ReviewArticle
