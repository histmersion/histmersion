package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.annotations.description

final case class RecheckAnswer(
    @description("One check per finding you were asked to verify") checks: List[CheckAnswer] = Nil,
    @description("New problems in the EDITED passages (a regression the fix made); outside them only a factual error, with evidence") findings: List[ReportedFinding] = Nil,
    @description("At most three short rules for the fixer, learned from a fix that did not fix or from a regression (each under 25 words)") lessons: List[RecheckAnswer.Lessons] = Nil
) derives JsonCodec, KindlingsSchema, Schema

object RecheckAnswer:
  export RecheckedFindings.Lessons
