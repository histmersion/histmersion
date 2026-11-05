package com.histmersion.util.time

import scala.concurrent.duration.FiniteDuration

final class ThreadPause extends Pause:
  def sleep(duration: FiniteDuration): Unit =
    java.util.concurrent.TimeUnit.NANOSECONDS.sleep(duration.toNanos)
