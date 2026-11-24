package com.histmersion
package api.exchange
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema

import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

enum MergeJobState derives CanEqual:
  case Running, Done, Failed, Interrupted

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object MergeJobState:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[MergeJobState]:
    def all: Array[MergeJobState] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: MergeJobState): WireEnumValues.Wire =
      WireEnumValues.Wire((Wire(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[MergeJobState] =
      values.find(_.toString.equalsIgnoreCase((s).value.trim))
  given JsonValueCodec[MergeJobState] = WireEnum.codec
  given Schema[MergeJobState] = WireEnum.schema
