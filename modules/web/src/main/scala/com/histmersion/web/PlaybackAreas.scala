package com.histmersion
package web

import io.github.iltotore.iron.constraint.any.Pure

/** The boundaries of a playback, drawn from every state fetched so far and faded in and out by the playhead at each
  * frame (#3764): the year's states were fetched five times a second and the map replaced its shapes with each answer —
  * states jumped on and off in steps of ten or more years, and their colours with them. Pure.
  */
object PlaybackAreas:
  type AreaKey = AreaKey.T
  object AreaKey extends Newtype[String, Pure]
  type FadeYears = FadeYears.T
  object FadeYears extends Newtype[Double, Pure]
  type FractionalYear = FractionalYear.T
  object FractionalYear extends Newtype[Double, Pure]
  type Opacity = Opacity.T
  object Opacity extends Newtype[Double, Pure]
  type StepCount = StepCount.T
  object StepCount extends Newtype[Int, Pure]

  /** Pure: how visible a state of years [`start`, `end`] is at playhead `ph` (0…1): wholly inside its years, fading
    * over `fade` years before it begins and after it ends, gone beyond.
    */

  def alpha(ph: FractionalYear, start: FractionalYear, end: FractionalYear, fade: FadeYears): Opacity =
    if ph.value >= start.value && ph.value <= end.value then Opacity(1.0)
    else if fade.value <= 0 then Opacity(0.0)
    else
      val d = if ph.value < start.value then start.value - ph.value else ph.value - end.value
      Opacity(math.max(0.0, 1.0 - d / fade.value))

  /** Pure: the alpha drawn — `a` in steps of 1/`steps`, so a frame restyles only the states whose look changed (the map
    * recolours a state per change).
    */
  def step(a: Opacity, steps: StepCount = StepCount(20)): Opacity = Opacity(math.round(a.value * steps.value).toDouble / steps.value)

  /** Pure: a playback state's key: its place and its years (one place's versions are different shapes). */
  def key(id: AreaKey, start: Option[FractionalYear], end: Option[FractionalYear]): AreaKey =
    AreaKey(s"${id.value}@${start.fold("")(_.value.toLong.toString)}..${end.fold("")(_.value.toLong.toString)}")

  /** Pure: whether a state of years [`start`, `end`] still counts once the playhead is at `ph` (it ended no more than
    * `fade` years ago).
    */
  def live(ph: FractionalYear, end: FractionalYear, fade: FadeYears): Boolean = end.value + fade.value >= ph.value
