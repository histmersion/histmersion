package com.histmersion
package domain.library

import com.histmersion.domain.library.sources.{FindingMergeRules, SourceMergeRules, SourcesCarried}
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** What the library carries in a bundle and how a merge treats its records: the sources and what they own. */
object LibraryExchange:

  /** The sources, their findings, edges, parked edges, assessments and old citekeys. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def carried(referrers: SourceReferrers)(using Creator.New): List[CarriedKind] = SourcesCarried.all(referrers)

  /** How a merge treats sources and findings. */
  val mergeRules: List[MergeRules] = List(SourceMergeRules, FindingMergeRules)
