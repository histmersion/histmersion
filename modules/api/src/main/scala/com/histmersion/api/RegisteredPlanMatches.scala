package com.histmersion.api

import com.histmersion.api.exchange.PlanMatchKind
import com.histmersion.api.library.Source

/** The kind whose records a merge plan matches across collections, registered by its owner (as `Api.all` lists the
  * features' endpoints): the library's sources.
  */
object RegisteredPlanMatches:
  val kind: PlanMatchKind[Source.Match] = Source.Match.Matches
