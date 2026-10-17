package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js

/** Findings at one point: one marker, counting them all (a point holds several claims). */
final case class Spot(lat: Double, lon: Double, items: List[GeoHit])

object Spot:
  /** Pure: findings grouped by their exact point, in the order they came. */
  def of(points: Iterable[GeoHit]): List[Spot] =
    val spots = scala.collection.mutable.LinkedHashMap.empty[(Double, Double), scala.collection.mutable.ListBuffer[GeoHit]]
    for pt <- points; lat <- pt.lat; lon <- pt.lon do
      spots.getOrElseUpdate((lat, lon), scala.collection.mutable.ListBuffer.empty) += pt
    spots.toList.map { case ((lat, lon), items) => Spot(lat, lon, items.toList) }

/** A site from the places feed (`/api/places/geo?kind=site`). */
final case class SitePoint(id: Option[String], label: String, kind: String, siteType: Option[String],
    basis: Option[String], lat: Double, lon: Double, period: Option[(Double, Double)]):
  def name: String = if label.nonEmpty then label else id.getOrElse("site")
  def mid: Option[Double] = period.map((a, b) => (a + b) / 2)
  /** Its type, when it is one the map knows. */
  def known: Option[SiteTypes.SiteType] = siteType.flatMap(SiteTypes.get)

object SitePoint:
  private def str(v: js.Dynamic): Option[String] = if js.isUndefined(v) || v == null then None else Some(v.toString)

  /** A GeoJSON point feature of the places feed. */
  def of(f: js.Dynamic): SitePoint =
    val p = f.properties
    val c = f.geometry.coordinates
    val ps = p.selectDynamic("periodStart")
    val pe = p.selectDynamic("periodEnd")
    val period = if js.isUndefined(ps) || js.isUndefined(pe) || ps == null || pe == null then None
      else Some((ps.asInstanceOf[Double], pe.asInstanceOf[Double]))
    SitePoint(str(p.id), str(p.label).getOrElse(""), str(p.kind).getOrElse(PlaceKind.Site), str(p.siteType), str(p.basis),
      c.selectDynamic("1").asInstanceOf[Double], c.selectDynamic("0").asInstanceOf[Double], period)

/** The four kinds of point (docs/map-layers.md): findings, sites, events, pictures — each a descriptor
  * of [[PointLayer]]. */
