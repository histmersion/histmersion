package com.histmersion.api.automation
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
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.automation.AgentRole.ParseS): Option[AgentRole] = values.find(_.wire == s.trim.toLowerCase)
