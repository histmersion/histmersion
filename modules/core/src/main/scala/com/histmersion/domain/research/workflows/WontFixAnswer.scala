package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.ReviewArticle.Rationale
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.domain.research.workflows.Finding

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class WontFixAnswer(id: WontFixAnswer.Id, reason: Rationale) derives JsonCodec

object WontFixAnswer:
  export Finding.Id

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[WontFixAnswer] = KindlingsSchema
    .derived[WontFixAnswer]
    .schema
    .modify(_.id)(_.description("The id of the finding you decline"))
    .modify(_.reason)(_.description("Why, in one sentence"))
