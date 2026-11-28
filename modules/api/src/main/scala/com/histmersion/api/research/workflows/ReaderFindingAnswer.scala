package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.annotations.description

final case class ReaderFindingAnswer(
    @description("major | minor — by the severity rules of your instructions") severity: ReaderFindingAnswer.Severity,
    @description("The kind your instructions name (e.g. term, opaque, why, lost)") kind: ReaderFindingAnswer.Kind,
    @description("The section heading, or the first 8 words of the passage") where: ReaderFindingAnswer.Where,
    @description("The article's words the finding is about, copied exactly") quote: ReaderFindingAnswer.Quote,
    @description("What is wrong, in one or two sentences") problem: ReaderFindingAnswer.Problem,
    @description("What would fix it") fix: ReaderFindingAnswer.Fix
) derives JsonCodec, KindlingsSchema, Schema

object ReaderFindingAnswer:
  export Severity.Wire as Severity
  export ReviewFinding.{Kind, Where}
  export Quotation.Quote
  export ReviewFinding.{Fix, Problem}
