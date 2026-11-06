package com.histmersion.domain.automation
enum AgentRole(val wire: AgentRole.Wire):
  case Probe extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("probe"))
  case Extractor extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("extractor"))
  case Reviewer extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("reviewer"))
  case ColdReader extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("cold-reader"))
  case Skeptic extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("skeptic"))
  case Fixer extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("fixer"))

  /** plan-goal: sub-goals, queries and candidate works for a goal; acquisition-sweep: ranks them. */
  case Planner extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("planner"))

  /** compile-article: drafts the article (or the section to append) from the verified claims. */
  case Writer extends AgentRole(_root_.com.histmersion.domain.automation.AgentRole.Wire("writer"))

object AgentRole:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.domain.automation.AgentRole.ParseS): Option[AgentRole] = values.find(_.wire == (s).value.trim.toLowerCase)
