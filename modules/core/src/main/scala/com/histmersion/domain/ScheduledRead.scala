package com.histmersion
package domain

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

enum ScheduledRead derives CanEqual:
  case Due
  case NotDue(why: Problem)
