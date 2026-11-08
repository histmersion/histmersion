package com.histmersion
package api

import io.circe.Json
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year

/** Signed years in historical reckoning (no year 0: −1 is 1 BCE, 1 is 1 CE), as the corpus stores them, and the
  * astronomical years of ISO/XSD dates (0 is 1 BCE, −1 is 2 BCE) that Wikidata, LPF and PeriodO answer with.
  */
object Years:
  object Iso:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  object Era:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  type Astronomical = Astronomical.T
  object Astronomical extends Newtype[Long, Pure]
  type Shift = Shift.T
  object Shift extends Newtype[Long, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  export com.histmersion.api.catalogue.Year as ShiftH
  type NoYearZero = Problem

  def fromAstronomical(a: Astronomical): Year = if a.value <= 0 then Year((a).value - 1) else Year((a).value)

  def toAstronomical(h: Year): Astronomical = if (h).value < 0 then Astronomical((h).value + 1)
  else if h.value == 0 then Astronomical((0).toLong)
  else Astronomical((h).value)

  def shift(h: Years.ShiftH, d: Shift): Year = fromAstronomical(Astronomical(toAstronomical(h).value + d.value))

  val NoYearZero: NoYearZero = Problem("there is no year 0 (1 BCE is -1, 1 CE is 1)")

  private val Circa = "^(?:c|ca|circa)\\.?\\s+".r
  private val EraBce = "(?:bce|bc|b\\.c\\.e\\.|b\\.c\\.)"
  private val EraCe = "(?:ce|ad|c\\.e\\.|a\\.d\\.)"
  private val Typed = s"^([+-]?\\d+)\\s*($EraBce|$EraCe)?$$".r
  private val EraFirst = s"^($EraBce|$EraCe)\\s*(\\d+)$$".r

  def parse(raw: Text): Either[Problem, Year] =
    val t = Circa
      .replaceFirstIn(Option((raw).value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT), "")
      .replace(",", "")
      .trim
    def isBce(era: Era.Wire) = (era).value.matches(EraBce)
    def signed(num: String, era: Option[Era.Wire]): Either[Problem, Year] =
      (num).toLongOption.toRight(Problem(s"'${(raw).value.trim}' is not a year")).flatMap { n =>
        era match
          case None                => Right(Year(n))
          case Some(e) if isBce(e) => Right(Year(-math.abs(n)))
          case Some(_) if n < 0    =>
            Left(Problem(s"'${(raw).value.trim}': a CE year is positive; write the BCE year instead"))
          case Some(_) => Right(Year(n))
      }
    val parsed = t match
      case Typed(num, era)    => signed((num), Option(Era.Wire(era)))
      case EraFirst(era, num) => signed((num), Some(Era.Wire(era)))
      case _                  => Left(Problem(s"'${(raw).value.trim}' is not a year (e.g. 1300 BCE, 530 CE, -1300)"))
    parsed.flatMap(y => if y.value == 0 then Left(Problem(s"'${(raw).value.trim}': $NoYearZero")) else Right(y))

  def isoAstronomical(xsd: Iso.Wire): Option[Astronomical] =
    val s = (xsd).value.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then Astronomical(-v) else Astronomical(v))

  def parseIso(raw: Iso.Wire): Option[Year] =
    (raw).value.trim.toLongOption.map(v => fromAstronomical(Astronomical(v)))

  def fromJson(j: Json, text: Text => Option[Year]): Option[Year] =
    (j.asNumber
      .flatMap(_.toLong)
      .orElse(j.asString.flatMap {
        val refactorFunction = text;
        (refactorValue) => (refactorFunction(Text(refactorValue))).map(refactorValue2 => (refactorValue2).value)
      })
      .orElse(j.asNumber.map(_.toDouble.round)))
      .map(refactorValue4 => Year(refactorValue4))
