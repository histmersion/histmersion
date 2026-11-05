package com.histmersion.domain.activity

import java.time.Instant

/** Where an older page of the activity list starts: the entry it follows, by its time and its key (entries are ordered
  * by time, then key, both descending). On the wire: `<ISO instant>~<key>`.
  */
final case class ActivityCursor(at: Instant, key: ActivityCursor.Key):
  def wire: String = s"$at~$key"

object ActivityCursor:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: String): Either[String, ActivityCursor] =
    s.trim.split("~", 2) match
      case Array(at, key) if key.nonEmpty =>
        scala.util
          .Try(Instant.parse(at))
          .toOption
          .map(ActivityCursor(_, key))
          .toRight(s"'$s' is not a page of the activity list (its time is not an instant)")
      case _ => Left(s"'$s' is not a page of the activity list")
