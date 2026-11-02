package com.histmersion.server.agents

import com.histmersion.config.AgentToolsConfig
import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import com.histmersion.domain.library.workflows.{AgentUpstream, LibraryAgentTools, LibrarySite}
import com.histmersion.domain.research.workflows.ResearchAgentTools

/** The agent tools the engine's toolbox lists: the library's own ([[LibraryAgentTools]]) and research's (`research`,
  * [[ResearchAgentTools]]), in the order the toolbox has always listed them.
  */
final class TransitionTools(
    library: => LibrarySite,
    upstream: () => AgentUpstream,
    research: List[ToolContribution],
    caps: => AgentToolsConfig
):

  /** Every tool, in the order the engine's toolbox has always listed them. */
  def contributions: List[ToolContribution] =
    val all = (LibraryAgentTools(library, upstream, caps).contributions ++ research).map(t => t.name -> t).toMap
    TransitionTools.names.map(all)

object TransitionTools:
  export LibraryAgentTools.{Facets, Followups, GetSource, Neighbors, Passages, Query, Search}

  export ResearchAgentTools.{ArticleCoverage, CheckEdits, GetArticle}

  /** The names, in the order of [[TransitionTools.contributions]]: what the definitions may list before a server runs.
    */
  val names: List[ToolName] = List(
    LibraryAgentTools.GetSource,
    LibraryAgentTools.Passages,
    LibraryAgentTools.Search,
    LibraryAgentTools.Facets,
    LibraryAgentTools.Neighbors,
    LibraryAgentTools.Query,
    ResearchAgentTools.GetArticle,
    LibraryAgentTools.Followups,
    ResearchAgentTools.CheckEdits,
    ResearchAgentTools.ArticleCoverage
  )
