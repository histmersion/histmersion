package com.histmersion
package domain

import java.time.Duration
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.AutomationScheduler)
enum ReadSchedule:
  case Off
  case Every(interval: Duration)
