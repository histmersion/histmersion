package com.histmersion
package web

import scala.scalajs.js
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Research's sections, in the order of its side navigation. `agents`: shown only where the server runs research
  * agents.
  */
enum ResearchSection(val slug: ResearchSection.Slug, val label: ResearchSection.Label, val agents: ResearchSection.Agents):
  case Overview extends ResearchSection(ResearchSection.Slug(""), ResearchSection.Label("Overview"), agents = ResearchSection.Agents(false))
  case Decisions extends ResearchSection(ResearchSection.Slug("decisions"), ResearchSection.Label("Needs your decision"), agents = ResearchSection.Agents(true))
  case Goals extends ResearchSection(ResearchSection.Slug("goals"), ResearchSection.Label("Goals"), agents = ResearchSection.Agents(false))
  case Activity extends ResearchSection(ResearchSection.Slug("activity"), ResearchSection.Label("Activity"), agents = ResearchSection.Agents(false))
  case Backlog extends ResearchSection(ResearchSection.Slug("backlog"), ResearchSection.Label("Backlog"), agents = ResearchSection.Agents(false))
  case Acquire extends ResearchSection(ResearchSection.Slug("acquire"), ResearchSection.Label("To acquire"), agents = ResearchSection.Agents(false))
  case Models extends ResearchSection(ResearchSection.Slug("models"), ResearchSection.Label("AI models and limits"), agents = ResearchSection.Agents(true))

object ResearchSection:
  type Slug = Preview.Item.Slug
  val Slug: Preview.Item.Slug.type = Preview.Item.Slug
  type Label = UiLabels.Text
  val Label: UiLabels.Text.type = UiLabels.Text
  type Agents = Agents.T
  object Agents extends RefinedType[Boolean, Pure]
