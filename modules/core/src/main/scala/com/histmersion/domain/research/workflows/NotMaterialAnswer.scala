package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.api.research.CritiqueRef
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class NotMaterialAnswer(pair: NotMaterialAnswer.Pair, reason: Rationale) derives JsonCodec

object NotMaterialAnswer:
  export CritiqueRef.Wire as Pair

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[NotMaterialAnswer] = KindlingsSchema
    .derived[NotMaterialAnswer]
    .schema
    .modify(_.pair)(_.description("The open pair, as src>dst"))
    .modify(_.reason)(
      _.description(
        "One sentence: what the article cites the source for, and why the critique does not touch it (quote the article's words)"
      )
    )
