package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.research.CritiqueRef
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise

@customise(_.name(SName("Finding")))
final case class ReviewFinding(
    id: ReviewFinding.Id,
    reviewer: Reviewer,
    severity: Severity,
    kind: ReviewFinding.Kind,
    where: ReviewFinding.Where,
    quote: ReviewFinding.Quote,
    problem: ReviewFinding.Problem,
    fix: ReviewFinding.Fix,
    evidence: List[Quotation] = Nil,
    escalated: ReviewFinding.Escalated = ReviewFinding.Escalated(false),
    pair: Option[CritiqueRef] = None
) derives JsonCodec, KindlingsSchema, Schema

object ReviewFinding:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Where = Where.T
  object Where extends Newtype[String, Pure]
  export Quotation.Quote
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Fix = Fix.T
  object Fix extends Newtype[String, Pure]
  type Escalated = Escalated.T
  object Escalated extends Newtype[Boolean, Pure]
