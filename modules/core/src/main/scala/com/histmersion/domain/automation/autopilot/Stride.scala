package com.histmersion
package domain.automation.autopilot

import com.histmersion.domain.automation.Goal
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.domain.automation

enum Stride:
  case CreateSeed(goal: Stride.CreateSeed.Goal, cron: Stride.CreateSeed.Cron, planFirst: Stride.CreateSeed.PlanFirst)

  case CreateWatch(goal: Stride.CreateWatch.Goal, cron: Stride.CreateWatch.Cron, perDay: Stride.CreateWatch.PerDay)

  case Resume(id: Stride.Resume.Id)

  case SetCron(id: Stride.SetCron.Id, cron: Stride.SetCron.Cron)

  case AutoIngestOn(id: Stride.AutoIngestOn.Id, perDay: Stride.AutoIngestOn.PerDay)

  case AddPlanItem(id: Stride.AddPlanItem.Id, goal: Stride.AddPlanItem.Goal)

  case ExtendMinNew(id: Stride.ExtendMinNew.Id, n: Stride.ExtendMinNew.N)

object Stride:
  object CreateSeed:
    export automation.Goal.Slug as Goal
    export AutopilotPolicy.Cron
    export GoalRow.NeedsPlan as PlanFirst
  object CreateWatch:
    export automation.Goal.Slug as Goal
    export AutopilotPolicy.Cron
    export Pacing.WatchIngestPerDay as PerDay
  object Resume:
    export ManagerRow.Id
  object SetCron:
    export ManagerRow.Id
    export AutopilotPolicy.Cron
  object AutoIngestOn:
    export ManagerRow.Id
    export Pacing.WatchIngestPerDay as PerDay
  object AddPlanItem:
    export ManagerRow.Id
    export automation.Goal.Slug as Goal
  object ExtendMinNew:
    export ManagerRow.Id
    export Pacing.ExtendMinNew as N
