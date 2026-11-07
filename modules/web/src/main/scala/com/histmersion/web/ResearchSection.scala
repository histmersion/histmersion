package com.histmersion.web

import scala.scalajs.js

/** Research's sections, in the order of its side navigation. `agents`: shown only where the server runs research
  * agents.
  */
enum ResearchSection(val slug: ResearchSection.Slug, val label: ResearchSection.Label, val agents: ResearchSection.Agents):
  case Overview extends ResearchSection(com.histmersion.web.ResearchSection.Slug(""), com.histmersion.web.ResearchSection.Label("Overview"), agents = com.histmersion.web.ResearchSection.Agents(false))
  case Decisions extends ResearchSection(com.histmersion.web.ResearchSection.Slug("decisions"), com.histmersion.web.ResearchSection.Label("Needs your decision"), agents = com.histmersion.web.ResearchSection.Agents(true))
  case Goals extends ResearchSection(com.histmersion.web.ResearchSection.Slug("goals"), com.histmersion.web.ResearchSection.Label("Goals"), agents = com.histmersion.web.ResearchSection.Agents(false))
  case Activity extends ResearchSection(com.histmersion.web.ResearchSection.Slug("activity"), com.histmersion.web.ResearchSection.Label("Activity"), agents = com.histmersion.web.ResearchSection.Agents(false))
  case Backlog extends ResearchSection(com.histmersion.web.ResearchSection.Slug("backlog"), com.histmersion.web.ResearchSection.Label("Backlog"), agents = com.histmersion.web.ResearchSection.Agents(false))
  case Acquire extends ResearchSection(com.histmersion.web.ResearchSection.Slug("acquire"), com.histmersion.web.ResearchSection.Label("To acquire"), agents = com.histmersion.web.ResearchSection.Agents(false))
  case Models extends ResearchSection(com.histmersion.web.ResearchSection.Slug("models"), com.histmersion.web.ResearchSection.Label("AI models and limits"), agents = com.histmersion.web.ResearchSection.Agents(true))

object ResearchSection:
  type Slug = com.histmersion.web.Preview.Item.Slug
  val Slug: _root_.com.histmersion.web.Preview.Item.Slug.type = _root_.com.histmersion.web.Preview.Item.Slug
  type Label = com.histmersion.api.UiLabels.Text
  val Label: _root_.com.histmersion.api.UiLabels.Text.type = _root_.com.histmersion.api.UiLabels.Text
  type Agents = Agents.T
  object Agents extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
