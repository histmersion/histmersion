package com.histmersion
package api.exchange
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema

import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

enum MergeJobState derives CanEqual:
  case Running, Done, Failed, Interrupted

object MergeJobState:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[MergeJobState]:
    def all: Array[MergeJobState] = values
    def wire(e: MergeJobState): WireEnumValues.Wire =
      WireEnumValues.Wire((Wire(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    def fromWire(s: WireEnumValues.Wire): Option[MergeJobState] =
      values.find(_.toString.equalsIgnoreCase((s).value.trim))
  given JsonValueCodec[MergeJobState] = WireEnum.codec
  given Schema[MergeJobState] = WireEnum.schema
