package com.histmersion
package domain

import java.time.Duration
import com.histmersion.lint.{FeaturePlace, movesTo}

@movesTo(FeaturePlace.AutomationScheduler)
enum ReadSchedule:
  case Off
  case Every(interval: Duration)
