package com.histmersion.util.time

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
    def reached: com.histmersion.util.time.Timer.Deadline.ReachedResult
    def exceeded: com.histmersion.util.time.Timer.Deadline.ExceededResult

  object Deadline:
    type ReachedResult = ReachedResult.T
    object ReachedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExceededResult = ExceededResult.T
    object ExceededResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
