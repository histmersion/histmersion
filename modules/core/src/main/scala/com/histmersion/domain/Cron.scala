package com.histmersion
package domain

import java.time.{Instant, LocalDate, ZoneOffset}
import java.time.temporal.ChronoUnit
import io.github.iltotore.iron.constraint.any.Pure

/** A five-field cron expression, in UTC — the schedule of a goal's currency check or acquisition sweep
  * (docs/research-embedded-agents.md §14). `minute hour day-of-month month day-of-week`, each `*`, a number, a range
  * `a-b`, a list `a,b`, or a step (a star, a range or a start, then a slash and the step: every 15 minutes is a
  * star-slash-15); day of week 0–7 (0 and 7 are Sunday). When both day fields are restricted, a day matching EITHER
  * runs (the classic cron rule). Shortcuts: `@hourly`, `@daily`, `@weekly`, `@monthly`. Pure.
  */
final case class Cron(
    text: Cron.Text,
    minutes: List[Cron.Minutes],
    hours: List[Cron.Hours],
    days: Set[Cron.Days],
    months: Set[Cron.Months],
    weekdays: Set[Cron.Weekdays],
    anyDay: Cron.AnyDay,
    anyWeekday: Cron.AnyWeekday
):

  private def dayMatches(d: LocalDate): Boolean =
    val dom = days.contains(Cron.Days(d.getDayOfMonth))
    val dow = weekdays.contains(Cron.Weekdays(d.getDayOfWeek.getValue % 7)) // java: Monday = 1 … Sunday = 7 → 0
    months.contains(Cron.Months(d.getMonthValue)) && {
      if anyDay.value && anyWeekday.value then true
      else if anyDay.value then dow
      else if anyWeekday.value then dom
      else dom || dow
    }

  /** The first minute strictly AFTER `after` that matches, looking at most [[Cron.HorizonDays]] ahead (`None` for an
    * expression that never fires, e.g. 30 February).
    */
  def next(after: Instant): Option[Instant] =
    given CanEqual[LocalDate, LocalDate] = CanEqual.derived
    val start = after.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC)
    val day0 = start.toLocalDate
    Iterator
      .range(0, Cron.HorizonDays)
      .map(day0.plusDays(_))
      .filter(dayMatches)
      .flatMap { d =>
        val first = d == day0
        hours.iterator.filter(h => !first || h.value >= start.getHour).flatMap { h =>
          val sameHour = first && h.value == start.getHour
          minutes.iterator
            .filter(m => !sameHour || m.value >= start.getMinute)
            .map(m => d.atTime(h.value, m.value).toInstant(ZoneOffset.UTC))
        }
      }
      .nextOption()

  /** The shortest gap between the next `n` firings after `from`, in minutes (`None` if it fires less than twice) — how
    * a schedule that would run too often is refused.
    */
  def shortestGapMinutes(from: Instant, n: Cron.SampleCount = Cron.SampleCount(48)): Option[Cron.GapMinutes] =
    val times = Iterator.iterate(next(from))(_.flatMap(next)).takeWhile(_.isDefined).map(_.get).take(n.value).toList
    times.zip(times.drop(1)).map((a, b) => ChronoUnit.MINUTES.between(a, b)).minOption.map(Cron.GapMinutes(_))

object Cron:
  type GapMinutes = GapMinutes.T
  object GapMinutes extends Newtype[Long, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type SampleCount = SampleCount.T
  object SampleCount extends Newtype[Int, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Minutes = Minutes.T
  object Minutes extends Newtype[Int, Pure]
  type Hours = Hours.T
  object Hours extends Newtype[Int, Pure]
  type Days = Days.T
  object Days extends Newtype[Int, Pure]
  type Months = Months.T
  object Months extends Newtype[Int, Pure]
  type Weekdays = Weekdays.T
  object Weekdays extends Newtype[Int, Pure]
  type AnyDay = AnyDay.T
  object AnyDay extends Newtype[Boolean, Pure]
  type AnyWeekday = AnyWeekday.T
  object AnyWeekday extends Newtype[Boolean, Pure]

  /** How far ahead [[Cron.next]] looks: four years and a day covers every leap-day expression. */
  val HorizonDays = 4 * 366 + 1

  private val Shortcuts = Map(
    "@hourly" -> "0 * * * *",
    "@daily" -> "0 0 * * *",
    "@midnight" -> "0 0 * * *",
    "@weekly" -> "0 0 * * 0",
    "@monthly" -> "0 0 1 * *"
  )

  final private case class Field(name: String, min: Int, max: Int)
  private val Fields = List(
    Field("minute", 0, 59),
    Field("hour", 0, 23),
    Field("day of month", 1, 31),
    Field("month", 1, 12),
    Field("day of week", 0, 7)
  )

  /** One field → its values, or why not. */
  private def field(raw: String, f: Field): Either[String, Set[Int]] =
    def num(s: String): Either[String, Int] =
      s.toIntOption
        .filter(n => n >= f.min && n <= f.max)
        .toRight(s"${f.name}: '$s' is not a number from ${f.min} to ${f.max}")
    val parts = raw.split(",", -1).toList
    val each = parts.map { part =>
      val (range, step) = part.split("/", -1).toList match
        case List(r)    => (r, Right(1))
        case List(r, s) => (r, s.toIntOption.filter(_ > 0).toRight(s"${f.name}: step '$s' must be a positive number"))
        case _          => (part, Left(s"${f.name}: '$part' has more than one '/'"))
      for
        st <- step
        bounds <- range match
          case "*"                  => Right((f.min, f.max))
          case r if r.contains("-") =>
            r.split("-", -1).toList match
              case List(a, b) =>
                for x <- num(a); y <- num(b); _ <- Either.cond(x <= y, (), s"${f.name}: range '$r' runs backwards")
                yield (x, y)
              case _ => Left(s"${f.name}: '$r' is not a range")
          // `a/n` means from a to the end, every n
          case r => num(r).map(x => (x, if part.contains("/") then f.max else x))
      yield (bounds._1 to bounds._2 by st).toSet
    }
    each.collectFirst { case Left(m) => m } match
      case Some(m) => Left(m)
      case None    => Right(each.collect { case Right(v) => v }.flatten.toSet)

  /** Parse an expression (or a shortcut). */
  def parse(raw: Text): Either[Problem, Cron] =
    val t = Option(raw).map(_.value.trim).getOrElse("")
    val expr = Shortcuts.getOrElse(t.toLowerCase, t)
    val parts = expr.split("\\s+").toList.filter(_.nonEmpty)
    if parts.size != 5 then
      Left(
        Problem(
          s"'$t' is not a cron expression: give five fields (minute hour day-of-month month day-of-week, in UTC) " +
            s"or one of ${Shortcuts.keys.toList.sorted.mkString(", ")}"
        )
      )
    else
      val parsed = parts.zip(Fields).map((p, f) => field(p, f))
      parsed.collectFirst { case Left(m) => m } match
        case Some(m) => Left(Problem(s"'$t': $m"))
        case None    =>
          val v = parsed.collect { case Right(s) => s }
          val c = Cron(
            Text(t),
            v(0).toList.sorted.map(Minutes(_)),
            v(1).toList.sorted.map(Hours(_)),
            v(2).map(Days(_)),
            v(3).map(Months(_)),
            v(4).map(x => Weekdays(x % 7)),
            anyDay = AnyDay(parts(2) == "*"),
            anyWeekday = AnyWeekday(parts(4) == "*")
          )
          if c.next(Instant.parse("2000-01-01T00:00:00Z")).isEmpty then Left(Problem(s"'$t' never fires"))
          else Right(c)
