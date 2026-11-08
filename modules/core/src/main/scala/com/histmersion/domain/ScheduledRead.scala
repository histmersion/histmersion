package com.histmersion
package domain

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

enum ScheduledRead:
  case Due
  case NotDue(why: ScheduledRead.NotDue.Why)

object ScheduledRead:
  object NotDue:
    type Why = Why.T
    object Why extends RefinedType[String, Pure]
