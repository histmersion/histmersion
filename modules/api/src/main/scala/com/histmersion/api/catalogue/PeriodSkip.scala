package com.histmersion.api.catalogue
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum PeriodSkip:
  case NoSpan

  case EndsBeforeStart

  case OutOfRange

  case ImplausibleSpan

object PeriodSkip:
  def wireOf(p: PeriodSkip): String = p match
    case NoSpan          => "no_span"
    case EndsBeforeStart => "ends_before_start"
    case OutOfRange      => "out_of_range"
    case ImplausibleSpan => "implausible_span"

  given WireEnumValues[PeriodSkip]:
    def all: Array[PeriodSkip] = PeriodSkip.values
    def wire(e: PeriodSkip): String = wireOf(e)
    def fromWire(s: String): Option[PeriodSkip] = PeriodSkip.values.find(wireOf(_) == s)

  given io.circe.Encoder[PeriodSkip] = WireEnum.encoder
  given io.circe.Decoder[PeriodSkip] = WireEnum.decoder
  given Schema[PeriodSkip] = WireEnum.schema
