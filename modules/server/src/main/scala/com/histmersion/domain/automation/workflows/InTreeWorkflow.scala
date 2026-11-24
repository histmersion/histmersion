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
  case AdoptDatabase extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("adopt-database"))
  case Ingest extends InTreeWorkflow(Plugin.Name("library"), Workflow.Name("ingest"))
  case CheckFix extends InTreeWorkflow(Plugin.Name("library"), Workflow.Name("check-fix"))
  case Init extends InTreeWorkflow(Plugin.Name("catalogue"), Workflow.Name("init"))
  case AgentTools extends InTreeWorkflow(Plugin.Name("library"), Workflow.Name("agent-tools"))
  case EngineStart extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("engine-start"))
  case ApprovalEffects extends InTreeWorkflow(Plugin.Name("engine"), Workflow.Name("approval-effects"))

  def creator: Creator.New = Creator.code(plugin, workflow)

object InTreeWorkflow:
  def Engine: Plugin.Name = Plugin.Name("engine")
  def Library: Plugin.Name = Plugin.Name("library")
  def Research: Plugin.Name = Plugin.Name("research")
