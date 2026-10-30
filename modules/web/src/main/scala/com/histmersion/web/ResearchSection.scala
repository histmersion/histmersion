package com.histmersion.web

import scala.scalajs.js

/** Research's sections, in the order of its side navigation. `agents`: shown only where the server runs research
  * agents.
  */
enum ResearchSection(val slug: String, val label: String, val agents: Boolean):
  case Overview extends ResearchSection("", "Overview", agents = false)
  case Decisions extends ResearchSection("decisions", "Needs your decision", agents = true)
  case Goals extends ResearchSection("goals", "Goals", agents = false)
  case Activity extends ResearchSection("activity", "Activity", agents = false)
  case Backlog extends ResearchSection("backlog", "Backlog", agents = false)
  case Acquire extends ResearchSection("acquire", "To acquire", agents = false)
  case Models extends ResearchSection("models", "AI models and limits", agents = true)
