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
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Quotation
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RejectionAnswer(
    id: RejectionAnswer.Id,
    reason: Rationale,
    citekey: RejectionAnswer.Citekey,
    quote: RejectionAnswer.Quote
) derives JsonCodec

object RejectionAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]:
    extension (c: Citekey) def normalised: String = c.value.trim
  export Finding.Id
  export Quotation.Quote

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[RejectionAnswer] = KindlingsSchema
    .derived[RejectionAnswer]
    .schema
    .modify(_.id)(_.description("The id of the finding you reject (R2, S1…)"))
    .modify(_.citekey)(_.description("The source whose record contradicts the finding"))
    .modify(_.quote)(
      _.description("The sentence of that record that contradicts it, copied EXACTLY (from get_source)")
    )
