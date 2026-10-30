package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** Why a PeriodO definition was not imported (#771). */
enum PeriodSkip:
  /** No readable start or stop year. */
  case NoSpan

  /** It ends before it starts. */
  case EndsBeforeStart

  /** A bound lies outside [[PeriodO.MinYear]]..[[PeriodO.MaxYear]]. */
  case OutOfRange

  /** Far longer than anything else its authority states for the region ([[PeriodO.SpanFactor]]). */
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
