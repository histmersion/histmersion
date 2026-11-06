package com.histmersion.api.exchange
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum MergeJobState:
  case Running, Done, Failed, Interrupted

object MergeJobState:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[MergeJobState]:
    def all: Array[MergeJobState] = MergeJobState.values
    def wire(e: MergeJobState): com.histmersion.api.exchange.MergeJobState.WireResult = _root_.com.histmersion.api.exchange.MergeJobState.WireResult(e.toString.toLowerCase(java.util.Locale.ROOT))
    def fromWire(s: com.histmersion.api.exchange.MergeJobState.FromWireS): Option[MergeJobState] = MergeJobState.values.find(_.toString.equalsIgnoreCase(s.trim))
  given Encoder[MergeJobState] = WireEnum.encoder
  given Decoder[MergeJobState] = WireEnum.decoder
  given Schema[MergeJobState] = WireEnum.schema
