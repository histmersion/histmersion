package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.api.research.CritiqueRef
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.domain.reviewarticle.Finding

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SetAsideAnswer(
    id: SetAsideAnswer.Id,
    pair: Option[SetAsideAnswer.Pair] = None,
    reason: Rationale
) derives JsonCodec

object SetAsideAnswer:
  export Finding.Id
  export CritiqueRef.Wire as Pair

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[SetAsideAnswer] = KindlingsSchema
    .derived[SetAsideAnswer]
    .schema
    .modify(_.id)(
      _.description(
        "The id of the missing-critique finding; empty for a checklist pair no finding names (one your edits brought in)"
      )
    )
    .modify(_.pair)(_.description("The pair, as src>dst — null to use the one the finding names"))
    .modify(_.reason)(
      _.description(
        "One sentence, after reading both records: what the article cites the source for, and why the critique does not touch it"
      )
    )
