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
    def wire(e: MergeJobState): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((_root_.com.histmersion.api.exchange.MergeJobState.WireResult(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[MergeJobState] = MergeJobState.values.find(_.toString.equalsIgnoreCase((s).value.trim))
  given Encoder[MergeJobState] = WireEnum.encoder
  given Decoder[MergeJobState] = WireEnum.decoder
  given Schema[MergeJobState] = WireEnum.schema
