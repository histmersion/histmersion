package com.histmersion
package domain.automation

import io.github.iltotore.iron.constraint.any.Pure
enum AgentRole(val wire: AgentRole.Wire) derives CanEqual:
  case Probe extends AgentRole(AgentRole.Wire("probe"))
  case Extractor extends AgentRole(AgentRole.Wire("extractor"))
  case Reviewer extends AgentRole(AgentRole.Wire("reviewer"))
  case ColdReader extends AgentRole(AgentRole.Wire("cold-reader"))
  case Skeptic extends AgentRole(AgentRole.Wire("skeptic"))
  case Fixer extends AgentRole(AgentRole.Wire("fixer"))

  /** plan-goal: sub-goals, queries and candidate works for a goal; acquisition-sweep: ranks them. */
  case Planner extends AgentRole(AgentRole.Wire("planner"))

  /** compile-article: drafts the article (or the section to append) from the verified claims. */
  case Writer extends AgentRole(AgentRole.Wire("writer"))

object AgentRole:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]


  def parse(s: Wire): Option[AgentRole] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
