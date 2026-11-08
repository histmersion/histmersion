package com.histmersion
package web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Years

/** The span the tiled layers are drawn for in playback: the playhead's window widened to whole buckets of the years
  * `ui.map-tiles.playback-bucket-seconds` of playback cover (a round number of years), so the tiles' URL — and the
  * request for them — changes once a bucket, and a replay finds them kept.
  */
object PlaybackBuckets:
  type BucketYears = BucketYears.T
  object BucketYears extends Newtype[Long, Pure]
  type Seconds = Seconds.T
  object Seconds extends Newtype[Double, Pure]
  type YearsPerSecond = YearsPerSecond.T
  object YearsPerSecond extends Newtype[Double, Pure]
  /** Pure: the bucket of `yearsPerSecond` × `seconds`, up to a round 1, 2 or 5 × 10ⁿ years (at least 1). */

  def years(yearsPerSecond: YearsPerSecond, seconds: Seconds): BucketYears =
    val raw = math.max(1.0, math.abs(yearsPerSecond.value) * seconds.value)
    val p = math.pow(10, math.floor(math.log10(raw)))
    val step = List(1.0, 2.0, 5.0, 10.0).map(_ * p).find(_ >= raw).getOrElse(10 * p)
    BucketYears(math.max(1L, math.round(step)))

  /** Pure: `span` widened outwards to whole buckets of `bucket` years. */
  def widen(span: (Years.Astronomical, Years.Astronomical), bucket: BucketYears): (Years.Astronomical, Years.Astronomical) =
    val (lo, hi) = (span._1.value.min(span._2.value), span._1.value.max(span._2.value))
    val b = bucket.value.max(1)
    (Years.Astronomical(Math.floorDiv(lo, b) * b), Years.Astronomical(Math.floorDiv(hi, b) * b + b - 1))
