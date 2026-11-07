package com.histmersion.web

import scala.scalajs.js

/** Research's sections, in the order of its side navigation. `agents`: shown only where the server runs research
  * agents.
  */
enum ResearchSection(val slug: ResearchSection.Slug, val label: ResearchSection.Label, val agents: ResearchSection.Agents):
  case Overview extends ResearchSection("", "Overview", agents = false)
  case Decisions extends ResearchSection("decisions", "Needs your decision", agents = true)
  case Goals extends ResearchSection("goals", "Goals", agents = false)
  case Activity extends ResearchSection("activity", "Activity", agents = false)
  case Backlog extends ResearchSection("backlog", "Backlog", agents = false)
  case Acquire extends ResearchSection("acquire", "To acquire", agents = false)
  case Models extends ResearchSection("models", "AI models and limits", agents = true)

object ResearchSection:
  type Slug = com.histmersion.web.Preview.Item.Slug
  val Slug: _root_.com.histmersion.web.Preview.Item.Slug.type = _root_.com.histmersion.web.Preview.Item.Slug
  type Label = com.histmersion.api.UiLabels.Text
  val Label: _root_.com.histmersion.api.UiLabels.Text.type = _root_.com.histmersion.api.UiLabels.Text
  type Agents = Agents.T
  object Agents extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
