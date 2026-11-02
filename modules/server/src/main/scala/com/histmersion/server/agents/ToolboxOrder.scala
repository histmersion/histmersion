package com.histmersion.server.agents

import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import com.histmersion.domain.library.workflows.LibraryAgentTools
import com.histmersion.domain.research.workflows.ResearchAgentTools

/** The order the engine's toolbox lists the domains' agent tools in — the library's and research's interleaved, as the
  * toolbox has always listed them (the definitions, their generator and the toolbox snapshot name them so); the tools
  * themselves are each domain's contribution.
  */
object ToolboxOrder:

  /** The names, in order: what the definitions may list before a server runs. */
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

  /** The domains' tools in the toolbox's order; a contributed tool the order does not name comes after them, in the
    * order contributed.
    */
  def ordered(tools: List[ToolContribution]): List[ToolContribution] =
    tools.sortBy(t => names.indexOf(t.name) match { case -1 => Int.MaxValue; case i => i })
