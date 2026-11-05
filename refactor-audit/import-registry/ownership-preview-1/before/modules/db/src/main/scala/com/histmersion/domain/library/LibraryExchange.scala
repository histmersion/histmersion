package com.histmersion.domain.library

import com.histmersion.domain.library.sources.{FindingMergeRules, SourceMergeRules, SourcesCarried}
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}

/** What the library carries in a bundle and how a merge treats its records: the sources and what they own. */
object LibraryExchange:

  /** The sources, their findings, edges, parked edges, assessments and old citekeys. */
  val carried: List[CarriedKind] = SourcesCarried.all

  /** How a merge treats sources and findings. */
  val mergeRules: List[MergeRules] = List(SourceMergeRules, FindingMergeRules)
