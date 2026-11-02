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
