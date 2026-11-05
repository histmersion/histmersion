package com.histmersion.web

import com.histmersion.api.library.GeoHit

/** The findings of a playback, fetched ahead in long stretches and cut to each frame's window locally (#3764). Playback
  * asked the server for the window's findings once per animation frame (~60 requests a second) and drew each answer as
  * it came: the map changed as often as answers arrived, not with the frames. Now one request covers `chunk` years
  * ahead of the playhead, and every frame draws the findings of its window from what is held. Pure.
  */
object PlaybackFeed:

  /** Pure: how many years one request covers — `seconds` of playback at `yearsPerSecond`, and at least four windows
    * (`windowHalf` either side of the playhead).
    */
  type ChunkYearsResult = ChunkYearsResult.T
  object ChunkYearsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkYearsWindowHalf = ChunkYearsWindowHalf.T
  object ChunkYearsWindowHalf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkYearsYearsPerSecond = ChunkYearsYearsPerSecond.T
  object ChunkYearsYearsPerSecond extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkYearsSeconds = ChunkYearsSeconds.T
  object ChunkYearsSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextResultPart1 = NextResultPart1.T
  object NextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextResultPart2 = NextResultPart2.T
  object NextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextCoveredTo = NextCoveredTo.T
  object NextCoveredTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextPh = NextPh.T
  object NextPh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextWh = NextWh.T
  object NextWh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextChunk = NextChunk.T
  object NextChunk extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextEnd = NextEnd.T
  object NextEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoveredResult = CoveredResult.T
  object CoveredResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoveredAskedPart1 = CoveredAskedPart1.T
  object CoveredAskedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoveredAskedPart2 = CoveredAskedPart2.T
  object CoveredAskedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoveredLimit = CoveredLimit.T
  object CoveredLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowLo = InWindowLo.T
  object InWindowLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InWindowHi = InWindowHi.T
  object InWindowHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepLo = KeepLo.T
  object KeepLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def chunkYears(windowHalf: com.histmersion.web.PlaybackFeed.ChunkYearsWindowHalf, yearsPerSecond: com.histmersion.web.PlaybackFeed.ChunkYearsYearsPerSecond, seconds: com.histmersion.web.PlaybackFeed.ChunkYearsSeconds): com.histmersion.web.PlaybackFeed.ChunkYearsResult =
    math.max(8 * math.max(1L, windowHalf), math.ceil(math.abs(yearsPerSecond) * seconds).toLong)

  /** Pure: the next period to ask for, given the findings held cover the years up to `coveredTo` (`None`: nothing yet),
    * the playhead `ph`, the window's half `wh`, the stretch `chunk` and the last year played `end`: the first stretch
    * from the window's start; then the next one once the window's end is within half a stretch of what is held; nothing
    * once the end is covered.
    */
  def next(coveredTo: Option[com.histmersion.web.PlaybackFeed.NextCoveredTo], ph: com.histmersion.web.PlaybackFeed.NextPh, wh: com.histmersion.web.PlaybackFeed.NextWh, chunk: com.histmersion.web.PlaybackFeed.NextChunk, end: com.histmersion.web.PlaybackFeed.NextEnd): Option[(com.histmersion.web.PlaybackFeed.NextResultPart1, com.histmersion.web.PlaybackFeed.NextResultPart2)] =
    val last = end + wh
    coveredTo match
      case None                                  => Some((ph - wh, math.min(last, ph + wh + chunk)))
      case Some(hi) if hi >= last                => None
      case Some(hi) if ph + wh + chunk / 2 >= hi => Some((hi + 1, math.min(last, math.max(hi + 1, ph + wh) + chunk)))
      case _                                     => None

  /** Pure: the years an answer to `asked` covers. The feed is ordered by start year and capped at `limit`: a full
    * answer may have been cut, so it covers only up to the year before its latest start (every finding starting earlier
    * is in it); a shorter one covers all it was asked for.
    */
  def covered(asked: (com.histmersion.web.PlaybackFeed.CoveredAskedPart1, com.histmersion.web.PlaybackFeed.CoveredAskedPart2), got: Seq[GeoHit], limit: com.histmersion.web.PlaybackFeed.CoveredLimit): com.histmersion.web.PlaybackFeed.CoveredResult =
    if got.size < limit then asked._2
    else got.flatMap(_.periodStart).maxOption.fold(asked._2)(s => math.max(asked._1 - 1, math.min(asked._2, s - 1)))

  /** Pure: the findings dated within [`lo`, `hi`] (overlapping it, as the server's period filter does; an undated
    * finding is never in a window).
    */
  def inWindow(points: Iterable[GeoHit], lo: com.histmersion.web.PlaybackFeed.InWindowLo, hi: com.histmersion.web.PlaybackFeed.InWindowHi): List[GeoHit] =
    points.iterator.filter(p => p.periodStart.exists(_ <= hi) && p.periodEnd.exists(_ >= lo)).toList

  /** Pure: what is worth keeping once the window starts at `lo` — every finding that ends at or after it. */
  def keep(points: Iterable[GeoHit], lo: com.histmersion.web.PlaybackFeed.KeepLo): List[GeoHit] =
    points.iterator.filter(p => p.periodEnd.forall(_ >= lo)).toList
