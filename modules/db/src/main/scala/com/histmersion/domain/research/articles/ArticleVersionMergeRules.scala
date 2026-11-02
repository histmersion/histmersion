package com.histmersion.domain.research.articles

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.util.data.exchange.Bundle

/** How a merge treats an article's versions: nothing points at one, and the ledger names none. */
object ArticleVersionMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("research")
  val kind: Bundle.Kind = Bundle.Kind.ArticleVersion
