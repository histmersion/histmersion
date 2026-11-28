package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import io.github.iltotore.iron.constraint.any.Pure

final case class Window(seconds: Window.Seconds):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text: String =
    if (seconds).value % 86400 == 0 then (s"${(seconds).value / 86400}d")
    else if (seconds).value % 3600 == 0 then (s"${(seconds).value / 3600}h")
    else (s"${(seconds).value / 60}m")

object Window:
  type MaxSecondsNumber = MaxSecondsNumber.T
  object MaxSecondsNumber extends Newtype[Long, Pure]
  type MaxSeconds = MaxSecondsValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type MaxSecondsValue = MaxSecondsNumber
  val MaxSecondsValue: MaxSecondsNumber.type = MaxSecondsNumber

  type Seconds = Seconds.T
  object Seconds extends Newtype[Long, Pure]

  private val Unit = Map('m' -> 60L, 'h' -> 3600L, 'd' -> 86400L, 'w' -> 604800L)
  private val Names = Map("hour" -> 3600L, "day" -> 86400L, "week" -> 604800L, "month" -> 30L * 86400L)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val MaxSeconds: MaxSeconds = MaxSecondsNumber(90L * 86400L)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: LimitSpec.Window): Either[Problem, Window] =
    val t = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
    val secs = Names.get(t).orElse {
      Option.when(t.length >= 2)(t.last).flatMap(Unit.get).flatMap(u => t.init.toLongOption.filter(_ > 0).map(_ * u))
    }
    secs match
      case None =>
        Left(
          Invalid.Unreadable.here(
            TypeName.of(LimitSpec.Window),
            Raw(s"$s"),
            Vector(Raw("hour"), Raw("day"), Raw("week"), Raw("month"), Raw("5h"), Raw("7d"))
          )
        )
      case Some(x) if x < 60 || x > MaxSeconds.value =>
        Left(
          Invalid.OutOfRange
            .here(TypeName.of(LimitSpec.Window), Raw(s"$s"), Invalid.Bounds(Some(Raw("1m")), Some(Raw("90d"))))
        )
      case Some(x) => Right(Window(Seconds(x)))
