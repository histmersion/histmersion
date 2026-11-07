package com.histmersion.web

import com.histmersion.api.Plural
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.library.GeoHit

/** The pure half of the mixed clusters: what a bubble holds, says and looks like. */
object DotCluster:
  type BubbleHtml = BubbleHtml.T
  object BubbleHtml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByKeyboard = ByKeyboard.T
  object ByKeyboard extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndLabel = EndLabel.T
  object EndLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtraClass = ExtraClass.T
  object ExtraClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindCount = KindCount.T
  object KindCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mid = Mid.T
  object Mid extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RingClass = RingClass.T
  object RingClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RingExtraClass = RingExtraClass.T
  object RingExtraClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RingOutset = RingOutset.T
  object RingOutset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpokenText = SpokenText.T
  object SpokenText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartLabel = StartLabel.T
  object StartLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A bubble's content: what its markers hold. */

  type Ring = RingValue
  type RingValue = RingValue.T
  object RingValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SquareCorner = SquareCornerValue
  type SquareCornerValue = SquareCornerValue.T
  object SquareCornerValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tab = TabValue
  type TabValue = TabValue.T
  object TabValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Contents(members: List[Member]):
    def total: com.histmersion.web.DotCluster.Total = members.map(_.n).sum
    def counts: Map[DotKind, com.histmersion.web.DotCluster.KindCount] = members.groupMapReduce(_.kind)(_.n)(_ + _)
    def findings: List[GeoHit] = members.flatMap(_.findings)
    def entries: List[DotEntry] = members.flatMap(_.entry)

    /** The middle of every dated member's span: the bubble's colour is their range. */
    def mids: List[com.histmersion.web.DotCluster.Mid] = members.flatMap(_.mids)

    /** The least certain member's certainty (only findings carry one other than [[Certainty.NoSay]]). */
    def certainty: Certainty = Certainty.worst(members.map(_.certainty))

    /** Some of it is only counted (a tile's cells): it cannot all be listed. */
    def counted: Boolean = members.exists(_.aggregate)

    /** The boxes of its cells. */
    def cells: List[com.histmersion.web.map.GeoBounds] = members.flatMap(_.cell)

    /** How many of what it holds have no known date. */
    def undated: com.histmersion.web.DotCluster.Undated = members.map(_.undated).sum


  /** Pure: what a bubble holds, in words — "12 findings · 3 sites · 2 events", the kinds in a fixed order, those it has
    * none of left out.
    */
  def summary(counts: Map[DotKind, com.histmersion.web.DotCluster.KindCount]): com.histmersion.web.DotCluster.Summary =
    DotKind.values.toList
      .flatMap(k => counts.get(k).filter(_ > 0).map(n => Plural.count(n, k.one, k.many)))
      .mkString(" · ")

  /** Pure: the same, then how many of them have no known date — "12 sites, 5 with no known date". */
  def summary(counts: Map[DotKind, com.histmersion.web.DotCluster.KindCount], undated: com.histmersion.web.DotCluster.Undated): com.histmersion.web.DotCluster.Summary =
    val all = summary(counts)
    if undated <= 0 then all else s"$all, $undated with no known date"

  def summary(c: Contents): com.histmersion.web.DotCluster.Summary = summary(c.counts, c.undated)

  /** Is it a bubble of more than one kind? */
  def mixed(c: Contents): Boolean = c.counts.count(_._2 > 0) > 1

  /** How far a bubble's rings reach outside its disc, px: a dark line, the white coin ring, a dark line. */
  val Ring: com.histmersion.web.DotCluster.Ring = com.histmersion.web.DotCluster.Ring(5)

  /** Pure: a bubble's outline (#1647) — square when it holds events only, tabbed when events are among other kinds,
    * round otherwise.
    */
  def form(c: Contents): BubbleForm =
    val events = c.counts.getOrElse(DotKind.Event, 0)
    if events == 0 then BubbleForm.Round
    else if events == c.total then BubbleForm.Events
    else BubbleForm.WithEvents

  /** The corner radius of an events bubble's square, px (each ring's rounds by its distance out). */
  val SquareCorner: com.histmersion.web.DotCluster.SquareCorner = 4.0

  /** The side of the square tab of a bubble with events among other kinds, px. */
  val Tab: com.histmersion.web.DotCluster.Tab = com.histmersion.web.DotCluster.Tab(8)

  /** Pure: a bubble's markup — its count on a disc of diameter `d` in its colour (`ends`: the colours of the oldest and
    * the newest of its dates, the same when they agree), ringed by a dark line, a white coin ring and a dark line, and
    * (`says`) what it holds, to the pointer and to assistive tech. The disc and the rings are ONE inline SVG, every
    * circle about the same centre: drawn as box-shadows round a CSS background they were rasterised apart — the
    * background snapped to the device pixel grid, the shadows not — so wherever a marker landed off that grid (a
    * fractional device-pixel ratio or page zoom, a map under a toolbar of fractional height) the disc sat up to a pixel
    * off-centre in its rings. `id` names the disc's gradient; it must be unique on the page. `form` is its outline
    * ([[BubbleForm]]): an events bubble's disc and rings are rounded squares about the same centre, and one with events
    * among other kinds carries a small square tab.
    */
  def bubbleHtml(
      n: com.histmersion.web.DotCluster.Count,
      d: com.histmersion.web.DotCluster.Size,
      ends: (com.histmersion.web.DotCluster.StartLabel, com.histmersion.web.DotCluster.EndLabel),
      id: com.histmersion.web.DotCluster.Id,
      extraClass: com.histmersion.web.DotCluster.ExtraClass,
      says: Option[com.histmersion.web.DotCluster.SpokenText],
      form: BubbleForm = BubbleForm.Round
  ): com.histmersion.web.DotCluster.BubbleHtml =
    val label = if n > 1 then n.toString else ""
    val words = says.fold("")(w => s""" role="img" title="${Fmt.esc(w)}" aria-label="${Fmt.esc(w)}"""")
    val s = d + 2 * Ring
    val c = s / 2.0
    val r = d / 2.0
    // the disc and its rings, each `out` px beyond the disc's edge: circles, or (events only) rounded
    // squares whose corners round by the same distance out — concentric either way
    def ring(cls: com.histmersion.web.DotCluster.RingClass, out: com.histmersion.web.DotCluster.RingOutset, extra: com.histmersion.web.DotCluster.RingExtraClass = com.histmersion.web.DotCluster.RingExtraClass("")) = form match
      case BubbleForm.Events =>
        val h = r + out
        s"""<rect class="$cls" x="${c - h}" y="${c - h}" width="${2 * h}" height="${2 * h}" rx="${SquareCorner + out}"$extra/>"""
      case _ => s"""<circle class="$cls" cx="$c" cy="$c" r="${r + out}"$extra/>"""
    // events among other kinds: a small square tab in the upper right corner, clear of the round rings
    val tab =
      if form == BubbleForm.WithEvents then
        s"""<rect class="hd-bubble-tab" x="${s - Tab - 0.5}" y="0.5" width="$Tab" height="$Tab" rx="1.5"/>"""
      else ""
    val svg =
      s"""<svg class="hd-bubble" viewBox="0 0 $s $s" aria-hidden="true" focusable="false">""" +
        s"""<defs><linearGradient id="$id" x1="0" y1="0" x2="1" y2="1">""" +
        """<stop offset="0" class="hd-bubble-g0"/><stop offset="1" class="hd-bubble-g1"/></linearGradient></defs>""" +
        ring(com.histmersion.web.DotCluster.RingClass("hd-bubble-fill"), com.histmersion.web.DotCluster.RingOutset(0), s""" fill="url(#$id)"""") +
        ring("hd-bubble-line", com.histmersion.web.DotCluster.RingOutset(0).5) + ring("hd-bubble-coin", 2.5) + ring("hd-bubble-line", 4.5) + tab +
        "</svg>"
    val classes = List("hd-dot hd-cluster", form.cls, extraClass).filter(_.nonEmpty).mkString(" ")
    s"""<span class="$classes" style="--d:${d}px;--fs:${math.min(12, d / 2)}px;--ring:${Ring}px;""" +
      s"""--g0:${ends._1};--g1:${ends._2}"$words>$svg<span class="hd-dot-count">$label</span></span>"""

  /** Pure: the middle of a dated span, for the ramp. */
  def mid(start: Option[com.histmersion.api.catalogue.Period.Start], end: Option[com.histmersion.api.catalogue.Period.End]): Option[com.histmersion.web.DotCluster.Mid] =
    for a <- start; b <- end yield (a + b).toDouble / 2

  // ── the members, as the map's cluster layer holds them ──────────────────────────────────────

  /** What the points of a bubble hold. */
  def contents(dots: Seq[Dot]): Contents = Contents(dots.toList.map(_.member))

  /** The rows of a bubble's list that are not findings, each with how it opens. */
  def others(dots: Seq[Dot]): List[(DotEntry, dom.Element => Unit)] =
    dots.toList.flatMap(d => d.member.entry.map(e => e -> ((row: dom.Element) => d.layer.open(d, row))))

  /** Pure: the order of a bubble's list — by kind, then by name. */
  def ordered(others: List[(DotEntry, dom.Element => Unit)]): List[(DotEntry, dom.Element => Unit)] =
    others.sortBy((e, _) => (e.kind.ordinal, e.label.toLowerCase, e.id))

  /** A card pinned under a list row (`row`): the row is its owner, so the click that pinned it does not count as a
    * click outside it.
    */
  def pinUnder(t: Preview.Target, row: dom.Element, keyboard: com.histmersion.web.DotCluster.ByKeyboard = com.histmersion.web.DotCluster.ByKeyboard(false)): Unit =
    Preview.pin(
      t,
      () =>
        Option.when(row.isConnected) {
          val r = row.getBoundingClientRect()
          Preview.Anchor.Under(r.left, r.top, r.bottom, r.width)
        },
      owner = Some(row),
      keyboard = keyboard
    )
