package com.histmersion.server.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** The steps of the engine's pipelines, each run as one agent definition. A test holds every one of them to a
  * definition that parses.
  */
enum StepAgent(val wire: String):
  case Probe extends StepAgent("probe")
  case EnrichClassify extends StepAgent("enrich-classify")
  case EnrichFindings extends StepAgent("enrich-findings")
  case EnrichNotes extends StepAgent("enrich-notes")
  case RulesReviewer extends StepAgent("article-rules-reviewer")
  case ColdReader extends StepAgent("article-cold-reader")
  case Skeptic extends StepAgent("article-skeptic")
  case Fixer extends StepAgent("article-fixer")
  case GoalPlanner extends StepAgent("goal-planner")
  case CompileExtractor extends StepAgent("compile-extractor")
  case CompileWriter extends StepAgent("compile-writer")
  case AcquisitionRanker extends StepAgent("acquisition-ranker")
  case FilterInferrer extends StepAgent("filter-inferrer")
  case ArticlePlanner extends StepAgent("article-planner")
  case HintReader extends StepAgent("hint-reader")
