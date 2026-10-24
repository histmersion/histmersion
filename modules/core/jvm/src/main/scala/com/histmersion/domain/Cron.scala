package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.time.{Instant, LocalDate, ZoneOffset}
import java.time.temporal.ChronoUnit

/** A five-field cron expression, in UTC — the schedule of a goal's currency check or acquisition
  * sweep (docs/research-embedded-agents.md §14). `minute hour day-of-month month day-of-week`, each
  * `*`, a number, a range `a-b`, a list `a,b`, or a step (a star, a range or a start, then a slash
  * and the step: every 15 minutes is a star-slash-15); day of week 0–7
  * (0 and 7 are Sunday). When both day fields are restricted, a day matching EITHER runs (the
  * classic cron rule). Shortcuts: `@hourly`, `@daily`, `@weekly`, `@monthly`. Pure. */
final case class Cron(text: String, minutes: List[Int], hours: List[Int], days: Set[Int], months: Set[Int],
    weekdays: Set[Int], anyDay: Boolean, anyWeekday: Boolean):

  private def dayMatches(d: LocalDate): Boolean =
    val dom = days.contains(d.getDayOfMonth)
    val dow = weekdays.contains(d.getDayOfWeek.getValue % 7) // java: Monday = 1 … Sunday = 7 → 0
    months.contains(d.getMonthValue) && {
      if anyDay && anyWeekday then true
      else if anyDay then dow
      else if anyWeekday then dom
      else dom || dow
    }

  /** The first minute strictly AFTER `after` that matches, looking at most [[Cron.HorizonDays]] ahead
    * (`None` for an expression that never fires, e.g. 30 February). */
  def next(after: Instant): Option[Instant] =
    val start = after.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC)
    val day0  = start.toLocalDate
    Iterator.range(0, Cron.HorizonDays).map(day0.plusDays(_)).filter(dayMatches).flatMap { d =>
      val first = d == day0
      hours.iterator.filter(h => !first || h >= start.getHour).flatMap { h =>
        val sameHour = first && h == start.getHour
        minutes.iterator.filter(m => !sameHour || m >= start.getMinute)
          .map(m => d.atTime(h, m).toInstant(ZoneOffset.UTC))
      }
    }.nextOption()

  /** The shortest gap between the next `n` firings after `from`, in minutes (`None` if it fires less
    * than twice) — how a schedule that would run too often is refused. */
  def shortestGapMinutes(from: Instant, n: Int = 48): Option[Long] =
    val times = Iterator.iterate(next(from))(_.flatMap(next)).takeWhile(_.isDefined).map(_.get).take(n).toList
    times.zip(times.drop(1)).map((a, b) => ChronoUnit.MINUTES.between(a, b)).minOption

object Cron:
  /** How far ahead [[Cron.next]] looks: four years and a day covers every leap-day expression. */
  val HorizonDays = 4 * 366 + 1

  private val Shortcuts = Map(
    "@hourly"  -> "0 * * * *",
    "@daily"   -> "0 0 * * *",
    "@midnight" -> "0 0 * * *",
    "@weekly"  -> "0 0 * * 0",
    "@monthly" -> "0 0 1 * *")

  private final case class Field(name: String, min: Int, max: Int)
  private val Fields = List(Field("minute", 0, 59), Field("hour", 0, 23), Field("day of month", 1, 31),
    Field("month", 1, 12), Field("day of week", 0, 7))

  /** One field → its values, or why not. */
  private def field(raw: String, f: Field): Either[String, Set[Int]] =
    def num(s: String): Either[String, Int] =
      s.toIntOption.filter(n => n >= f.min && n <= f.max)
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
          case "*" => Right((f.min, f.max))
          case r if r.contains("-") =>
            r.split("-", -1).toList match
              case List(a, b) =>
                for x <- num(a); y <- num(b); _ <- Either.cond(x <= y, (), s"${f.name}: range '$r' runs backwards") yield (x, y)
              case _ => Left(s"${f.name}: '$r' is not a range")
          // `a/n` means from a to the end, every n
          case r => num(r).map(x => (x, if part.contains("/") then f.max else x))
      yield (bounds._1 to bounds._2 by st).toSet
    }
    each.collectFirst { case Left(m) => m } match
      case Some(m) => Left(m)
      case None    => Right(each.collect { case Right(v) => v }.flatten.toSet)

  /** Parse an expression (or a shortcut). */
  def parse(raw: String): Either[String, Cron] =
    val t    = Option(raw).map(_.trim).getOrElse("")
    val expr = Shortcuts.getOrElse(t.toLowerCase, t)
    val parts = expr.split("\\s+").toList.filter(_.nonEmpty)
    if parts.size != 5 then
      Left(s"'$t' is not a cron expression: give five fields (minute hour day-of-month month day-of-week, in UTC) " +
        s"or one of ${Shortcuts.keys.toList.sorted.mkString(", ")}")
    else
      val parsed = parts.zip(Fields).map((p, f) => field(p, f))
      parsed.collectFirst { case Left(m) => m } match
        case Some(m) => Left(s"'$t': $m")
        case None =>
          val v = parsed.collect { case Right(s) => s }
          val c = Cron(t, v(0).toList.sorted, v(1).toList.sorted, v(2), v(3), v(4).map(_ % 7),
            anyDay = parts(2) == "*", anyWeekday = parts(4) == "*")
          if c.next(Instant.parse("2000-01-01T00:00:00Z")).isEmpty then Left(s"'$t' never fires")
          else Right(c)
