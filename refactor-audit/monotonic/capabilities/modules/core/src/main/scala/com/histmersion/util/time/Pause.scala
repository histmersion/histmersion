package com.histmersion.util.time

import scala.concurrent.duration.FiniteDuration

trait Pause:
  def sleep(duration: FiniteDuration): Unit
