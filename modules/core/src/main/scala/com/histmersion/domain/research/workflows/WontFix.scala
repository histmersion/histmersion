package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.domain.research.workflows.Finding

final case class WontFix(id: WontFix.Id, reason: Rationale) derives JsonCodec

object WontFix:
  export Finding.Id
