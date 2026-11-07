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
  type BucketYears = BucketYears.T
  object BucketYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearsPerSecond = YearsPerSecond.T
  object YearsPerSecond extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Pure: the bucket of `yearsPerSecond` × `seconds`, up to a round 1, 2 or 5 × 10ⁿ years (at least 1). */

  def years(yearsPerSecond: com.histmersion.web.PlaybackBuckets.YearsPerSecond, seconds: com.histmersion.web.PlaybackBuckets.Seconds): com.histmersion.web.PlaybackBuckets.BucketYears =
    val raw = math.max(1.0, math.abs(yearsPerSecond) * seconds)
    val p = math.pow(10, math.floor(math.log10(raw)))
    val step = List(1.0, 2.0, 5.0, 10.0).map(_ * p).find(_ >= raw).getOrElse(10 * p)
    math.max(1L, math.round(step))

  /** Pure: `span` widened outwards to whole buckets of `bucket` years. */
  def widen(span: (com.histmersion.api.Years.Astronomical, com.histmersion.api.Years.Astronomical), bucket: com.histmersion.web.PlaybackBuckets.BucketYears): (com.histmersion.api.Years.Astronomical, com.histmersion.api.Years.Astronomical) =
    val (lo, hi) = (span._1.min(span._2), span._1.max(span._2))
    val b = bucket.max(1)
    (Math.floorDiv(lo, b) * b, Math.floorDiv(hi, b) * b + b - 1)
