package com.histmersion.util.time

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}

final class SystemTimer extends Timer:
  def start(): Timer.Measure =
    val started = System.nanoTime()
    new Timer.Measure:
      def elapsed: FiniteDuration = FiniteDuration(System.nanoTime() - started, NANOSECONDS)
