package com.histmersion
package api

import com.histmersion.api.exchange.PlanMatchKind
import com.histmersion.api.library.Source
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** The kind whose records a merge plan matches across collections, registered by its owner (as `Api.all` lists the
  * features' endpoints): the library's sources.
  */
@movesTo(FeaturePlace.Api)
object RegisteredPlanMatches:
  val kind: PlanMatchKind[Source.Match] = Source.Match.Matches
