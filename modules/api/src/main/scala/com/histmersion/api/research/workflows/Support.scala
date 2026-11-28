package com.histmersion
package api.research.workflows

import com.histmersion.api.automation.Goal.Plans.ArticleDraft
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Support(
    filter: ArticleDraft.Filter,
    total: Support.RowCount,
    sources: List[ListedSource],
    claims: Map[Citekey, Support.ClaimCount],
    edges: List[DisagreementEdge],
    disputed: List[Citekey],
    notUsed: List[NotUsed]
) derives JsonCodec

object Support:
  type ClaimCount = ClaimCount.T
  object ClaimCount extends Newtype[Int, Pure]
  type RowCount = RowCount.T
  object RowCount extends Newtype[Int, Pure]
