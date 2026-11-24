package com.histmersion
package domain.automation

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
enum AgentRole(val wire: AgentRole.Wire) derives CanEqual:
  case Probe extends AgentRole(AgentRole.Wire("probe"))
  case Extractor extends AgentRole(AgentRole.Wire("extractor"))
  case Reviewer extends AgentRole(AgentRole.Wire("reviewer"))
  case ColdReader extends AgentRole(AgentRole.Wire("cold-reader"))
  case Skeptic extends AgentRole(AgentRole.Wire("skeptic"))
  case Fixer extends AgentRole(AgentRole.Wire("fixer"))

  case Planner extends AgentRole(AgentRole.Wire("planner"))

  case Writer extends AgentRole(AgentRole.Wire("writer"))

object AgentRole:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[AgentRole] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
