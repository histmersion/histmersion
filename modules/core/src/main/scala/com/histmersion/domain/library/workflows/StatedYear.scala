package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.AgentVerify.*
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class StatedYear(value: ToSignedYearValue, unit: YearUnit.Wire) derives JsonCodec

object StatedYear:
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[StatedYear] = KindlingsSchema
    .derived[StatedYear]
    .schema
    .modify(_.value)(_.description("The number as written, always positive (3300 for 3300 BCE)"))
    .modify(_.unit)(
      _.description("BCE | CE | calBP (calibrated years before 1950) | BP (UNcalibrated radiocarbon years)")
    )
