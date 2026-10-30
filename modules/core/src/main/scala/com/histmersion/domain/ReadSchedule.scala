package com.histmersion.domain

import java.time.Duration

enum ReadSchedule:
  case Off
  case Every(interval: Duration)
