package com.histmersion.web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** The span the tiled layers are drawn for in playback: the playhead's window widened to whole buckets of the years
  * `ui.map-tiles.playback-bucket-seconds` of playback cover (a round number of years), so the tiles' URL — and the
  * request for them — changes once a bucket, and a replay finds them kept.
  */
object PlaybackBuckets:
  /** Pure: the bucket of `yearsPerSecond` × `seconds`, up to a round 1, 2 or 5 × 10ⁿ years (at least 1). */
  type YearsResult = YearsResult.T
  object YearsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearsYearsPerSecond = YearsYearsPerSecond.T
  object YearsYearsPerSecond extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearsSeconds = YearsSeconds.T
  object YearsSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WidenResultPart1 = WidenResultPart1.T
  object WidenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WidenResultPart2 = WidenResultPart2.T
  object WidenResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WidenSpanPart1 = WidenSpanPart1.T
  object WidenSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WidenSpanPart2 = WidenSpanPart2.T
  object WidenSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WidenBucket = WidenBucket.T
  object WidenBucket extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def years(yearsPerSecond: com.histmersion.web.PlaybackBuckets.YearsYearsPerSecond, seconds: com.histmersion.web.PlaybackBuckets.YearsSeconds): com.histmersion.web.PlaybackBuckets.YearsResult =
    val raw = math.max(1.0, math.abs(yearsPerSecond) * seconds)
    val p = math.pow(10, math.floor(math.log10(raw)))
    val step = List(1.0, 2.0, 5.0, 10.0).map(_ * p).find(_ >= raw).getOrElse(10 * p)
    math.max(1L, math.round(step))

  /** Pure: `span` widened outwards to whole buckets of `bucket` years. */
  def widen(span: (com.histmersion.web.PlaybackBuckets.WidenSpanPart1, com.histmersion.web.PlaybackBuckets.WidenSpanPart2), bucket: com.histmersion.web.PlaybackBuckets.WidenBucket): (com.histmersion.web.PlaybackBuckets.WidenResultPart1, com.histmersion.web.PlaybackBuckets.WidenResultPart2) =
    val (lo, hi) = (span._1.min(span._2), span._1.max(span._2))
    val b = bucket.max(1)
    (Math.floorDiv(lo, b) * b, Math.floorDiv(hi, b) * b + b - 1)
