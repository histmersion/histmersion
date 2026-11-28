package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.domain.research.workflows.Finding

final case class WontFix(id: WontFix.Id, reason: Rationale)

object WontFix:
  export Finding.Id
