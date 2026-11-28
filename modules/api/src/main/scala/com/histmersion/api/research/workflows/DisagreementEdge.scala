package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.Article
import com.histmersion.util.json.JsonCodec

final case class DisagreementEdge(from: Citekey, kind: Article.Coverage.DisagreementKind, to: Citekey) derives JsonCodec
