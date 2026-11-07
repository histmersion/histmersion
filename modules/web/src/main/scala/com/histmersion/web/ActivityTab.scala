package com.histmersion.web

import scala.scalajs.js

/** Research › Activity: ONE list of what the agents, the server and the person did, shown through one of its filter
  * chips (`show`: [[com.histmersion.api.automation.Activity.Show]]) — or one of the detailed lists it sums up (every
  * agent run with its costs, every background job, the worklog by day).
  */
enum ActivityTab(
    val slug: ActivityTab.Slug,
    val label: ActivityTab.Label,
    val agents: ActivityTab.Agents,
    val show: Option[com.histmersion.api.automation.Activity.Show]
):
  case Everything
      extends ActivityTab(
        com.histmersion.web.ActivityTab.Slug(""),
        com.histmersion.web.ActivityTab.Label("Everything"),
        agents = com.histmersion.web.ActivityTab.Agents(false),
        Some(com.histmersion.api.automation.Activity.Show.Everything)
      )
  case Running
      extends ActivityTab(
        com.histmersion.web.ActivityTab.Slug("running"),
        com.histmersion.web.ActivityTab.Label("Running now"),
        agents = com.histmersion.web.ActivityTab.Agents(false),
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
        com.histmersion.web.ActivityTab.Slug("jobs"),
        com.histmersion.web.ActivityTab.Label("Background jobs"),
        agents = com.histmersion.web.ActivityTab.Agents(false),
        Some(com.histmersion.api.automation.Activity.Show.Jobs)
      )
  case Yours
      extends ActivityTab(
        com.histmersion.web.ActivityTab.Slug("yours"),
        com.histmersion.web.ActivityTab.Label("Your actions"),
        agents = com.histmersion.web.ActivityTab.Agents(true),
        Some(com.histmersion.api.automation.Activity.Show.Yours)
      )
  case Problems
      extends ActivityTab(
        com.histmersion.web.ActivityTab.Slug("problems"),
        com.histmersion.web.ActivityTab.Label("Problems"),
        agents = com.histmersion.web.ActivityTab.Agents(false),
        Some(com.histmersion.api.automation.Activity.Show.Problems)
      )
  case Runs extends ActivityTab(com.histmersion.web.ActivityTab.Slug("runs"), com.histmersion.web.ActivityTab.Label("Every agent run"), agents = com.histmersion.web.ActivityTab.Agents(true), None)
  case JobList extends ActivityTab(com.histmersion.web.ActivityTab.Slug("all-jobs"), com.histmersion.web.ActivityTab.Label("Every background job"), agents = com.histmersion.web.ActivityTab.Agents(false), None)
  case Worklog extends ActivityTab(com.histmersion.web.ActivityTab.Slug("worklog"), com.histmersion.web.ActivityTab.Label("The worklog, by day"), agents = com.histmersion.web.ActivityTab.Agents(false), None)

  /** One of the merged list's filter chips (not a detailed list). */
  def isChip: Boolean = show.isDefined

object ActivityTab:
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Agents = Agents.T
  object Agents extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

