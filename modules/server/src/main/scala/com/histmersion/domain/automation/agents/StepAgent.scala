package com.histmersion
package domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The steps of the engine's pipelines, each run as one agent definition. A test holds every one of them to a
  * definition that parses.
  */
enum StepAgent(val wire: StepAgent.Wire):
  case Probe extends StepAgent(StepAgent.Wire("probe"))
  case EnrichClassify extends StepAgent(StepAgent.Wire("enrich-classify"))
  case EnrichFindings extends StepAgent(StepAgent.Wire("enrich-findings"))
  case EnrichNotes extends StepAgent(StepAgent.Wire("enrich-notes"))
  case RulesReviewer extends StepAgent(StepAgent.Wire("article-rules-reviewer"))
  case ColdReader extends StepAgent(StepAgent.Wire("article-cold-reader"))
  case Skeptic extends StepAgent(StepAgent.Wire("article-skeptic"))
  case Fixer extends StepAgent(StepAgent.Wire("article-fixer"))
  case GoalPlanner extends StepAgent(StepAgent.Wire("goal-planner"))
  case CompileExtractor extends StepAgent(StepAgent.Wire("compile-extractor"))
  case CompileWriter extends StepAgent(StepAgent.Wire("compile-writer"))
  case AcquisitionRanker extends StepAgent(StepAgent.Wire("acquisition-ranker"))
  case FilterInferrer extends StepAgent(StepAgent.Wire("filter-inferrer"))
  case ArticlePlanner extends StepAgent(StepAgent.Wire("article-planner"))
  case HintReader extends StepAgent(StepAgent.Wire("hint-reader"))

object StepAgent:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
