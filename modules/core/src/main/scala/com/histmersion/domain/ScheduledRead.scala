package com.histmersion.domain

enum ScheduledRead:
  case Due
  case NotDue(why: ScheduledRead.NotDue.Why)

object ScheduledRead:
  object NotDue:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
