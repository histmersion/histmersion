package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure

enum ScheduledRead derives CanEqual:
  case Due
  case NotDue(why: Problem)
