package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.reviewarticle.EvidenceAnswer
import com.histmersion.api.research.CritiqueRef
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.domain.ReviewArticle.MaxEvidence
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Quotation
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.reviewarticle.Severity

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class FindingAnswer(
    severity: FindingAnswer.Severity,
    kind: FindingAnswer.Kind,
    where: FindingAnswer.Where,
    quote: FindingAnswer.Quote,
    problem: FindingAnswer.Problem,
    fix: FindingAnswer.Fix,
    evidence: List[EvidenceAnswer] = Nil,
    pair: Option[FindingAnswer.Pair] = None
) derives JsonCodec

object FindingAnswer:
  export Severity.Wire as Severity
  export Finding.{Kind, Where}
  export Quotation.Quote
  export Finding.{Fix, Problem}
  export CritiqueRef.Wire as Pair

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[FindingAnswer] = KindlingsSchema
    .derived[FindingAnswer]
    .schema
    .modify(_.severity)(_.description("major | minor — by the severity rules of your instructions"))
    .modify(_.kind)(
      _.description("The rule or kind your instructions name (e.g. synth, decorative, term, missing-critique)")
    )
    .modify(_.where)(_.description("The section heading, or the first 8 words of the passage"))
    .modify(_.quote)(_.description("The article's words the finding is about, copied exactly"))
    .modify(_.problem)(_.description("What is wrong, in one or two sentences"))
    .modify(_.fix)(_.description("What would fix it"))
    .modify(_.evidence)(
      _.description(
        s"The records the finding rests on (at most $MaxEvidence): citekey, offset, and the sentence copied exactly; " +
          "empty only when it rests on the article's own text"
      )
    )
    .modify(_.pair)(
      _.description(
        "missing-critique only: the critique-checklist pair it is about, as src>dst (the critic first); null otherwise"
      )
    )
