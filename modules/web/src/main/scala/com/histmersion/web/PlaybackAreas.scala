package com.histmersion.web

/** The boundaries of a playback, drawn from every state fetched so far and faded in and out by the playhead at each
  * frame (#3764): the year's states were fetched five times a second and the map replaced its shapes with each answer —
  * states jumped on and off in steps of ten or more years, and their colours with them. Pure.
  */
object PlaybackAreas:
  type AreaKey = AreaKey.T
  object AreaKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FadeYears = FadeYears.T
  object FadeYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FractionalYear = FractionalYear.T
  object FractionalYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Opacity = Opacity.T
  object Opacity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepCount = StepCount.T
  object StepCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: how visible a state of years [`start`, `end`] is at playhead `ph` (0…1): wholly inside its years, fading
    * over `fade` years before it begins and after it ends, gone beyond.
    */

  def alpha(ph: com.histmersion.web.PlaybackAreas.FractionalYear, start: com.histmersion.web.PlaybackAreas.FractionalYear, end: com.histmersion.web.PlaybackAreas.FractionalYear, fade: com.histmersion.web.PlaybackAreas.FadeYears): com.histmersion.web.PlaybackAreas.Opacity =
    if ph.value >= start.value && ph.value <= end.value then com.histmersion.web.PlaybackAreas.Opacity(1.0)
    else if fade.value <= 0 then com.histmersion.web.PlaybackAreas.Opacity(0.0)
    else
      val d = if ph.value < start.value then start.value - ph.value else ph.value - end.value
      com.histmersion.web.PlaybackAreas.Opacity(math.max(0.0, 1.0 - d / fade.value))

  /** Pure: the alpha drawn — `a` in steps of 1/`steps`, so a frame restyles only the states whose look changed (the map
    * recolours a state per change).
    */
  def step(a: com.histmersion.web.PlaybackAreas.Opacity, steps: com.histmersion.web.PlaybackAreas.StepCount = com.histmersion.web.PlaybackAreas.StepCount(20)): com.histmersion.web.PlaybackAreas.Opacity = com.histmersion.web.PlaybackAreas.Opacity(math.round(a.value * steps.value).toDouble / steps.value)

  /** Pure: a playback state's key: its place and its years (one place's versions are different shapes). */
  def key(id: com.histmersion.web.PlaybackAreas.AreaKey, start: Option[com.histmersion.web.PlaybackAreas.FractionalYear], end: Option[com.histmersion.web.PlaybackAreas.FractionalYear]): com.histmersion.web.PlaybackAreas.AreaKey =
    com.histmersion.web.PlaybackAreas.AreaKey(s"${id.value}@${start.fold("")(_.value.toLong.toString)}..${end.fold("")(_.value.toLong.toString)}")

  /** Pure: whether a state of years [`start`, `end`] still counts once the playhead is at `ph` (it ended no more than
    * `fade` years ago).
    */
  def live(ph: com.histmersion.web.PlaybackAreas.FractionalYear, end: com.histmersion.web.PlaybackAreas.FractionalYear, fade: com.histmersion.web.PlaybackAreas.FadeYears): Boolean = end.value + fade.value >= ph.value
