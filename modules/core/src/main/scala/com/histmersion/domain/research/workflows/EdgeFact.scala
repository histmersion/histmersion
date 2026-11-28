package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.research.Article

final case class EdgeFact(from: Citekey, kind: Article.Coverage.DisagreementKind, to: Citekey)
