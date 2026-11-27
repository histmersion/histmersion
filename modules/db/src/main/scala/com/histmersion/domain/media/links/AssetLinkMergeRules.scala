package com.histmersion
package domain.media.links

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.lint.{tolerated, LintRule}

/** How a merge treats the figures' links: no line points at one, the ledger names none; the figures' plan folds them
  * with their figures.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AssetLinkMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("media")
  val kind: Bundle.Kind = Bundle.Kind.AssetLink
