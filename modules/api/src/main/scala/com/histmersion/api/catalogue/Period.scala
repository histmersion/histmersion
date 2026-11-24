package com.histmersion
package api.catalogue
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.StrictJson
import com.histmersion.api.WireEnum
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import scala.annotation.tailrec
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
final case class Period(start: Year, end: Year):
  def isPoint: Boolean = (start == end)

object Period:
  type End = End.T
  object End extends Newtype[Long, Pure]
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type IsoYear = IsoYear.T
  object IsoYear extends Newtype[Long, Pure]
  type Start = Start.T
  object Start extends Newtype[Long, Pure]

  final case class Detail(
      period: HistoricalPeriod,
      parent: Option[HistoricalPeriod] = None,
      children: List[HistoricalPeriod] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  final case class ImportResult(
      periods: ImportResult.Periods,
      roots: ImportResult.Roots = ImportResult.Roots(0),
      presets: ImportResult.Presets = ImportResult.Presets(0),
      skipped: List[SkippedPeriod] = Nil,
      skippedCount: ImportResult.SkippedCount = ImportResult.SkippedCount(0),
      removed: ImportResult.Removed = ImportResult.Removed(0)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object ImportResult:
    type Periods = Periods.T
    object Periods extends Newtype[Int, Pure]
    type Roots = Roots.T
    object Roots extends Newtype[Int, Pure]
    type Presets = Presets.T
    object Presets extends Newtype[Int, Pure]
    type SkippedCount = SkippedCount.T
    object SkippedCount extends Newtype[Int, Pure]
    type Removed = Removed.T
    object Removed extends Newtype[Int, Pure]

  final case class Input(start: Year, end: Year) derives StrictJson, KindlingsSchema, api.json.Schema

  final case class Row(periodStart: Option[Year], periodEnd: Option[Year])

  enum Skip derives CanEqual:
    case NoSpan

    case EndsBeforeStart

    case OutOfRange

    case ImplausibleSpan

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Skip:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def wireOf(p: Skip): Wire = p match
      case NoSpan          => Wire("no_span")
      case EndsBeforeStart => Wire("ends_before_start")
      case OutOfRange      => Wire("out_of_range")
      case ImplausibleSpan => Wire("implausible_span")

    given WireEnumValues[Skip]:
      def all: Array[Skip] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Skip): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      def fromWire(s: WireEnumValues.Wire): Option[Skip] = values.find(wire(_) == s)

    given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[Skip] = WireEnum.codec
    given Schema[Skip] = WireEnum.schema
