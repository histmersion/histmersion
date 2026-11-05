package com.histmersion.domain.automation.approvals

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.config.AgentsConfig

import java.time.Instant
import com.histmersion.domain.automation.Approval

/** What a manager kind's checks of an approval its items wait for are given ([[AutoApprovals]] is one): what was
  * approved by code since a moment, the output a run's step recorded, the caps.
  */
trait ApprovalChecks:
  def config: AgentsConfig

  /** Whether an article with this slug exists (as stored). */
  def slugExists(slug: com.histmersion.domain.automation.approvals.ApprovalChecks.SlugExistsSlug): com.histmersion.domain.automation.approvals.ApprovalChecks.SlugExistsResult

  /** The approvals of `kind` decided by code since `since`. */
  def autoApprovedSince(kind: Approval.Kind, since: Instant): List[ApprovalInfo]

  /** The output the last finished `step` of `run` recorded. */
  def checkpoint(run: com.histmersion.domain.automation.approvals.ApprovalChecks.CheckpointRun, step: com.histmersion.domain.automation.approvals.ApprovalChecks.CheckpointStep): Option[com.histmersion.domain.automation.approvals.ApprovalChecks.CheckpointResult]

  /** How many article drafts the checks may publish today. */
  def publishCap: com.histmersion.domain.automation.approvals.ApprovalChecks.PublishCapResult

object ApprovalChecks:
  type SlugExistsResult = SlugExistsResult.T
  object SlugExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugExistsSlug = SlugExistsSlug.T
  object SlugExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckpointResult = CheckpointResult.T
  object CheckpointResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckpointRun = CheckpointRun.T
  object CheckpointRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckpointStep = CheckpointStep.T
  object CheckpointStep extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PublishCapResult = PublishCapResult.T
  object PublishCapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
