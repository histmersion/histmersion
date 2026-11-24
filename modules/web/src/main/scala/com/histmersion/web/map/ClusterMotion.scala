package com.histmersion
package web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import com.histmersion.lint.{LintRule, tolerated}

/** How the cluster layer moves on a zoom that regroups it (#3764: the raster map's cluster plugin animated it, the
  * MapLibre release drew the new groups in one step). A split slides each new marker out from where its bubble stood; a
  * merge slides the markers that join a bubble into it while the bubble fades in — about a quarter of a second, the
  * stylesheet's `.hd-cluster-move`/`.hd-cluster-arrive`. When the map does not animate ([[MotionPref]]: the reader's
  * "Map motion", by default the system's reduced-motion setting) nothing moves: the new groups are drawn at once.
  */
object ClusterMotion:
  /** How long a move takes (the stylesheet's animation), ms. */
  val Ms = 250

  /** A marker made for a zoom's moves before they start: drawn hidden until then. */
  val Pending = "hd-cluster-pending"

  /** Pure: where a marker made of points last drawn at `places` comes from — the one place they all stood at (a split,
    * or the same group), or none when they came from several (a merge) or nowhere.
    */
  def origin(places: Seq[(ScreenPt.X, ScreenPt.Y)]): Option[(ScreenPt.X, ScreenPt.Y)] =
    places.distinct match
      case Seq(one) => Some(one)
      case _        => None

  /** Pure: does a marker of the points `ks` merge into the marker of `target` (a group that holds more than it did)? A
    * split's old bubble does not: its points come out of it instead.
    */
  def merges(ks: Seq[Clustering.GroupKey], target: Seq[Clustering.GroupKey]): Boolean =
    target.size > ks.size && ks.forall(target.contains)

  /** A marker's move from one screen point to another. */
  final case class Slide(marker: DomMarker, from: (ScreenPt.X, ScreenPt.Y), to: (ScreenPt.X, ScreenPt.Y))

  /** Pure: does a zoom from `drawnBefore` markers to `drawnAfter` animate — at most `max` on either side? What an
    * animation costs is the markers drawn and moved on screen, not the points the layer holds: a wide window's view
    * holds thousands of events, of which a few hundred are drawn.
    */
  def within(drawnBefore: Int, drawnAfter: Int, max: Int): Boolean =
    drawnBefore <= max && drawnAfter <= max

  /** Move every marker of `slides` from its `from` to its `to` over [[Ms]]: the stylesheet animates the offset between
    * them (`--move-x`/`--move-y`) on top of the marker's place — a marker made at its new place comes out of the old
    * one, a ghost (still at its old place) flies to the new one. Nothing is read back from the page: a transition needs
    * each marker's start laid out before its move, and reading that layout between one marker's writes and the next's
    * lays the whole page out once per marker.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def slideAll(slides: List[Slide]): Unit =
    slides.foreach { sl =>
      val ghost = sl.marker.el.classList.contains("hd-cluster-ghost")
      val (dx, dy) =
        if ghost then (sl.to._1.value - sl.from._1.value, sl.to._2.value - sl.from._2.value)
        else (sl.from._1.value - sl.to._1.value, sl.from._2.value - sl.to._2.value)
      sl.marker.el.style.setProperty("--move-x", s"${dx}px")
      sl.marker.el.style.setProperty("--move-y", s"${dy}px")
      sl.marker.el.classList.add("hd-cluster-move")
    }
    if slides.nonEmpty then
      dom.window.setTimeout(() => slides.foreach(_.marker.el.classList.remove("hd-cluster-move")), Ms + 70)

  /** `m` appears as what merges into it arrives. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def arrive(m: DomMarker): Unit =
    m.el.classList.add("hd-cluster-arrive")
    dom.window.setTimeout(() => m.el.classList.remove("hd-cluster-arrive"), Ms + 70)
