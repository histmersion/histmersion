package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** Fanning a bubble's points out around it (the "spiderfy" of the marker-cluster plugins): a ring for a few, a spiral
  * for more, each point on a thin leg back to the spot. Pure but for the legs' markup.
  */
object Spiderfy:
  type OffsetCoordinate = OffsetCoordinate.T
  object OffsetCoordinate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Svg = Svg.T
  object Svg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** A fanned-out point's marker; the bubble it came out of; the legs' element. */
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Separation = Separation.T
  object Separation extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Side = Side.T
  object Side extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Member = "hd-spider"
  val Fanned = "hd-spidered"
  val Legs = "hd-spider-legs"

  /** Fanned-out points stand over every other marker of their pane. */
  val Z = 2000

  /** Up to this many go on one ring; more on a spiral. */
  val RingMax = 8

  /** Pure: each point's offset from the spot (px, y down) for `n` points `separation` px apart: on a ring (the first a
    * little past twelve o'clock) or an outward spiral, never on the spot itself.
    */
  def offsets(n: com.histmersion.web.map.Spiderfy.Count, separation: com.histmersion.web.map.Spiderfy.Separation): Vector[(com.histmersion.web.map.Spiderfy.OffsetCoordinate, com.histmersion.web.map.Spiderfy.OffsetCoordinate)] =
    offsetsOf(n.value, separation.value).map((x, y) => (OffsetCoordinate(x), OffsetCoordinate(y)))

  private def offsetsOf(n: Int, separation: Double): Vector[(Double, Double)] =
    if n <= 0 then Vector.empty
    else if n == 1 then Vector((0.0, -separation))
    else if n <= RingMax then
      // the ring's circumference holds n markers with room between them (and at least a marker's width)
      val radius = math.max(separation, separation * n / (2 * math.Pi) * 1.15)
      val step = 2 * math.Pi / n
      val start = -math.Pi / 2 + math.Pi / 6
      Vector.tabulate(n)(i => (radius * math.cos(start + i * step), radius * math.sin(start + i * step)))
    else
      // Archimedes' spiral: each point `separation` along the curve from the last, the arms `separation` apart
      val b = separation / (2 * math.Pi)
      // `var`: walking out along the spiral
      var angle = 2 * math.Pi
      Vector.tabulate(n) { _ =>
        val r = b * angle
        val at = (r * math.cos(angle - math.Pi / 2), r * math.sin(angle - math.Pi / 2))
        angle += separation / r
        at
      }

  /** The legs: an SVG `side` px square, centred on the spot, a line to each offset. */
  def legsSvg(offsets: Seq[(com.histmersion.web.map.Spiderfy.OffsetCoordinate, com.histmersion.web.map.Spiderfy.OffsetCoordinate)], side: com.histmersion.web.map.Spiderfy.Side): com.histmersion.web.map.Spiderfy.Svg =
    val px = side.value
    val c = px / 2.0
    val lines = offsets.map((x, y) => s"""<line x1="$c" y1="$c" x2="${c + x.value}" y2="${c + y.value}"/>""").mkString
    Svg(s"""<svg width="$px" height="$px" viewBox="0 0 $px $px" aria-hidden="true">$lines</svg>""")
