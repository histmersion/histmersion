package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.reviewarticle.FindingAnswer
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Quotation
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.reviewarticle.Severity

final case class ReaderFindingAnswer(
    severity: ReaderFindingAnswer.Severity,
    kind: ReaderFindingAnswer.Kind,
    where: ReaderFindingAnswer.Where,
    quote: ReaderFindingAnswer.Quote,
    problem: ReaderFindingAnswer.Problem,
    fix: ReaderFindingAnswer.Fix
) derives JsonCodec:
  def asFinding: FindingAnswer = FindingAnswer(severity, kind, where, quote, problem, fix)

object ReaderFindingAnswer:
  export Severity.Wire as Severity
  export Finding.{Kind, Where}
  export Quotation.Quote
  export Finding.{Fix, Problem}

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[ReaderFindingAnswer] = KindlingsSchema
    .derived[ReaderFindingAnswer]
    .schema
    .modify(_.severity)(_.description("major | minor — by the severity rules of your instructions"))
    .modify(_.kind)(_.description("The kind your instructions name (e.g. term, opaque, why, lost)"))
    .modify(_.where)(_.description("The section heading, or the first 8 words of the passage"))
    .modify(_.quote)(_.description("The article's words the finding is about, copied exactly"))
    .modify(_.problem)(_.description("What is wrong, in one or two sentences"))
    .modify(_.fix)(_.description("What would fix it"))
