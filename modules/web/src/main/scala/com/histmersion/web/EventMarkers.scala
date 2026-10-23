package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** The pure half of the map's EVENT markers (#1449). */
object EventMarker:

  /** Pure: the marker's markup ([[PointKinds.Events]]): its kind's icon on a square whose fill
    * is the event's date on the period ramp (`--c`), as every point's. The TIER is the badge's style:
    * `hd-ev--attested` filled; `hd-ev--catalogue` paler and dashed — a research lead, not evidence
    * (docs/visualization-guide.md §1). */
  def html(e: EventGeo, color: String): String = PointMarker.html(PointKinds.Events, e, color)

  /** Pure: an event in words — its name, kind and years, size, place and tier (its accessible name). */
  def describe(e: EventGeo): String =
    List(Some(e.label), Some(EventPlayback.note(e)), e.dating, EventPrecision.label(e.precision), e.size, e.placeLabel,
      Some(if e.tier == EventTier.Catalogue then "catalogue only" else "attested")).flatten.mkString(" · ")

  /** Pure: the tier's class name — anything but `catalogue` is drawn attested. */
  def tierClass(tier: String): String = if tier == EventTier.Catalogue then "catalogue" else "attested"

  /** Pure: where an event sits on the period ramp of the span shown (0 = its start, 1 = its end): the
    * middle of its outer span. */
  def rampAt(e: EventGeo, span: (Long, Long)): Double =
    val (lo, hi) = (span._1.min(span._2).toDouble, span._1.max(span._2).toDouble)
    EventGeo.outer(e.timeSpan).fold(0.5) { (a, b) =>
      if hi == lo then 0.5 else (((a + b) / 2.0 - lo) / (hi - lo)).max(0).min(1)
    }

  /** Pure: the map's view as the `area` an events query takes, wrapped and clamped as the in-view
    * filter does (`minLon,minLat,maxLon,maxLat`). */
  def area(west: Double, south: Double, east: Double, north: Double): String =
    def r(x: Double) = Math.round(x * 1e4) / 1e4
    def wrap(x: Double) = { val y = ((x + 180) % 360 + 360) % 360 - 180; if y == -180 && x > 0 then 180.0 else y }
    val (w, e) = if east - west >= 360 then (-180.0, 180.0) else (wrap(west), wrap(east))
    s"${r(w)},${r(south.max(-90))},${r(e)},${r(north.min(90))}"

/** The EVENTS layer (#1449): every event of the span shown — the corpus's and the catalogues' — in the
  * map's view; the filter bar's kind and tier menus narrow it. One request per change of span, view or
  * filter (`/api/events/geo`, server-side bbox + time + kind); the points are [[PointKinds.Events]]. */
object EventMarkers:

  /** The markers — a [[PointLayer]] of [[PointKinds.Events]], attached and detached by the map with its
    * other overlays ([[MapView]]). */
  val layer: PointLayer[EventGeo] = PointLayer(PointKinds.Events, () => MapView.dots, () => MapView.spanMid())
  // the request a late answer must belong to
  private val latest = Latest()

  /** Load the events of `span` in the view (the layer must be attached). */
  def load(span: (Long, Long), limit: Int): Unit =
    if TheMap.isUp && layer.isAttached then
      val b = TheMap.surface.bounds
      val (lo, hi) = (span._1.min(span._2), span._1.max(span._2))
      val q = Api.EventQuery(UiState.toFilter().copy(period = Some(Period(lo, hi)), limit = limit),
        kind = UiState.eventKind.now(), tier = UiState.eventTier.now(), context = Some(true),
        area = Some(EventMarker.area(b.west, b.south, b.east, b.north)))
      latest.onCurrent(ApiClient.eventsGeo(q))(_.foreach { list =>
        if layer.isAttached then layer.show(list.events, span)
      })(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

/** The map's KEY to its shapes and icons (#1449): what each shape is, the findings' source kinds, and —
  * while their layers are on — the event kinds (natural, human) and site types, each icon with its name.
  * Pure markup. */
object MapKey:
  def eventsHtml: String =
    EventTaxonomy.groups.map { g =>
      val items = EventTaxonomy.kinds.filter(_.group == g).map(k =>
        s"""<li><span class="hd-badge hd-badge--square hd-key-badge">${Glyphs.badge(k.icon)}</span><span>${Fmt.esc(k.label)}</span></li>""").mkString
      s"""<div class="hd-key-group"><div class="hd-key-title">$g events</div><ul class="hd-key-list">$items</ul></div>"""
    }.mkString +
      """<div class="hd-key-group"><ul class="hd-key-list hd-key-tiers">""" +
      """<li><span class="hd-badge hd-ev hd-ev--attested hd-key-swatch"></span><span>attested by a source</span></li>""" +
      """<li><span class="hd-badge hd-ev hd-ev--catalogue hd-key-swatch"></span><span>catalogue only (a lead)</span></li></ul></div>"""

  def sitesHtml: String =
    val items = (SiteTypes.all.map(t => t.icon -> t.label) :+ (CardIcons.Unknown -> "site of unknown type")).map((icon, label) =>
      s"""<li><span class="hd-badge hd-badge--circle hd-key-badge">${Glyphs.badge(icon)}</span><span>${Fmt.esc(label)}</span></li>""").mkString
    s"""<div class="hd-key-group"><div class="hd-key-title">sites</div><ul class="hd-key-list">$items</ul></div>"""

  /** The shape grammar (docs/visualization-guide.md §2): the SHAPE says what kind of thing a marker is,
    * the icon its type, the fill its date, the outline its tier — and the findings' icons, by what their
    * source is. Shown whatever layers are on: the findings always are. */
  def shapesHtml: String =
    def badge(shape: String, inner: String) = s"""<span class="hd-badge hd-badge--$shape hd-key-badge">$inner</span>"""
    // the same icons as the toolbar's layer switches
    val shapes = PointKinds.all.map(k => Icon.html(k.icon) -> k.meaning) ++ List(
      Icon.html(Icon.Region) -> "a region or polity (its boundary)",
      DotCluster.bubbleHtml(5, 20, ("var(--accent)", "var(--accent)"), "hdb-key", "hd-key-cluster", None) ->
        "several nearby (click to list)",
      // the clusters of events keep the event's square (#1647)
      DotCluster.bubbleHtml(5, 20, ("var(--accent)", "var(--accent)"), "hdb-key-ev", "hd-key-cluster", None, BubbleForm.Events) ->
        "several events nearby",
      DotCluster.bubbleHtml(5, 20, ("var(--accent)", "var(--accent)"), "hdb-key-mix", "hd-key-cluster", None, BubbleForm.WithEvents) ->
        "several nearby, events among them")
    val kinds = SourceKinds.all.map(k => badge("diamond", Glyphs.badge(CardIcons.sourceKind(k, marker = true))) ->
      (if k == SourceKinds.Unknown then "source of unknown kind" else k))
    def list(items: List[(String, String)]) =
      items.map((m, label) => s"""<li>$m<span>${Fmt.esc(label)}</span></li>""").mkString
    s"""<div class="hd-key-group"><div class="hd-key-title">shapes</div><ul class="hd-key-list">${list(shapes)}</ul></div>""" +
      s"""<div class="hd-key-group"><div class="hd-key-title">findings, by source</div><ul class="hd-key-list">${list(kinds)}</ul></div>"""

  /** The key's markup: the shapes, then the icons of the layers on. */
  def html(events: Boolean, sites: Boolean): String =
    val body = shapesHtml + (if events then eventsHtml else "") + (if sites then sitesHtml else "")
    s"""<details class="hd-key"><summary>key</summary>$body</details>"""
