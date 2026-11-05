package com.histmersion.web

/** The boundaries of a playback, drawn from every state fetched so far and faded in and out by the playhead at each
  * frame (#3764): the year's states were fetched five times a second and the map replaced its shapes with each answer —
  * states jumped on and off in steps of ten or more years, and their colours with them. Pure.
  */
object PlaybackAreas:

  /** Pure: how visible a state of years [`start`, `end`] is at playhead `ph` (0…1): wholly inside its years, fading
    * over `fade` years before it begins and after it ends, gone beyond.
    */
  type AlphaResult = AlphaResult.T
  object AlphaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AlphaPh = AlphaPh.T
  object AlphaPh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AlphaStart = AlphaStart.T
  object AlphaStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AlphaEnd = AlphaEnd.T
  object AlphaEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AlphaFade = AlphaFade.T
  object AlphaFade extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepResult = StepResult.T
  object StepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepA = StepA.T
  object StepA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepSteps = StepSteps.T
  object StepSteps extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyId = KeyId.T
  object KeyId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyStart = KeyStart.T
  object KeyStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyEnd = KeyEnd.T
  object KeyEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiveResult = LiveResult.T
  object LiveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LivePh = LivePh.T
  object LivePh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiveEnd = LiveEnd.T
  object LiveEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiveFade = LiveFade.T
  object LiveFade extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def alpha(ph: com.histmersion.web.PlaybackAreas.AlphaPh, start: com.histmersion.web.PlaybackAreas.AlphaStart, end: com.histmersion.web.PlaybackAreas.AlphaEnd, fade: com.histmersion.web.PlaybackAreas.AlphaFade): com.histmersion.web.PlaybackAreas.AlphaResult =
    if ph >= start && ph <= end then 1.0
    else if fade <= 0 then 0.0
    else
      val d = if ph < start then start - ph else ph - end
      math.max(0.0, 1.0 - d / fade)

  /** Pure: the alpha drawn — `a` in steps of 1/`steps`, so a frame restyles only the states whose look changed (the map
    * recolours a state per change).
    */
  def step(a: com.histmersion.web.PlaybackAreas.StepA, steps: com.histmersion.web.PlaybackAreas.StepSteps = 20): com.histmersion.web.PlaybackAreas.StepResult = math.round(a * steps).toDouble / steps

  /** Pure: a playback state's key: its place and its years (one place's versions are different shapes). */
  def key(id: com.histmersion.web.PlaybackAreas.KeyId, start: Option[com.histmersion.web.PlaybackAreas.KeyStart], end: Option[com.histmersion.web.PlaybackAreas.KeyEnd]): com.histmersion.web.PlaybackAreas.KeyResult =
    s"$id@${start.fold("")(_.toLong.toString)}..${end.fold("")(_.toLong.toString)}"

  /** Pure: whether a state of years [`start`, `end`] still counts once the playhead is at `ph` (it ended no more than
    * `fade` years ago).
    */
  def live(ph: com.histmersion.web.PlaybackAreas.LivePh, end: com.histmersion.web.PlaybackAreas.LiveEnd, fade: com.histmersion.web.PlaybackAreas.LiveFade): com.histmersion.web.PlaybackAreas.LiveResult = end + fade >= ph
