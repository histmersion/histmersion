package com.histmersion
package domain

enum ScheduledRead:
  case Due

  /** Not due yet, and why: the account of the schedule's decision, not a failure. */
  case NotDue(why: ReadSchedule.Rationale)
