package com.histmersion.domain.media.links

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.util.data.exchange.Bundle

/** How a merge treats the figures' links: no line points at one, the ledger names none; the figures' plan folds them
  * with their figures.
  */
object AssetLinkMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("media")
  val kind: Bundle.Kind = Bundle.Kind.AssetLink
