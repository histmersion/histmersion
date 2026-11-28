package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class StatedYear(
    @description("The number as written, always positive (3300 for 3300 BCE)") value: StatedYear.ToSignedYearValue,
    @description(
      "BCE | CE | calBP (calibrated years before 1950) | BP (UNcalibrated radiocarbon years)"
    ) unit: StatedYear.Wire
) derives JsonCodec,
      KindlingsSchema,
      Schema

object StatedYear:
  type ToSignedYearValue = ToSignedYearValue.T
  object ToSignedYearValue extends Newtype[Long, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
