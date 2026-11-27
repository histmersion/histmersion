package com.histmersion
package api
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw, TypeName}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Signed years in historical reckoning (no year 0: −1 is 1 BCE, 1 is 1 CE), as the corpus stores them, and the
  * astronomical years of ISO/XSD dates (0 is 1 BCE, −1 is 2 BCE) that Wikidata, LPF and PeriodO answer with.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
@movesTo(FeaturePlace.UtilCommon)
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
  object Text extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  export com.histmersion.api.catalogue.Year as ShiftH

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromAstronomical(a: Astronomical): Year = if a.value <= 0 then Year((a).value - 1) else Year((a).value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def toAstronomical(h: Year): Astronomical = if (h).value < 0 then Astronomical((h).value + 1)
  else if h.value == 0 then Astronomical((0).toLong)
  else Astronomical((h).value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def shift(h: Years.ShiftH, d: Shift): Year = fromAstronomical(Astronomical(toAstronomical(h).value + d.value))

  /** How a year is written: what a refusal shows. */
  private val Examples = Vector(Raw("1300 BCE"), Raw("530 CE"), Raw("-1300"))

  /** There is no year 0: 1 BCE is -1, 1 CE is 1. */
  val NoYearZero: Problem = Invalid.Unreadable.here(TypeName.of(Year), Raw("0"), Vector(Raw("-1"), Raw("1")))

  private val Circa = "^(?:c|ca|circa)\\.?\\s+".r
  private val EraBce = "(?:bce|bc|b\\.c\\.e\\.|b\\.c\\.)"
  private val EraCe = "(?:ce|ad|c\\.e\\.|a\\.d\\.)"
  private val Typed = s"^([+-]?\\d+)\\s*($EraBce|$EraCe)?$$".r
  private val EraFirst = s"^($EraBce|$EraCe)\\s*(\\d+)$$".r

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(raw: Text): Either[Problem, Year] =
    val t = Circa
      .replaceFirstIn(Option((raw).value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT), "")
      .replace(",", "")
      .trim
    def isBce(era: Era.Wire) = (era).value.matches(EraBce)
    val written = Raw(s"${(raw).value.trim}")
    def signed(num: String, era: Option[Era.Wire]): Either[Problem, Year] =
      (num).toLongOption.toRight(Invalid.Unreadable.here(TypeName.of(Year), written, Examples)).flatMap { n =>
        era match
          case None                => Right(Year(n))
          case Some(e) if isBce(e) => Right(Year(-math.abs(n)))
          case Some(_) if n < 0    =>
            Left(Invalid.OutOfRange.here(TypeName.of(Year), written, Invalid.Bounds(Some(Raw("1")), None)))
          case Some(_) => Right(Year(n))
      }
    val parsed = t match
      case Typed(num, era)    => signed((num), Option(Era.Wire(era)))
      case EraFirst(era, num) => signed((num), Some(Era.Wire(era)))
      case _                  =>
        Left(Invalid.Unreadable.here(TypeName.of(Year), written, Examples))
    parsed.flatMap(y =>
      if y.value == 0 then Left(Invalid.Unreadable.here(TypeName.of(Year), written, Vector(Raw("-1"), Raw("1"))))
      else Right(y)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isoAstronomical(xsd: Iso.Wire): Option[Astronomical] =
    val s = (xsd).value.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then Astronomical(-v) else Astronomical(v))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseIso(raw: Iso.Wire): Option[Year] =
    (raw).value.trim.toLongOption.map(v => fromAstronomical(Astronomical(v)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromJson(j: Json, text: Text => Option[Year]): Option[Year] =
    (j.asNumber
      .flatMap(_.toLong)
      .orElse(j.asString.flatMap {
        val refactorFunction = text;
        (refactorValue) => (refactorFunction(Text(refactorValue))).map(refactorValue2 => (refactorValue2).value)
      })
      .orElse(j.asNumber.flatMap(_.toDouble).map(_.round)))
      .map(refactorValue4 => Year(refactorValue4))
