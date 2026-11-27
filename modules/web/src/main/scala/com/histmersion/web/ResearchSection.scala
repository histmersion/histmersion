package com.histmersion
package web

import scala.scalajs.js
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.constraint.any.Pure
import ResearchSection.{Agents, Label, Slug}

/** Research's sections, in the order of its side navigation. `agents`: shown only where the server runs research
  * agents.
  */
enum ResearchSection(
    val slug: ResearchSection.Slug,
    val label: ResearchSection.Label,
    val agents: ResearchSection.Agents
) derives CanEqual:
  case Overview
      extends ResearchSection(
        ResearchSection.Slug(""),
        ResearchSection.Label("Overview"),
        agents = ResearchSection.Agents.Disabled
      )
  case State extends ResearchSection(Slug("state"), Label("State"), agents = Agents.Disabled)
  case Decisions
      extends ResearchSection(
        ResearchSection.Slug("decisions"),
        ResearchSection.Label("Needs your decision"),
        agents = ResearchSection.Agents.Enabled
      )
  case Goals
      extends ResearchSection(
        ResearchSection.Slug("goals"),
        ResearchSection.Label("Goals"),
        agents = ResearchSection.Agents.Disabled
      )
  case Activity
      extends ResearchSection(
        ResearchSection.Slug("activity"),
        ResearchSection.Label("Activity"),
        agents = ResearchSection.Agents.Disabled
      )
  case Backlog
      extends ResearchSection(
        ResearchSection.Slug("backlog"),
        ResearchSection.Label("Backlog"),
        agents = ResearchSection.Agents.Disabled
      )
  case Acquire
      extends ResearchSection(
        ResearchSection.Slug("acquire"),
        ResearchSection.Label("To acquire"),
        agents = ResearchSection.Agents.Disabled
      )
  case Models
      extends ResearchSection(
        ResearchSection.Slug("models"),
        ResearchSection.Label("AI models and limits"),
        agents = ResearchSection.Agents.Enabled
      )

object ResearchSection:
  export api.research.Article.Slug
  export UiLabels.Text as Label
  enum Agents derives CanEqual:
    case Enabled, Disabled
  object Agents:
    extension (agents: Agents) def enabled: Boolean = agents == Enabled
