package com.histmersion.web

import com.histmersion.api.library.GeoHit

/** The findings of a playback, fetched ahead in long stretches and cut to each frame's window locally (#3764). Playback
  * asked the server for the window's findings once per animation frame (~60 requests a second) and drew each answer as
  * it came: the map changed as often as answers arrived, not with the frames. Now one request covers `chunk` years
  * ahead of the playhead, and every frame draws the findings of its window from what is held. Pure.
  */
object PlaybackFeed:
  type WindowHalfYears = WindowHalfYears.T
  object WindowHalfYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: how many years one request covers — `seconds` of playback at `yearsPerSecond`, and at least four windows
    * (`windowHalf` either side of the playhead).
    */

  def chunkYears(windowHalf: com.histmersion.web.PlaybackFeed.WindowHalfYears, yearsPerSecond: com.histmersion.web.PlaybackBuckets.YearsPerSecond, seconds: com.histmersion.web.PlaybackBuckets.Seconds): com.histmersion.web.PlaybackBuckets.BucketYears =
    com.histmersion.web.PlaybackBuckets.BucketYears(math.max(8 * math.max(1L, windowHalf.value), math.ceil(math.abs(yearsPerSecond.value) * seconds.value).toLong))

  /** Pure: the next period to ask for, given the findings held cover the years up to `coveredTo` (`None`: nothing yet),
    * the playhead `ph`, the window's half `wh`, the stretch `chunk` and the last year played `end`: the first stretch
    * from the window's start; then the next one once the window's end is within half a stretch of what is held; nothing
    * once the end is covered.
    */
  def next(coveredTo: Option[com.histmersion.api.Years.Astronomical], ph: com.histmersion.api.Years.Astronomical, wh: com.histmersion.web.PlaybackFeed.WindowHalfYears, chunk: com.histmersion.web.PlaybackBuckets.BucketYears, end: com.histmersion.api.Years.Astronomical): Option[(com.histmersion.api.Years.Astronomical, com.histmersion.api.Years.Astronomical)] =
    val last = end.value + wh.value
    val a = com.histmersion.api.Years.Astronomical
    coveredTo.map(_.value) match
      case None                                  => Some((a(ph.value - wh.value), a(math.min(last, ph.value + wh.value + chunk.value))))
      case Some(hi) if hi >= last                => None
      case Some(hi) if ph.value + wh.value + chunk.value / 2 >= hi =>
        Some((a(hi + 1), a(math.min(last, math.max(hi + 1, ph.value + wh.value) + chunk.value))))
      case _                                     => None

  /** Pure: the years an answer to `asked` covers. The feed is ordered by start year and capped at `limit`: a full
    * answer may have been cut, so it covers only up to the year before its latest start (every finding starting earlier
    * is in it); a shorter one covers all it was asked for.
    */
  def covered(asked: (com.histmersion.api.Years.Astronomical, com.histmersion.api.Years.Astronomical), got: Seq[GeoHit], limit: com.histmersion.api.Paging.Limit): com.histmersion.api.Years.Astronomical =
    if got.size < limit.value then asked._2
    else
      got
        .flatMap(_.periodStart)
        .map(_.value)
        .maxOption
        .fold(asked._2)(s => com.histmersion.api.Years.Astronomical(math.max(asked._1.value - 1, math.min(asked._2.value, s - 1))))

  /** Pure: the findings dated within [`lo`, `hi`] (overlapping it, as the server's period filter does; an undated
    * finding is never in a window).
    */
  def inWindow(points: Iterable[GeoHit], lo: com.histmersion.api.Years.Astronomical, hi: com.histmersion.api.Years.Astronomical): List[GeoHit] =
    points.iterator.filter(p => p.periodStart.exists(_.value <= hi.value) && p.periodEnd.exists(_.value >= lo.value)).toList

  /** Pure: what is worth keeping once the window starts at `lo` — every finding that ends at or after it. */
  def keep(points: Iterable[GeoHit], lo: com.histmersion.api.Years.Astronomical): List[GeoHit] =
    points.iterator.filter(p => p.periodEnd.forall(_.value >= lo.value)).toList
