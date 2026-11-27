package com.histmersion
package domain.activity
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw}

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Where an older page of the activity list starts: the entry it follows, by its time and its key (entries are ordered
  * by time, then key, both descending). On the wire: `<ISO instant>~<key>`.
  */
@movesTo(FeaturePlace.AutomationActivity)
final case class ActivityCursor(at: Instant, key: ActivityCursor.Key):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: ActivityCursor.Wire = ActivityCursor.Wire(s"$at~$key")

object ActivityCursor:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  type Key = Key.T
  object Key extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Either[Problem, ActivityCursor] =
    s.value.trim.split("~", 2) match
      case Array(at, key) if key.nonEmpty =>
        scala.util
          .Try(Instant.parse(at))
          .toOption
          .map(ActivityCursor(_, Key(key)))
          .toRight(Invalid.Broken.here(Invalid.Rule.ActivityPageTime(Raw.of(s))))
      case _ => Left(Invalid.Broken.here(Invalid.Rule.ActivityPage(Raw.of(s))))
