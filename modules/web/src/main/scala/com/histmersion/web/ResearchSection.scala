package com.histmersion
package web

import scala.scalajs.js
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.constraint.any.Pure

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
        agents = ResearchSection.Agents(false)
      )
  case Decisions
      extends ResearchSection(
        ResearchSection.Slug("decisions"),
        ResearchSection.Label("Needs your decision"),
        agents = ResearchSection.Agents(true)
      )
  case Goals
      extends ResearchSection(
        ResearchSection.Slug("goals"),
        ResearchSection.Label("Goals"),
        agents = ResearchSection.Agents(false)
      )
  case Activity
      extends ResearchSection(
        ResearchSection.Slug("activity"),
        ResearchSection.Label("Activity"),
        agents = ResearchSection.Agents(false)
      )
  case Backlog
      extends ResearchSection(
        ResearchSection.Slug("backlog"),
        ResearchSection.Label("Backlog"),
        agents = ResearchSection.Agents(false)
      )
  case Acquire
      extends ResearchSection(
        ResearchSection.Slug("acquire"),
        ResearchSection.Label("To acquire"),
        agents = ResearchSection.Agents(false)
      )
  case Models
      extends ResearchSection(
        ResearchSection.Slug("models"),
        ResearchSection.Label("AI models and limits"),
        agents = ResearchSection.Agents(true)
      )

object ResearchSection:
  export com.histmersion.api.research.Article.Slug
  export UiLabels.Text as Label
  type Agents = Agents.T
  object Agents extends Newtype[Boolean, Pure]
