package com.histmersion
package domain.autopilotpolicy

import com.histmersion.domain.automation.Goal
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.domain.automation

enum Step:
  /** Create the goal's seeding manager, active, with its cron (and a plan-goal item before its first proposal when the
    * goal has no plan).
    */
  case CreateSeed(goal: Step.CreateSeed.Goal, cron: Step.CreateSeed.Cron, planFirst: Step.CreateSeed.PlanFirst)

  /** Create the goal's watcher, active, auto-ingest on (at most `perDay` a day), with its cron. */
  case CreateWatch(goal: Step.CreateWatch.Goal, cron: Step.CreateWatch.Cron, perDay: Step.CreateWatch.PerDay)

  /** Resume a library-wide manager, or one the autopilot paused when it was turned off. */
  case Resume(id: Step.Resume.Id)

  /** Give a manager with no cron its spread cron. */
  case SetCron(id: Step.SetCron.Id, cron: Step.SetCron.Cron)

  /** Turn a watcher's auto-ingest on (it never said either way). */
  case AutoIngestOn(id: Step.AutoIngestOn.Id, perDay: Step.AutoIngestOn.PerDay)

  /** A plan-goal item first, for a goal that still needs a plan. */
  case AddPlanItem(id: Step.AddPlanItem.Id, goal: Step.AddPlanItem.Goal)

  /** The review manager's extend threshold (it sets none). */
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
