package com.histmersion.api.catalogue
import com.histmersion.api.given
import com.histmersion.WireEnumValues
import com.histmersion.api.StrictJson
import com.histmersion.api.WireEnum
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import scala.annotation.tailrec
import sttp.tapir.Schema
final case class Period(start: Long, end: Long):
  def isPoint: Boolean = start == end

object Period:

  final case class Detail(
      period: HistoricalPeriod,
      parent: Option[HistoricalPeriod] = None,
      children: List[HistoricalPeriod] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Detail:
    given Schema[Detail] = summon[KindlingsSchema[Detail]].schema

  final case class ImportResult(
      periods: Int,
      roots: Int = 0,
      presets: Int = 0,
      skipped: List[SkippedPeriod] = Nil,
      skippedCount: Int = 0,
      removed: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ImportResult:
    given Schema[ImportResult] = summon[KindlingsSchema[ImportResult]].schema

  final case class Input(start: Long, end: Long) derives KindlingsSchema
  object Input:
    given Schema[Input] = summon[KindlingsSchema[Input]].schema

    given Codec.AsObject[Input] = StrictJson.codec(KindlingsCodecAsObject.derived[Input])

  final case class Row(periodStart: Option[Long], periodEnd: Option[Long])

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
