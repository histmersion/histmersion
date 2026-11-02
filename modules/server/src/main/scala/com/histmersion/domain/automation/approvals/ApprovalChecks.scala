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
  def slugExists(slug: String): Boolean

  /** The approvals of `kind` decided by code since `since`. */
  def autoApprovedSince(kind: Approval.Kind, since: Instant): List[ApprovalInfo]

  /** The output the last finished `step` of `run` recorded. */
  def checkpoint(run: String, step: String): Option[String]

  /** How many article drafts the checks may publish today. */
  def publishCap: Int
