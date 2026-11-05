package com.histmersion.api.catalogue
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.StrictJson
import com.histmersion.api.WireEnum
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import scala.annotation.tailrec
import sttp.tapir.Schema
final case class Period(start: com.histmersion.api.catalogue.Year, end: com.histmersion.api.catalogue.Year):
  def isPoint: Boolean = start == end

object Period:

  final case class Detail(
      period: HistoricalPeriod,
      parent: Option[HistoricalPeriod] = None,
      children: List[HistoricalPeriod] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class ImportResult(
      periods: ImportResult.Periods,
      roots: ImportResult.Roots = 0,
      presets: ImportResult.Presets = 0,
      skipped: List[SkippedPeriod] = Nil,
      skippedCount: ImportResult.SkippedCount = 0,
      removed: ImportResult.Removed = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ImportResult:
    type Periods = Periods.T
    object Periods extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Roots = Roots.T
    object Roots extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Presets = Presets.T
    object Presets extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SkippedCount = SkippedCount.T
    object SkippedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Removed = Removed.T
    object Removed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Input(start: com.histmersion.api.catalogue.Year, end: com.histmersion.api.catalogue.Year) derives KindlingsSchema, com.histmersion.api.json.Schema
  object Input:


    given Codec.AsObject[Input] = StrictJson.codec(KindlingsCodecAsObject.derived[Input])

  final case class Row(periodStart: Option[com.histmersion.api.catalogue.Year], periodEnd: Option[com.histmersion.api.catalogue.Year])

  enum Skip:
    case NoSpan

    case EndsBeforeStart

    case OutOfRange

    case ImplausibleSpan

  object Skip:
    def wireOf(p: Skip): String = p match
      case NoSpan          => "no_span"
      case EndsBeforeStart => "ends_before_start"
      case OutOfRange      => "out_of_range"
      case ImplausibleSpan => "implausible_span"

    given WireEnumValues[Skip]:
      def all: Array[Skip] = Skip.values
      def wire(e: Skip): String = wireOf(e)
      def fromWire(s: String): Option[Skip] = Skip.values.find(wireOf(_) == s)

    given io.circe.Encoder[Skip] = WireEnum.encoder
    given io.circe.Decoder[Skip] = WireEnum.decoder
    given Schema[Skip] = WireEnum.schema
