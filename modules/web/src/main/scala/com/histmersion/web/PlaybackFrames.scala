package com.histmersion.web

import com.histmersion.api.GeoHit

/** The findings of a playback, fetched ahead in long stretches and cut to each frame's window locally
  * (#3764). Playback asked the server for the window's findings once per animation frame (~60 requests
  * a second) and drew each answer as it came: the map changed as often as answers arrived, not with
  * the frames. Now one request covers `chunk` years ahead of the playhead, and every frame draws the
  * findings of its window from what is held. Pure. */
object PlaybackFeed:

  /** Pure: how many years one request covers — `seconds` of playback at `yearsPerSecond`, and at least
    * four windows (`windowHalf` either side of the playhead). */
  def chunkYears(windowHalf: Long, yearsPerSecond: Double, seconds: Double): Long =
    math.max(8 * math.max(1L, windowHalf), math.ceil(math.abs(yearsPerSecond) * seconds).toLong)

  /** Pure: the next period to ask for, given the findings held cover the years up to `coveredTo` (`None`:
    * nothing yet), the playhead `ph`, the window's half `wh`, the stretch `chunk` and the last year
    * played `end`: the first stretch from the window's start; then the next one once the window's end
    * is within half a stretch of what is held; nothing once the end is covered. */
  def next(coveredTo: Option[Long], ph: Long, wh: Long, chunk: Long, end: Long): Option[(Long, Long)] =
    val last = end + wh
    coveredTo match
      case None => Some((ph - wh, math.min(last, ph + wh + chunk)))
      case Some(hi) if hi >= last => None
      case Some(hi) if ph + wh + chunk / 2 >= hi => Some((hi + 1, math.min(last, math.max(hi + 1, ph + wh) + chunk)))
      case _ => None

  /** Pure: the years an answer to `asked` covers. The feed is ordered by start year and capped at
    * `limit`: a full answer may have been cut, so it covers only up to the year before its latest start
    * (every finding starting earlier is in it); a shorter one covers all it was asked for. */
  def covered(asked: (Long, Long), got: Seq[GeoHit], limit: Int): Long =
    if got.size < limit then asked._2
    else got.flatMap(_.periodStart).maxOption.fold(asked._2)(s => math.max(asked._1 - 1, math.min(asked._2, s - 1)))

  /** Pure: the findings dated within [`lo`, `hi`] (overlapping it, as the server's period filter does;
    * an undated finding is never in a window). */
  def inWindow(points: Iterable[GeoHit], lo: Long, hi: Long): List[GeoHit] =
    points.iterator.filter(p => p.periodStart.exists(_ <= hi) && p.periodEnd.exists(_ >= lo)).toList

  /** Pure: what is worth keeping once the window starts at `lo` — every finding that ends at or after it. */
  def keep(points: Iterable[GeoHit], lo: Long): List[GeoHit] =
    points.iterator.filter(p => p.periodEnd.forall(_ >= lo)).toList

/** The boundaries of a playback, drawn from every state fetched so far and faded in and out by the
  * playhead at each frame (#3764): the year's states were fetched five times a second and the map
  * replaced its shapes with each answer — states jumped on and off in steps of ten or more years, and
  * their colours with them. Pure. */
object PlaybackAreas:

  /** Pure: how visible a state of years [`start`, `end`] is at playhead `ph` (0…1): wholly inside its
    * years, fading over `fade` years before it begins and after it ends, gone beyond. */
  def alpha(ph: Double, start: Double, end: Double, fade: Double): Double =
    if ph >= start && ph <= end then 1.0
    else if fade <= 0 then 0.0
    else
      val d = if ph < start then start - ph else ph - end
      math.max(0.0, 1.0 - d / fade)

  /** Pure: the alpha drawn — `a` in steps of 1/`steps`, so a frame restyles only the states whose look
    * changed (the map recolours a state per change). */
  def step(a: Double, steps: Int = 20): Double = math.round(a * steps).toDouble / steps

  /** Pure: a playback state's key: its place and its years (one place's versions are different shapes). */
  def key(id: String, start: Option[Double], end: Option[Double]): String =
    s"$id@${start.fold("")(_.toLong.toString)}..${end.fold("")(_.toLong.toString)}"

  /** Pure: whether a state of years [`start`, `end`] still counts once the playhead is at `ph` (it
    * ended no more than `fade` years ago). */
  def live(ph: Double, end: Double, fade: Double): Boolean = end + fade >= ph
