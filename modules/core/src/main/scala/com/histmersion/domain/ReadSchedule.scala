package com.histmersion
package domain

import java.time.Duration

enum ReadSchedule derives CanEqual:
  case Off
  case Every(interval: Duration)
