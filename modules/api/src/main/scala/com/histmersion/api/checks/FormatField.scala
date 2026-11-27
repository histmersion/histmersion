package com.histmersion
package api.checks
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

enum FormatField derives CanEqual:
  case Body, Summary, Notes

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object FormatField:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[FormatField]:
    def all: Array[FormatField] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: FormatField): WireEnumValues.Wire =
      WireEnumValues.Wire((Wire(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[FormatField] = values.find(_.toString.equalsIgnoreCase((s).value))
  given JsonValueCodec[FormatField] = WireEnum.codec
  given Schema[FormatField] = WireEnum.schema
