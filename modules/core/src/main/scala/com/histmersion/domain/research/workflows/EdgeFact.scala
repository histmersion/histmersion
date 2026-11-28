package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.research.Article
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given

final case class EdgeFact(from: Citekey, kind: Article.Coverage.DisagreementKind, to: Citekey) derives JsonCodec
