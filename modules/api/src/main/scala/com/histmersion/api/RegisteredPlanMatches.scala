package com.histmersion.api

import com.histmersion.util.data.exchange.PlanMatchKind

/** The kind whose records a merge plan matches across collections, registered by its owner (as `Api.all` lists the
  * features' endpoints): the library's sources.
  */
object RegisteredPlanMatches:
  val kind: PlanMatchKind[SourceMatch] = SourceMatch.Matches
