package com.histmersion.domain

enum ScheduledRead:
  case Due
  case NotDue(why: String)
