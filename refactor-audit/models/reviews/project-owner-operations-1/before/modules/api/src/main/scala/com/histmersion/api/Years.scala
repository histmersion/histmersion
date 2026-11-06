package com.histmersion.api

import io.circe.Json

/** Signed years in historical reckoning (no year 0: −1 is 1 BCE, 1 is 1 CE), as the corpus stores them, and the
  * astronomical years of ISO/XSD dates (0 is 1 BCE, −1 is 2 BCE) that Wikidata, LPF and PeriodO answer with.
  */
object Years:

  type ParseIsBceEra = ParseIsBceEra.T
  object ParseIsBceEra extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSignedResultPart1 = ParseSignedResultPart1.T
  object ParseSignedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSignedResultPart2 = ParseSignedResultPart2.T
  object ParseSignedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSignedNum = ParseSignedNum.T
  object ParseSignedNum extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSignedEra = ParseSignedEra.T
  object ParseSignedEra extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FromAstronomicalResult = FromAstronomicalResult.T
  object FromAstronomicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromAstronomicalA = FromAstronomicalA.T
  object FromAstronomicalA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToAstronomicalResult = ToAstronomicalResult.T
  object ToAstronomicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToAstronomicalH = ToAstronomicalH.T
  object ToAstronomicalH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShiftResult = ShiftResult.T
  object ShiftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShiftH = ShiftH.T
  object ShiftH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShiftD = ShiftD.T
  object ShiftD extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoYearZero = NoYearZero.T
  object NoYearZero extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart2 = ParseResultPart2.T
  object ParseResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsoAstronomicalResult = IsoAstronomicalResult.T
  object IsoAstronomicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsoAstronomicalXsd = IsoAstronomicalXsd.T
  object IsoAstronomicalXsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseIsoResult = ParseIsoResult.T
  object ParseIsoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseIsoRaw = ParseIsoRaw.T
  object ParseIsoRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromJsonResult = FromJsonResult.T
  object FromJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromJsonTextArg1 = FromJsonTextArg1.T
  object FromJsonTextArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromJsonTextResult = FromJsonTextResult.T
  object FromJsonTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromAstronomical(a: com.histmersion.api.Years.FromAstronomicalA): com.histmersion.api.Years.FromAstronomicalResult = if a <= 0 then a - 1 else a

  def toAstronomical(h: com.histmersion.api.Years.ToAstronomicalH): com.histmersion.api.Years.ToAstronomicalResult = if h < 0 then h + 1 else if h == 0 then _root_.com.histmersion.api.Years.ToAstronomicalResult((0).toLong) else h

  def shift(h: com.histmersion.api.Years.ShiftH, d: com.histmersion.api.Years.ShiftD): com.histmersion.api.Years.ShiftResult = fromAstronomical(toAstronomical(h) + d)

  val NoYearZero: com.histmersion.api.Years.NoYearZero = _root_.com.histmersion.api.Years.NoYearZero("there is no year 0 (1 BCE is -1, 1 CE is 1)")

  private val Circa = "^(?:c|ca|circa)\\.?\\s+".r
  private val EraBce = "(?:bce|bc|b\\.c\\.e\\.|b\\.c\\.)"
  private val EraCe = "(?:ce|ad|c\\.e\\.|a\\.d\\.)"
  private val Typed = s"^([+-]?\\d+)\\s*($EraBce|$EraCe)?$$".r
  private val EraFirst = s"^($EraBce|$EraCe)\\s*(\\d+)$$".r

  def parse(raw: com.histmersion.api.Years.ParseRaw): Either[com.histmersion.api.Years.ParseResultPart1, com.histmersion.api.Years.ParseResultPart2] =
    val t = Circa
      .replaceFirstIn(Option(raw).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT), "")
      .replace(",", "")
      .trim
    def isBce(era: com.histmersion.api.Years.ParseIsBceEra) = era.matches(EraBce)
    def signed(num: com.histmersion.api.Years.ParseSignedNum, era: Option[com.histmersion.api.Years.ParseSignedEra]): Either[com.histmersion.api.Years.ParseSignedResultPart1, com.histmersion.api.Years.ParseSignedResultPart2] =
      num.toLongOption.toRight(s"'${raw.trim}' is not a year").flatMap { n =>
        era match
          case None                => Right(n)
          case Some(e) if isBce(e) => Right(-math.abs(n))
          case Some(_) if n < 0    => Left(s"'${raw.trim}': a CE year is positive; write the BCE year instead")
          case Some(_)             => Right(n)
      }
    val parsed = t match
      case Typed(num, era)    => signed(num, Option(era))
      case EraFirst(era, num) => signed(num, Some(era))
      case _                  => Left(s"'${raw.trim}' is not a year (e.g. 1300 BCE, 530 CE, -1300)")
    parsed.flatMap(y => if y == 0 then Left(s"'${raw.trim}': $NoYearZero") else Right(y))

  def isoAstronomical(xsd: com.histmersion.api.Years.IsoAstronomicalXsd): Option[com.histmersion.api.Years.IsoAstronomicalResult] =
    val s = xsd.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then -v else v)

  def parseIso(raw: com.histmersion.api.Years.ParseIsoRaw): Option[com.histmersion.api.Years.ParseIsoResult] =
    raw.trim.toLongOption.map(fromAstronomical)

  def fromJson(j: Json, text: com.histmersion.api.Years.FromJsonTextArg1 => Option[com.histmersion.api.Years.FromJsonTextResult]): Option[com.histmersion.api.Years.FromJsonResult] =
    j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(text)).orElse(j.asNumber.map(_.toDouble.round))
