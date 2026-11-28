package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.reviewarticle.Evidence
import com.histmersion.Newtype
import com.histmersion.domain.reviewarticle.SpotCheck
import com.histmersion.domain.ReviewArticle

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SpotCheckAnswer(
    citekey: SpotCheckAnswer.Citekey,
    claim: SpotCheckAnswer.Claim,
    verdict: SpotCheckAnswer.Verdict,
    recordQuote: SpotCheckAnswer.RecordQuote
) derives JsonCodec

object SpotCheckAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]:
    extension (c: Citekey) def normalised: String = c.value.trim.stripPrefix("[[").stripSuffix("]]").trim
  export SpotCheck.Claim
  export ReviewArticle.SpotVerdictsValue as Verdict
  export Evidence.Quote as RecordQuote

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[SpotCheckAnswer] = KindlingsSchema
    .derived[SpotCheckAnswer]
    .schema
    .modify(_.citekey)(_.description("The cited source, read with get_source or passages in this step"))
    .modify(_.claim)(_.description("The article's words, copied exactly"))
    .modify(_.verdict)(_.description("attributable | extrapolatory | contradictory"))
    .modify(_.recordQuote)(_.description("The record's sentence it rests on, copied EXACTLY"))
