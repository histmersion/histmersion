package com.histmersion.domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** The steps of the engine's pipelines, each run as one agent definition. A test holds every one of them to a
  * definition that parses.
  */
enum StepAgent(val wire: StepAgent.Wire):
  case Probe extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("probe"))
  case EnrichClassify extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("enrich-classify"))
  case EnrichFindings extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("enrich-findings"))
  case EnrichNotes extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("enrich-notes"))
  case RulesReviewer extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("article-rules-reviewer"))
  case ColdReader extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("article-cold-reader"))
  case Skeptic extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("article-skeptic"))
  case Fixer extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("article-fixer"))
  case GoalPlanner extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("goal-planner"))
  case CompileExtractor extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("compile-extractor"))
  case CompileWriter extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("compile-writer"))
  case AcquisitionRanker extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("acquisition-ranker"))
  case FilterInferrer extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("filter-inferrer"))
  case ArticlePlanner extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("article-planner"))
  case HintReader extends StepAgent(com.histmersion.domain.automation.agents.StepAgent.Wire("hint-reader"))

object StepAgent:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
