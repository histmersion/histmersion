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
import com.histmersion.domain.reviewarticle.Quotation
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EvidenceAnswer(
    citekey: EvidenceAnswer.Citekey,
    offset: Option[EvidenceAnswer.Offset] = None,
    quote: EvidenceAnswer.Quote
) derives JsonCodec

object EvidenceAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]:
    extension (c: Citekey) def normalised: String = c.value.trim.stripPrefix("[[").stripSuffix("]]").trim
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  export Quotation.Quote

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[EvidenceAnswer] = KindlingsSchema
    .derived[EvidenceAnswer]
    .schema
    .modify(_.citekey)(_.description("The source whose record shows it"))
    .modify(_.offset)(
      _.description(
        "Where in that source's body the quote is (the get_source offset you read at); null if not from the body"
      )
    )
    .modify(_.quote)(_.description("The sentence of that record, copied EXACTLY from get_source"))
