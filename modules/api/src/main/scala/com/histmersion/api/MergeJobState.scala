package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.values.WireEnumValues

enum MergeJobState:
  case Running, Done, Failed, Interrupted

object MergeJobState:
  given WireEnumValues[MergeJobState]:
    def all: Array[MergeJobState] = MergeJobState.values
    def wire(e: MergeJobState): String = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[MergeJobState] = MergeJobState.values.find(_.toString.equalsIgnoreCase(s.trim))
  given Encoder[MergeJobState] = WireEnum.encoder
  given Decoder[MergeJobState] = WireEnum.decoder
  given Schema[MergeJobState] = WireEnum.schema
