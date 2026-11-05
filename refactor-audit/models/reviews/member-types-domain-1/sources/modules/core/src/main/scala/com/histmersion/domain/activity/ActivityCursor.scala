package com.histmersion.domain.activity

import java.time.Instant

/** Where an older page of the activity list starts: the entry it follows, by its time and its key (entries are ordered
  * by time, then key, both descending). On the wire: `<ISO instant>~<key>`.
  */
final case class ActivityCursor(at: Instant, key: ActivityCursor.Key):
  def wire: com.histmersion.domain.activity.ActivityCursor.WireResult = s"$at~$key"

object ActivityCursor:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.domain.activity.ActivityCursor.ParseS): Either[com.histmersion.domain.activity.ActivityCursor.ParseResultPart1, ActivityCursor] =
    s.trim.split("~", 2) match
      case Array(at, key) if key.nonEmpty =>
        scala.util
          .Try(Instant.parse(at))
          .toOption
          .map(ActivityCursor(_, key))
          .toRight(s"'$s' is not a page of the activity list (its time is not an instant)")
      case _ => Left(s"'$s' is not a page of the activity list")
