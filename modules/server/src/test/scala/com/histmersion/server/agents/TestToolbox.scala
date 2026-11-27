package com.histmersion
package server.agents

import com.histmersion.domain.research.workflows.*

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.Toolbox

import com.histmersion.config.AgentToolsConfig
import com.histmersion.server.ServerWiring
import com.histmersion.lint.{tolerated, LintRule}

/** The toolbox of the domains' agent tools over `h` (no upstream attached), in the toolbox's order, as the engine holds
  * it.
  */
object TestToolbox:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def apply(h: ServerWiring, caps: AgentToolsConfig = AgentToolsConfig.Off): Toolbox =
    Toolbox(
      ToolboxOrder.ordered(
        LibraryAgentTools(h.librarySite, () => AgentUpstream.Offline, caps).contributions ++
          ResearchAgentTools(h.research.articles, caps).contributions
      )
    )
