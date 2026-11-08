package com.histmersion
package domain.activity

import com.histmersion.domain.Problem
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure

/** Where an older page of the activity list starts: the entry it follows, by its time and its key (entries are ordered
  * by time, then key, both descending). On the wire: `<ISO instant>~<key>`.
  */
final case class ActivityCursor(at: Instant, key: ActivityCursor.Key):
  def wire: ActivityCursor.Wire = ActivityCursor.Wire(s"$at~$key")

object ActivityCursor:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  type Key = Key.T
  object Key extends Newtype[String, Pure]

  def parse(s: Wire): Either[Problem, ActivityCursor] =
    s.value.trim.split("~", 2) match
      case Array(at, key) if key.nonEmpty =>
        scala.util
          .Try(Instant.parse(at))
          .toOption
          .map(ActivityCursor(_, Key(key)))
          .toRight(Problem(s"'$s' is not a page of the activity list (its time is not an instant)"))
      case _ => Left(Problem(s"'$s' is not a page of the activity list"))
