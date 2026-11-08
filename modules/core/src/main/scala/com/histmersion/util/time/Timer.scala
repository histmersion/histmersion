package com.histmersion
package util.time

import scala.concurrent.duration.FiniteDuration

trait Timer:
  def start(): Timer.Measure

  final def after(duration: FiniteDuration): Timer.Deadline =
    val measure = start()
    new Timer.Deadline:
      def reached: Boolean = measure.elapsed >= duration
      def exceeded: Boolean = measure.elapsed > duration

object Timer:

  trait Measure:
    def elapsed: FiniteDuration

  trait Deadline:
    def reached: Boolean
    def exceeded: Boolean
