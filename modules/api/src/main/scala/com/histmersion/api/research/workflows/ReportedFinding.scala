package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.research.CritiqueRef
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise
import sttp.tapir.Schema.annotations.description

@customise(_.name(SName("FindingAnswer")))
final case class ReportedFinding(
    @description("major | minor — by the severity rules of your instructions") severity: ReportedFinding.Severity,
    @description("The rule or kind your instructions name (e.g. synth, decorative, term, missing-critique)") kind: ReportedFinding.Kind,
    @description("The section heading, or the first 8 words of the passage") where: ReportedFinding.Where,
    @description("The article's words the finding is about, copied exactly") quote: ReportedFinding.Quote,
    @description("What is wrong, in one or two sentences") problem: ReportedFinding.Problem,
    @description("What would fix it") fix: ReportedFinding.Fix,
    @description("The records the finding rests on (at most 3): citekey, offset, and the sentence copied exactly; empty only when it rests on the article's own text") evidence: List[EvidenceAnswer] = Nil,
    @description("missing-critique only: the critique-checklist pair it is about, as src>dst (the critic first); null otherwise") pair: Option[ReportedFinding.Pair] = None
) derives JsonCodec, KindlingsSchema, Schema

object ReportedFinding:
  export Severity.Wire as Severity
  export ReviewFinding.{Kind, Where}
  export Quotation.Quote
  export ReviewFinding.{Fix, Problem}
  export CritiqueRef.Wire as Pair
