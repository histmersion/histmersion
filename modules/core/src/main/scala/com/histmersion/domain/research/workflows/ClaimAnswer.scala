package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import com.histmersion.domain.Proposal
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ClaimAnswer(
    claim: ClaimAnswer.Claim,
    quote: Proposal.Quote,
    date: Option[ClaimAnswer.Date] = None,
    place: Option[ClaimAnswer.Place] = None,
    confidence: library.Confidence.Wire
) derives JsonCodec

object ClaimAnswer:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Date = Date.T
  object Date extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[ClaimAnswer] = KindlingsSchema
    .derived[ClaimAnswer]
    .schema
    .modify(_.claim)(
      _.description("One atomic claim (one fact), in your words, that bears on the article's question")
    )
    .modify(_.quote)(_.description("The sentence of the record that states it, copied EXACTLY"))
    .modify(_.date)(_.description("The date or period the claim is about, as the record gives it; null if none"))
    .modify(_.place)(_.description("The place the claim is about, as the record names it; null if none"))
    .modify(_.confidence)(_.description("high | medium | low | contested — how firmly the record states it"))
