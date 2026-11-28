package com.histmersion
package domain.autopilotpolicy

import com.histmersion.domain.automation.Goal
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.domain.automation

enum Step:
  case CreateSeed(goal: Step.CreateSeed.Goal, cron: Step.CreateSeed.Cron, planFirst: Step.CreateSeed.PlanFirst)

  case CreateWatch(goal: Step.CreateWatch.Goal, cron: Step.CreateWatch.Cron, perDay: Step.CreateWatch.PerDay)

  case Resume(id: Step.Resume.Id)

  case SetCron(id: Step.SetCron.Id, cron: Step.SetCron.Cron)

  case AutoIngestOn(id: Step.AutoIngestOn.Id, perDay: Step.AutoIngestOn.PerDay)

  case AddPlanItem(id: Step.AddPlanItem.Id, goal: Step.AddPlanItem.Goal)

  case ExtendMinNew(id: Step.ExtendMinNew.Id, n: Step.ExtendMinNew.N)

object Step:
  object CreateSeed:
    export automation.Goal.Slug as Goal
    export AutopilotPolicy.Cron
    export GoalRow.NeedsPlan as PlanFirst
  object CreateWatch:
    export automation.Goal.Slug as Goal
    export AutopilotPolicy.Cron
    export Settings.WatchIngestPerDay as PerDay
  object Resume:
    export ManagerRow.Id
  object SetCron:
    export ManagerRow.Id
    export AutopilotPolicy.Cron
  object AutoIngestOn:
    export ManagerRow.Id
    export Settings.WatchIngestPerDay as PerDay
  object AddPlanItem:
    export ManagerRow.Id
    export automation.Goal.Slug as Goal
  object ExtendMinNew:
    export ManagerRow.Id
    export Settings.ExtendMinNew as N
