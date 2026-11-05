package com.histmersion.web

import scala.scalajs.js

/** Research › Activity: ONE list of what the agents, the server and the person did, shown through one of its filter
  * chips (`show`: [[com.histmersion.api.automation.Activity.Show]]) — or one of the detailed lists it sums up (every
  * agent run with its costs, every background job, the worklog by day).
  */
enum ActivityTab(
    val slug: String,
    val label: String,
    val agents: Boolean,
    val show: Option[com.histmersion.api.automation.Activity.Show]
):
  case Everything
      extends ActivityTab(
        "",
        "Everything",
        agents = false,
        Some(com.histmersion.api.automation.Activity.Show.Everything)
      )
  case Running
      extends ActivityTab(
        "running",
        "Running now",
        agents = false,
        Some(com.histmersion.api.automation.Activity.Show.Running)
      )
  case Agents
      extends ActivityTab(
        "agents",
        "Agent work",
        agents = true,
        Some(com.histmersion.api.automation.Activity.Show.Agents)
      )
  case Jobs
      extends ActivityTab(
        "jobs",
        "Background jobs",
        agents = false,
        Some(com.histmersion.api.automation.Activity.Show.Jobs)
      )
  case Yours
      extends ActivityTab(
        "yours",
        "Your actions",
        agents = true,
        Some(com.histmersion.api.automation.Activity.Show.Yours)
      )
  case Problems
      extends ActivityTab(
        "problems",
        "Problems",
        agents = false,
        Some(com.histmersion.api.automation.Activity.Show.Problems)
      )
  case Runs extends ActivityTab("runs", "Every agent run", agents = true, None)
  case JobList extends ActivityTab("all-jobs", "Every background job", agents = false, None)
  case Worklog extends ActivityTab("worklog", "The worklog, by day", agents = false, None)

  /** One of the merged list's filter chips (not a detailed list). */
  def isChip: Boolean = show.isDefined
