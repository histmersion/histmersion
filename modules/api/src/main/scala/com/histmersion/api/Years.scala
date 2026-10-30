package com.histmersion.api

import io.circe.Json

/** Signed years in historical reckoning (no year 0: −1 is 1 BCE, 1 is 1 CE), as the corpus stores them, and the
  * astronomical years of ISO/XSD dates (0 is 1 BCE, −1 is 2 BCE) that Wikidata, LPF and PeriodO answer with.
  */
object Years:

  def fromAstronomical(a: Long): Long = if a <= 0 then a - 1 else a

  def toAstronomical(h: Long): Long = if h < 0 then h + 1 else if h == 0 then 0 else h

  def shift(h: Long, d: Long): Long = fromAstronomical(toAstronomical(h) + d)

  val NoYearZero: String = "there is no year 0 (1 BCE is -1, 1 CE is 1)"

  private val Circa = "^(?:c|ca|circa)\\.?\\s+".r
  private val EraBce = "(?:bce|bc|b\\.c\\.e\\.|b\\.c\\.)"
  private val EraCe = "(?:ce|ad|c\\.e\\.|a\\.d\\.)"
  private val Typed = s"^([+-]?\\d+)\\s*($EraBce|$EraCe)?$$".r
  private val EraFirst = s"^($EraBce|$EraCe)\\s*(\\d+)$$".r

  def parse(raw: String): Either[String, Long] =
    val t = Circa
      .replaceFirstIn(Option(raw).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT), "")
      .replace(",", "")
      .trim
    def isBce(era: String) = era.matches(EraBce)
    def signed(num: String, era: Option[String]): Either[String, Long] =
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

  def isoAstronomical(xsd: String): Option[Long] =
    val s = xsd.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then -v else v)

  def parseIso(raw: String): Option[Long] =
    raw.trim.toLongOption.map(fromAstronomical)

  def fromJson(j: Json, text: String => Option[Long]): Option[Long] =
    j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(text)).orElse(j.asNumber.map(_.toDouble.round))
