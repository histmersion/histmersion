package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research.workflows.CheckAnswer
import com.histmersion.domain.research.workflows.FindingAnswer
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.domain.research.workflows.Recheck

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RecheckAnswer(
    checks: List[CheckAnswer] = Nil,
    findings: List[FindingAnswer] = Nil,
    lessons: List[RecheckAnswer.Lessons] = Nil
) derives JsonCodec

object RecheckAnswer:
  export Recheck.Lessons

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[RecheckAnswer] = KindlingsSchema
    .derived[RecheckAnswer]
    .schema
    .modify(_.checks)(_.description("One check per finding you were asked to verify"))
    .modify(_.findings)(
      _.description(
        "New problems in the EDITED passages (a regression the fix made); outside them only a factual error, with evidence"
      )
    )
    .modify(_.lessons)(
      _.description(
        "At most three short rules for the fixer, learned from a fix that did not fix or from a regression (each under 25 words)"
      )
    )
