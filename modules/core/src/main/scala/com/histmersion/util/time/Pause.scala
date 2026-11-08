package com.histmersion
package util.time

import scala.concurrent.duration.FiniteDuration

trait Pause:
  def sleep(duration: FiniteDuration): Unit
