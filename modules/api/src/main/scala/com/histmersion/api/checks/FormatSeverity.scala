package com.histmersion
package api.checks
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

enum FormatSeverity derives CanEqual:
  case Error, Warning

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object FormatSeverity:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[FormatSeverity]:
    def all: Array[FormatSeverity] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: FormatSeverity): WireEnumValues.Wire =
      WireEnumValues.Wire((Wire(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[FormatSeverity] = values.find(_.toString.equalsIgnoreCase((s).value))
  given JsonValueCodec[FormatSeverity] = WireEnum.codec
  given Schema[FormatSeverity] = WireEnum.schema
