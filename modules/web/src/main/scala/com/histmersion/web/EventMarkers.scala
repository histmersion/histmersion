package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.scalajs.js

/** The pure half of the map's EVENT markers (#1449). */
object EventMarker:

  /** Pure: the marker's markup. Its kind's icon ([[Glyphs]]) inside a badge whose colour is the event's
    * date on the period ramp (`--c`), as a finding's dot — the kind reads before a hover, the date by
    * the same channel as everything else. The TIER is the badge's style: `hd-ev--attested` filled, the
    * icon light on it; `hd-ev--catalogue` outlined and lighter, the icon in the colour — a research lead,
    * not evidence (docs/visualization-guide.md §1). `data-event` gives it its card on hover ([[Preview]]). */
  def html(e: EventGeo, color: String): String =
    val k = EventTaxonomy.of(e.kind)
    val title = List(Some(e.label), Some(EventPlayback.note(e)), e.size, e.placeLabel,
      Some(if e.tier == EventTier.Catalogue then "catalogue only" else "attested")).flatten.mkString(" · ")
    s"""<span class="hd-badge hd-ev hd-ev--${tierClass(e.tier)} hd-ev--${k.group}" data-event="${Fmt.esc(e.id)}" """ +
      s"""style="--c:$color" title="${Fmt.esc(title)}" aria-label="${Fmt.esc(title)}">${Glyphs.badge(k.icon)}</span>"""

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
  * map's view, as a marker with its kind's icon; the filter bar's kind and tier menus narrow it. One
  * request per change of span, view or filter (`/api/events/geo`, server-side bbox + time + kind), the
  * markers synced in place; a hover shows the card the server already assembled ([[Preview]]). */
object EventMarkers:

  /** The markers — attached and detached by the map with its other overlays ([[MapView]]); they are
    * dots of the map's one cluster group, with the findings, sites and pictures. */
  val layer = FadingLayer(Some(() => MapView.dots))
  // `var`: the request a late answer must belong to
  private var reqId = 0

  /** Load the events of `span` in the view (the layer must be attached). */
  def load(span: (Long, Long), limit: Int): Unit =
    val map = MapView.leafletMap
    if map != null && layer.isAttached then
      reqId += 1
      val mine = reqId
      val b = map.getBounds()
      val (lo, hi) = (span._1.min(span._2), span._1.max(span._2))
      val q = Api.EventQuery(UiState.toFilter().copy(period = Some(Period(lo, hi)), limit = limit),
        kind = UiState.eventKind.now(), tier = UiState.eventTier.now(), context = Some(true),
        area = Some(EventMarker.area(b.getWest(), b.getSouth(), b.getEast(), b.getNorth())))
      ApiClient.eventsGeo(q).foreach { list =>
        if mine == reqId && layer.isAttached then
          layer.sync[EventGeo](list.events,
            key = e => e.id,
            create = e => marker(e, span),
            update = (lyr, e) => setColor(lyr, e, span))
      }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  private def colorOf(e: EventGeo, span: (Long, Long)): String = Fmt.color(EventMarker.rampAt(e, span))

  private def divIcon(e: EventGeo, color: String): DivIcon =
    val size = ui.Layout.eventMarkerSize.value
    L.divIcon(js.Dynamic.literal(className = "hd-ev-wrap", iconSize = js.Array(size, size),
      iconAnchor = js.Array(size / 2, size / 2), html = EventMarker.html(e, color)).asInstanceOf[js.Object])

  private def marker(e: EventGeo, span: (Long, Long)): Layer =
    val m = L.marker(js.Array(e.lat, e.lon), js.Dynamic.literal(
      icon = divIcon(e, colorOf(e, span)),
      pane = MapPanes.Events,
      keyboard = false,
      // the corpus's events above the catalogue's
      zIndexOffset = if e.tier == EventTier.Catalogue then 0 else 500
    ).asInstanceOf[js.Object])
    // a click pins its card (its sources, leads and actions can then be followed)
    m.on("click", { (ev: LeafletEvent) =>
      DomEvent.stopPropagation(ev)
      val el = m.asInstanceOf[js.Dynamic].getElement().asInstanceOf[dom.Element]
      Preview.pin(Preview.Target.Event(e.id), () => Option(el).filter(_.isConnected).map { x =>
        val r = x.getBoundingClientRect()
        Preview.Anchor.Under(r.left, r.top, r.bottom, r.width)
      })
    })
    // in a bubble's list, its row pins its card
    DotCluster.tag(m, DotEntry(DotKind.Event, e.id, e.label, CardIcons.event(e.kind),
      EventGeo.outer(e.timeSpan).map((a, b) => (a + b).toDouble / 2), EventPlayback.note(e)),
      row => DotCluster.pinUnder(Preview.Target.Event(e.id), row))
    m

  /** The colour moves with the span; the rest of the marker stays. The icon it is drawn from next time
    * changes too: an event inside a bubble has no element now, and must not come out in the old colour. */
  private def setColor(lyr: Layer, e: EventGeo, span: (Long, Long)): Unit =
    lyr.asInstanceOf[js.Dynamic].options.icon = divIcon(e, colorOf(e, span))
    val el = lyr.asInstanceOf[js.Dynamic].getElement()
    if !js.isUndefined(el) && el != null then
      Option(el.asInstanceOf[dom.Element].querySelector(".hd-ev")).foreach(
        _.asInstanceOf[dom.html.Element].style.setProperty("--c", colorOf(e, span)))

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
    val shapes = List(
      badge("diamond", Glyphs.badge("newspaper")) -> "finding (a source's claim)",
      badge("circle", Glyphs.badge("castle")) -> "site",
      badge("square", Glyphs.badge("swords")) -> "event",
      badge("photo", Glyphs.badge(CardIcons.Media)) -> "picture",
      """<span class="hd-dot hd-cluster hd-key-cluster">5</span>""" -> "several nearby (click to list)")
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
