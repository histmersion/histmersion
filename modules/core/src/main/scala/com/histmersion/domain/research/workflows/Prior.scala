package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.api.research.Article
import com.histmersion.domain.AgentVerify

final case class Prior(asked: List[Finding], body: Prior.Body, minRatio: Prior.MinRatio)

object Prior:
  export Article.Body
  export AgentVerify.MinRatio