object PointKinds:

  /** A finding's point: a DIAMOND with its source's kind (the count where several coincide); its hover
    * shows the source's card; a click opens the finding's popup (one) or lists them (several). */
  object Findings extends PointKind[Spot]:
    val dot   = DotKind.Finding
    val shape = Shape.Diamond
    val icon  = Icon.PointFinding
    val layerLabel = "sources"
    val meaning = "a finding (a source’s claim)"
    def box   = (Layout.findingMarkerSize.value, Layout.findingMarkerSize.value)
    def key(s: Spot): String = s"${s.lat}|${s.lon}"
    def at(s: Spot): (Double, Double) = (s.lat, s.lon)
    def inner(s: Spot): String =
      if s.items.size > 1 then s"""<span class="hd-find-count">${s.items.size}</span>"""
      else Glyphs.badge(CardIcons.sourceKind(s.items.head.sourceKind.getOrElse(SourceKinds.Unknown), marker = true))
    override def classes(s: Spot): String =
      val n = s.items.size
      val span = if n == 1 then for a <- s.items.head.periodStart; b <- s.items.head.periodEnd yield b - a else None
      val ring = MapView.ringOf(n, span)
      val unstated = Certainty.markerClass(confOf(s))
      "hd-find" + (if n > 1 then " hd-coincident" else "") + (if ring > 0 then s" ring-$ring" else "") +
        (if unstated.nonEmpty then s" $unstated" else "")
    def fill(s: Spot, span: (Long, Long)): String =
      val conf = confOf(s)
      if s.items.size > 1 then MapView.gradientBg(s.items, span, conf) else MapView.colorFor(MapView.avgMid(s.items), span, conf)
    /** The least certain finding's — a finding that states none is unstated, never high (#1946). */
    private[web] def confOf(s: Spot): Certainty = Certainty.ofAll(s.items.map(_.confidence))
    def member(s: Spot): Member = Member(DotKind.Finding, s.items.size, s.items.flatMap(MapView.midOf), confOf(s), findings = s.items)
    def label(s: Spot): String = s.items match
      case List(one) => MapView.markerLabel(one)
      case many      => s"${many.size} findings here"
    def action(s: Spot): String = if s.items.size == 1 then "open this finding" else "list them"
    override def title(s: Spot): Option[String] = MapView.markerTitle(s.items)
    def target(s: Spot, asOf: Option[Long]): Option[Preview.Target] = MapView.hoverSource(s.items).map(Preview.Target.Source(_))
    /** One finding: its popup, following its content; several: none (a click lists them). */
    override def decorate(m: Marker, s: Spot): Unit =
      val d = m.asInstanceOf[js.Dynamic]
      s.items match
        case List(one) =>
          if js.isUndefined(d.getPopup()) || d.getPopup() == null then m.bindPopup(MapView.popupHtml(one))
          else d.setPopupContent(MapView.popupHtml(one))
        case _ => if !js.isUndefined(d.getPopup()) && d.getPopup() != null then d.unbindPopup()
    def activate(m: Marker, s: Spot, how: Activation): Unit =
      Preview.unpin(restoreFocus = false)
      if s.items.size == 1 then { if how.keyboard then m.openPopup() }   // a click opens it by itself
      else ResultsList.pick(s.items, s"${s.items.size} findings at ${s.lat}, ${s.lon}", None)
    def open(m: Marker, s: Spot, row: dom.Element): Unit = MapView.reveal(m, () => { val _ = m.openPopup() })

  /** A site: a CIRCLE with its type's icon (the exclamation mark when unknown); its hover shows the
    * place's card; a click chooses it as the place filter and pins its card. The site-type menu keeps
    * one type. */
  object Sites extends PointKind[SitePoint]:
    val dot   = DotKind.Site
    val shape = Shape.Circle
    val icon  = Icon.PointSite
    val layerLabel = "sites"
    val meaning = "a site"
    def box   = (Layout.siteIconSize.value, Layout.siteIconSize.value)
    override def pane = Some(MapPanes.Sites)   // above every region: a site inside one still takes the click
    def key(p: SitePoint): String = p.id.getOrElse(s"${p.lat}|${p.lon}")
    def at(p: SitePoint): (Double, Double) = (p.lat, p.lon)
    def inner(p: SitePoint): String = Glyphs.badge(CardIcons.siteMarker(p.known.map(_.id)))
    override def classes(p: SitePoint): String = MapView.placeClasses("hd-site-icon hd-site-marker", p.basis)
    override def attrs(p: SitePoint): String = p.known.fold("")(t => s""" data-site-type="${t.id}"""")
    def fill(p: SitePoint, span: (Long, Long)): String =
      MapView.viridisAt(p.mid.getOrElse((span._1 + span._2).toDouble / 2), span)
    private def note(p: SitePoint): String =
      List(p.known.fold("site of unknown type")(_.label), p.period.fold("")((a, b) => Fmt.periodStr(Some(a.toLong), Some(b.toLong))))
        .filter(_.nonEmpty).mkString(" · ")
    def member(p: SitePoint): Member =
      Member(DotKind.Site, 1, p.mid.toList, Certainty.NoSay,
        entry = Some(DotEntry(DotKind.Site, key(p), p.name, CardIcons.place(p.kind, p.known.map(_.id)), p.mid, note(p))))
    def label(p: SitePoint): String = s"${p.name} · ${p.known.fold("site")(_.label)}"
    def action(p: SitePoint): String = "show only this place and pin its card"
    def target(p: SitePoint, asOf: Option[Long]): Option[Preview.Target] = p.id.map(Preview.Target.Place(_, asOf))
    def activate(m: Marker, p: SitePoint, how: Activation): Unit =
      p.id.foreach(id => PlaceHover.activatePlace(id, p.name, p.kind, () => MapView.spanMid(), how))
    def open(m: Marker, p: SitePoint, row: dom.Element): Unit =
      p.id.foreach(id => DotCluster.pinUnder(Preview.Target.Place(id, MapView.spanMid()), row))
    override def keep(p: SitePoint): Boolean = keeps(p, UiState.siteType.now())
    /** Pure: does the site-type menu's choice keep it? "all" keeps every site. */
    def keeps(p: SitePoint, wanted: Option[String]): Boolean = wanted.forall(t => p.siteType.contains(t))

  /** An event: a SQUARE (its corners barely rounded) with its kind's icon; the tier is its fill (attested
    * filled, catalogue only paler and dashed); its hover shows the event's card, a click pins it. */
  object Events extends PointKind[EventGeo]:
    val dot   = DotKind.Event
    val shape = Shape.Square
    val icon  = Icon.PointEvent
    val layerLabel = "events"
    val meaning = "an event"
    def box   = (Layout.eventMarkerSize.value, Layout.eventMarkerSize.value)
    override def pane = Some(MapPanes.Events)
    def key(e: EventGeo): String = e.id
    def at(e: EventGeo): (Double, Double) = (e.lat, e.lon)
    def inner(e: EventGeo): String = Glyphs.badge(CardIcons.event(e.kind))
    override def classes(e: EventGeo): String =
      s"hd-ev hd-ev--${EventMarker.tierClass(e.tier)} hd-ev--${EventTaxonomy.of(e.kind).group}" +
        (if e.precision == EventPrecision.Approximate then " hd-ev--approximate" else "")
    override def attrs(e: EventGeo): String = s""" data-id="${Fmt.esc(e.id)}""""
    def fill(e: EventGeo, span: (Long, Long)): String = Fmt.color(EventMarker.rampAt(e, span))
    private def mid(e: EventGeo): Option[Double] = EventGeo.outer(e.timeSpan).map((a, b) => (a + b).toDouble / 2)
    def member(e: EventGeo): Member =
      Member(DotKind.Event, 1, mid(e).toList, Certainty.NoSay,
        entry = Some(DotEntry(DotKind.Event, e.id, e.label, CardIcons.event(e.kind), mid(e), EventPlayback.note(e))))
    def label(e: EventGeo): String = EventMarker.describe(e)
    def action(e: EventGeo): String = "pin its card"
    def target(e: EventGeo, asOf: Option[Long]): Option[Preview.Target] = Some(Preview.Target.Event(e.id))
    /** Drawn at a year only when dated well enough — the feed already leaves the imprecise out
      * ([[EventPrecision.mapped]], decided in core); this keeps a stray one off the map too. */
    override def keep(e: EventGeo): Boolean = EventPrecision.mapped(e.precision)
    // the corpus's events above the catalogue's
    override def zIndex(e: EventGeo): Int = if e.tier == EventTier.Catalogue then 0 else 500
    def activate(m: Marker, e: EventGeo, how: Activation): Unit =
      val el = how.element
      Preview.pin(Preview.Target.Event(e.id), () => el.filter(_.isConnected).map { x =>
        val r = x.getBoundingClientRect()
        Preview.Anchor.Under(r.left, r.top, r.bottom, r.width)
      }, owner = el.filter(_ => how.keyboard), keyboard = how.keyboard)
    def open(m: Marker, e: EventGeo, row: dom.Element): Unit = DotCluster.pinUnder(Preview.Target.Event(e.id), row)

  /** A picture: a landscape PHOTO PRINT with the picture icon, in its date's colour (an undated one in
    * its kind's); a source's figure previews its source; a click opens its popup. */
  object Media extends PointKind[MediaGeoHit]:
    val dot   = DotKind.Media
    val shape = Shape.Photo
    val icon  = Icon.PointMedia
    val layerLabel = "media"
    val meaning = "a picture"
    def box   = (Layout.mediaMarkerWidth.value, Layout.mediaMarkerHeight.value)
    def key(h: MediaGeoHit): String = s"${h.uid}|${h.lat}|${h.lon}"
    def at(h: MediaGeoHit): (Double, Double) = (h.lat, h.lon)
    def inner(h: MediaGeoHit): String = Glyphs.badge(CardIcons.Media)
    override def classes(h: MediaGeoHit): String = "hd-media-dot"
    private def mid(h: MediaGeoHit): Option[Double] = DotCluster.mid(h.periodStart, h.periodEnd)
    def fill(h: MediaGeoHit, span: (Long, Long)): String =
      mid(h).fold(s"var(${Palette.media(h.kind.getOrElse("scan"))})")(m => MapView.viridisAt(m, span))
    private def note(h: MediaGeoHit): String =
      List(Fmt.display(h.kind.getOrElse("media")), Fmt.periodStr(h.periodStart, h.periodEnd)).filter(_.nonEmpty).mkString(" · ")
    def member(h: MediaGeoHit): Member =
      Member(DotKind.Media, 1, mid(h).toList, Certainty.NoSay,
        entry = Some(DotEntry(DotKind.Media, h.uid, h.assetName, CardIcons.Media, mid(h), note(h))))
    def label(h: MediaGeoHit): String = s"${h.assetName} · ${note(h)}"
    def action(h: MediaGeoHit): String = "open this picture"
    def target(h: MediaGeoHit, asOf: Option[Long]): Option[Preview.Target] = h.citekey.map(Preview.Target.Source(_))
    override def decorate(m: Marker, h: MediaGeoHit): Unit =
      val d = m.asInstanceOf[js.Dynamic]
      if js.isUndefined(d.getPopup()) || d.getPopup() == null then
        val period = Fmt.periodStr(h.periodStart, h.periodEnd)
        val caption = h.caption.map(c => s"<br><i>${Fmt.esc(c)}</i>").getOrElse("")
        m.bindPopup(MediaPopup.html(h, period, caption))
    def activate(m: Marker, h: MediaGeoHit, how: Activation): Unit = if how.keyboard then m.openPopup()
    def open(m: Marker, h: MediaGeoHit, row: dom.Element): Unit = MapView.reveal(m, () => { val _ = m.openPopup() })

  /** Every kind, in the order the toolbar, the key and a bubble's list name them. */
  val all: List[PointKind[?]] = List(Findings, Sites, Events, Media)
