package com.histmersion
package api.checks
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class FormatCheck(
    text: FormatCheck.Text,
    field: FormatField = FormatField.Body,
    fix: FormatCheck.Fix = FormatCheck.Fix.Report
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FormatCheck:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  enum Fix derives CanEqual:
    case Fix, Report
  object Fix extends TwoStates[Fix]:
    extension (fix: Fix) def fix: Boolean = fix == Fix
