package com.histmersion
package plugins.media

import java.util.Locale
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Free-text object dates as museums write them (`1550-1196 BCE`, `100 BCE-100 CE`, `ca. 3300-ca. 2250 BCE`,
  * `12th-13th century`, `5th century BCE`, `2nd millennium BCE`, `1883 or 1884`) read as a signed span, BCE negative.
  * Conservative: an uncertain date (`?`), a range whose ends run backwards, or anything else it does not read is `None`
  * — and a record without dates is never matched. Pure.
  */
object FreeTextDates:
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  type Year = Year.T
  object Year extends RefinedType[Long, Pure]


  private val Part =
    """^(?:(?:late|early|mid|middle|first half of|second half of|end of|beginning of)[\s-]+)?(?:the\s+)?([0-9]{1,5})(?:st|nd|rd|th)?(?:\s*(century|centuries|millennium|millennia))?(?:\s*(bce|bc|ce|ad))?$""".r

  private enum Unit:
    case Year, Century, Millennium

  final private case class Bound(n: Long, unit: Option[Unit], ordinal: Boolean, bce: Option[Boolean])

  private def bound(raw: String): Option[Bound] =
    raw.trim match
      case Part(n, unit, era) =>
        val u = Option(unit).map(x => if x.startsWith("cent") then Unit.Century else Unit.Millennium)
        val e = Option(era).map(x => x.startsWith("b"))
        Some(Bound(n.toLong, u, raw.trim.matches("""^.*?[0-9]+(st|nd|rd|th).*$"""), e))
      case _ => None

  /** Pure: the signed span of one bound, given the unit and era it carries or inherits. */
  private def span(b: Bound): Option[(Long, Long)] =
    val bce = b.bce.contains(true)
    b.unit.getOrElse(Unit.Year) match
      case Unit.Year if b.ordinal => None
      case Unit.Year              => Option.when(b.n > 0)(if bce then (-b.n, -b.n) else (b.n, b.n))
      case Unit.Century           =>
        Option.when(b.n > 0)(if bce then (-b.n * 100, -(b.n * 100 - 99)) else ((b.n - 1) * 100 + 1, b.n * 100))
      case Unit.Millennium =>
        Option.when(b.n > 0)(if bce then (-b.n * 1000, -((b.n - 1) * 1000 + 1)) else ((b.n - 1) * 1000 + 1, b.n * 1000))

  /** Pure: the span a date text states, or `None`. */
  def parse(text: Text): Option[(Year, Year)] =
    spanOf(Option(text).fold("")(_.value)).map((from, to) => (Year(from), Year(to)))

  private def spanOf(text: String): Option[(Long, Long)] =
    val s0 = text.toLowerCase(Locale.ROOT).trim
    if s0.isEmpty || s0.contains("?") then None
    else
      // eras first (so `b.c.` is not taken for `c.` = circa), then the hedges, then the separators
      val s = s0
        .replace("b.c.e.", "bce")
        .replace("b.c.", "bc")
        .replace("a.d.", "ad")
        .replace("c.e.", "ce")
        .replaceAll("""\b(ca\.|circa|c\.|about|approx\.|approximately)\s*""", "")
        .replace('–', '-')
        .replace('—', '-')
        .replaceAll("""\b(late|early|mid)-""", "$1 ")
        .replaceAll("""\s+or\s+""", "-")
        .replaceAll("""\s+to\s+""", "-")
        .replaceAll("""\s+""", " ")
        .trim
      val parts = s.split("""\s*-\s*""").toList.map(_.trim).filter(_.nonEmpty)
      parts.map(bound) match
        case List(Some(a))           => span(a)
        case List(Some(a0), Some(b)) =>
          // the first bound inherits the unit and era it does not state (`12th-13th century`, `305-30 BCE`)
          val a = a0.copy(unit = a0.unit.orElse(Option.when(a0.ordinal)(b.unit).flatten), bce = a0.bce.orElse(b.bce))
          for
            (s1, _) <- span(a)
            (_, e2) <- span(b)
            if s1 <= e2
          yield (s1, e2)
        case _ => None
