package com.histmersion.server.agents

import com.histmersion.api.{ApiError, BackupStatus, Filter, Goal, GoalPatch, GoalPut}
import com.histmersion.api.Lint.LintFixResult
import com.histmersion.domain.PlanGoal
import com.histmersion.domain.automation.workflows.AutomationHost
import com.histmersion.server.Handlers

/** The engine's [[AutomationHost]] over today's handlers, until each operation's owner offers its own service. */
final class TransitionHost(h: Handlers, upstream: () => AgentUpstream) extends AutomationHost:
  def goal(slug: String): Either[ApiError, Goal] = h.getGoal(slug)
  def putGoal(slug: String, in: GoalPut): Either[ApiError, Goal] = h.putGoal(slug, in)
  def patchGoal(slug: String, patch: GoalPatch): Either[ApiError, Goal] = h.patchGoal(slug, patch)
  def workLookup: PlanGoal.Lookup = doi => upstream().meta(doi)
  def vocabulary(field: String): List[String] =
    h.facets(field, None, Filter()).toOption.toList.flatten.sortBy(-_.count).map(_.value)
  def housekeepingBlock: Option[String] = h.housekeepingBlock
  def backup(): Either[ApiError, BackupStatus] = h.backup()
  def backupStatus: Either[ApiError, BackupStatus] = h.backupStatus
  def lintFix(): Either[ApiError, LintFixResult] = h.ops.guardedLintFix(Nil)
