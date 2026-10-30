package com.histmersion.web

/** The boundaries of a playback, drawn from every state fetched so far and faded in and out by the playhead at each
  * frame (#3764): the year's states were fetched five times a second and the map replaced its shapes with each answer —
  * states jumped on and off in steps of ten or more years, and their colours with them. Pure.
  */
object PlaybackAreas:

  /** Pure: how visible a state of years [`start`, `end`] is at playhead `ph` (0…1): wholly inside its years, fading
    * over `fade` years before it begins and after it ends, gone beyond.
    */
  def alpha(ph: Double, start: Double, end: Double, fade: Double): Double =
    if ph >= start && ph <= end then 1.0
    else if fade <= 0 then 0.0
    else
      val d = if ph < start then start - ph else ph - end
      math.max(0.0, 1.0 - d / fade)

  /** Pure: the alpha drawn — `a` in steps of 1/`steps`, so a frame restyles only the states whose look changed (the map
    * recolours a state per change).
    */
  def step(a: Double, steps: Int = 20): Double = math.round(a * steps).toDouble / steps

  /** Pure: a playback state's key: its place and its years (one place's versions are different shapes). */
  def key(id: String, start: Option[Double], end: Option[Double]): String =
    s"$id@${start.fold("")(_.toLong.toString)}..${end.fold("")(_.toLong.toString)}"

  /** Pure: whether a state of years [`start`, `end`] still counts once the playhead is at `ph` (it ended no more than
    * `fade` years ago).
    */
  def live(ph: Double, end: Double, fade: Double): Boolean = end + fade >= ph
