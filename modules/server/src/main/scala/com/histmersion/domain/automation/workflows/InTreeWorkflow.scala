package com.histmersion
package domain.automation.workflows

import com.histmersion.domain.Creator
import com.histmersion.domain.Creator.{Plugin, Workflow}

enum InTreeWorkflow(val plugin: Plugin.Name, val workflow: Workflow.Name) derives CanEqual:
  case RunNotices extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("run-notices"))
  case Scheduler extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("scheduler"))
  case Autopilot extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("autopilot"))
  case AutoApprovals extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("auto-approvals"))
  case ApprovalDigest extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("approval-digest"))
  case Drain extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("drain"))
  case GoalFocus extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("goal-focus"))
  case Managers extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("managers"))
  case AcquireRecheck extends InTreeWorkflow(Plugin.Name("library"), Workflow.Name("acquire-recheck"))
  case IngestBacklog extends InTreeWorkflow(Plugin.Name("library"), Workflow.Name("ingest-backlog"))
  case Seed extends InTreeWorkflow(Plugin.Name("library"), Workflow.Name("seed"))

  def creator: Creator.New = Creator.code(plugin, workflow)

object InTreeWorkflow:
  def Engine: Plugin.Name = Plugin.Name("engine")
  def Library: Plugin.Name = Plugin.Name("library")
  def Research: Plugin.Name = Plugin.Name("research")
