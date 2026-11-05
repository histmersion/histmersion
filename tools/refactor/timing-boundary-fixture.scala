//> using scala 3.10.0
package com.histmersion.domain.detectorfixtures.timing

import com.histmersion.util.time.{Timer, Pause}

object Consumer:
  def timer: Timer = new com.histmersion.util.time.SystemTimer // expect: timer-factory
  def pause: Pause = new com.histmersion.util.time.ThreadPause // expect: timer-factory
  def supplied(timer: Timer, pause: Pause): Timer.Measure = timer.start()

object Composition:
  def timer: Timer = new com.histmersion.util.time.SystemTimer
  def pause: Pause = new com.histmersion.util.time.ThreadPause
