package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure

enum ScheduledRead derives CanEqual:
  case Due
  case NotDue(why: ScheduledRead.NotDue.Why)

object ScheduledRead:
  object NotDue:
    type Why = Why.T
    object Why extends Newtype[String, Pure]
