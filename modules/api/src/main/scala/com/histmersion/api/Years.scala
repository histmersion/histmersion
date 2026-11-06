package com.histmersion.api

import io.circe.Json

/** Signed years in historical reckoning (no year 0: −1 is 1 BCE, 1 is 1 CE), as the corpus stores them, and the
  * astronomical years of ISO/XSD dates (0 is 1 BCE, −1 is 2 BCE) that Wikidata, LPF and PeriodO answer with.
  */
object Years:
  object Iso:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Era:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Astronomical = Astronomical.T
  object Astronomical extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Shift = Shift.T
  object Shift extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type ShiftH = _root_.com.histmersion.api.catalogue.Year
  val ShiftH: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year
  type NoYearZero = NoYearZeroValue
  type NoYearZeroValue = com.histmersion.api.Years.Problem
  val NoYearZeroValue: _root_.com.histmersion.api.Years.Problem.type = _root_.com.histmersion.api.Years.Problem

  def fromAstronomical(a: com.histmersion.api.Years.Astronomical): com.histmersion.api.catalogue.Year = if a.value <= 0 then _root_.com.histmersion.api.catalogue.Year((a).value - 1) else _root_.com.histmersion.api.catalogue.Year((a).value)

  def toAstronomical(h: com.histmersion.api.catalogue.Year): com.histmersion.api.Years.Astronomical = if (h).value < 0 then _root_.com.histmersion.api.Years.Astronomical((h).value + 1) else if h.value == 0 then _root_.com.histmersion.api.Years.Astronomical((0).toLong) else _root_.com.histmersion.api.Years.Astronomical((h).value)

  def shift(h: com.histmersion.api.Years.ShiftH, d: com.histmersion.api.Years.Shift): com.histmersion.api.catalogue.Year = fromAstronomical(_root_.com.histmersion.api.Years.Astronomical(toAstronomical(h).value + d.value))

  val NoYearZero: com.histmersion.api.Years.NoYearZero = _root_.com.histmersion.api.Years.Problem("there is no year 0 (1 BCE is -1, 1 CE is 1)")

  private val Circa = "^(?:c|ca|circa)\\.?\\s+".r
  private val EraBce = "(?:bce|bc|b\\.c\\.e\\.|b\\.c\\.)"
  private val EraCe = "(?:ce|ad|c\\.e\\.|a\\.d\\.)"
  private val Typed = s"^([+-]?\\d+)\\s*($EraBce|$EraCe)?$$".r
  private val EraFirst = s"^($EraBce|$EraCe)\\s*(\\d+)$$".r

  def parse(raw: com.histmersion.api.Years.Text): Either[com.histmersion.api.Years.Problem, com.histmersion.api.catalogue.Year] =
    val t = Circa
      .replaceFirstIn(Option((raw).value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT), "")
      .replace(",", "")
      .trim
    def isBce(era: com.histmersion.api.Years.Era.Wire) = (era).value.matches(EraBce)
    def signed(num: String, era: Option[com.histmersion.api.Years.Era.Wire]): Either[com.histmersion.api.Years.Problem, com.histmersion.api.catalogue.Year] =
      (num).toLongOption.toRight(Problem(s"'${(raw).value.trim}' is not a year")).flatMap { n =>
        era match
          case None                => Right(_root_.com.histmersion.api.catalogue.Year(n))
          case Some(e) if isBce(e) => Right(_root_.com.histmersion.api.catalogue.Year(-math.abs(n)))
          case Some(_) if n < 0    => Left(Problem(s"'${(raw).value.trim}': a CE year is positive; write the BCE year instead"))
          case Some(_)             => Right(_root_.com.histmersion.api.catalogue.Year(n))
      }
    val parsed = t match
      case Typed(num, era)    => signed((num), Option(_root_.com.histmersion.api.Years.Era.Wire(era)))
      case EraFirst(era, num) => signed((num), Some(_root_.com.histmersion.api.Years.Era.Wire(era)))
      case _                  => Left(s"'${(raw).value.trim}' is not a year (e.g. 1300 BCE, 530 CE, -1300)")
    parsed.flatMap(y => if y.value == 0 then Left(Problem(s"'${(raw).value.trim}': $NoYearZero")) else Right(y))

  def isoAstronomical(xsd: com.histmersion.api.Years.Iso.Wire): Option[com.histmersion.api.Years.Astronomical] =
    val s = (xsd).value.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then _root_.com.histmersion.api.Years.Astronomical(-v) else _root_.com.histmersion.api.Years.Astronomical(v))

  def parseIso(raw: com.histmersion.api.Years.Iso.Wire): Option[com.histmersion.api.catalogue.Year] =
    (raw).value.trim.toLongOption.map(v => fromAstronomical(_root_.com.histmersion.api.Years.Astronomical(v)))

  def fromJson(j: Json, text: com.histmersion.api.Years.Text => Option[com.histmersion.api.catalogue.Year]): Option[com.histmersion.api.catalogue.Year] =
    (j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap({ val refactorFunction = text; (refactorValue) => (refactorFunction(_root_.com.histmersion.api.Years.Text(refactorValue))).map(refactorValue2 => (refactorValue2).value) })).orElse(j.asNumber.map(_.toDouble.round))).map(refactorValue4 => _root_.com.histmersion.api.catalogue.Year(refactorValue4))
