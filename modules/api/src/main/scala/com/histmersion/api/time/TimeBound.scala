package com.histmersion
package api.time

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw, TypeName}
import io.github.iltotore.iron.constraint.any.Pure

import java.time.{Instant, LocalDate, ZoneOffset}

type TimeBound = TimeBound.T
object TimeBound extends Newtype[String, Pure]:
  private val Relative = """(\d{1,6})\s*([dhm])""".r
  private val DateOnly = """(\d{4}-\d{2}-\d{2})""".r

  extension (s: TimeBound)
    def present: Option[TimeBound] = Option(s.value.trim).filter(_.nonEmpty).map(TimeBound(_))

    def instant(end: Boolean, now: Instant): Either[Problem, Instant] =
      def unreadable = Invalid.Unreadable.here(
        TypeName.of(TimeBound),
        Raw.of(s),
        Vector(Raw("2026-10-01"), Raw("2026-10-01T12:00:00Z"), Raw("7d"), Raw("12h"), Raw("30m"))
      )
      s.value.trim.toLowerCase match
        case Relative(n, unit) =>
          val secs = n.toLong * (unit match { case "d" => 86400L; case "h" => 3600L; case _ => 60L })
          Right(now.minusSeconds(secs))
        case DateOnly(d) =>
          scala.util
            .Try(LocalDate.parse(d))
            .toOption
            .map(day => (if end then day.plusDays(1) else day).atStartOfDay(ZoneOffset.UTC).toInstant)
            .toRight(unreadable)
        case _ => scala.util.Try(Instant.parse(s.value.trim)).toOption.toRight(unreadable)
