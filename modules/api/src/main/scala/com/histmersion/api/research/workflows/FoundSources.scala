package com.histmersion
package api.research.workflows

import com.histmersion.api.automation.Goal.Plans.ArticleDraft
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class FoundSources(
      filter: ArticleDraft.Filter,
      total: FoundSources.RowCount,
      sources: List[ListedSource],
      toExtract: List[Citekey]
  ) derives JsonCodec

object FoundSources:
  type RowCount = RowCount.T
  object RowCount extends Newtype[Int, Pure]
