package com.histmersion
package web

import com.histmersion.api.Palette
import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventTaxonomy, EventTier}
import com.histmersion.api.media.MediaGeoHit
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.{EventSummary, Period, Place, Year}
import com.histmersion.api.Geo
import com.histmersion.api.UiLabels
import com.histmersion.api.Years
import com.histmersion.api.MapStack
import com.histmersion.api.catalogue.SiteTile
import com.histmersion.api.Api
import com.histmersion.api.media.MediaItem
import com.histmersion.api.Html
import com.histmersion.api.Html.html as markup

/** The four kinds of point (docs/map-layers.md): findings, sites, events, pictures — each a descriptor of
  * [[PointLayer]].
  */
object PointKinds:

  /** A finding's point: a DIAMOND with its source's kind (the count where several coincide); its hover shows the
    * source's card; a click opens the finding's popup (one) or lists them (several).
    */
  object Findings extends PointKind[Spot]:

    val dot = DotKind.Finding
    val shape = Shape.Diamond
    val icon = Icon.PointFinding
    val layerLabel = UiLabels.Text("Sources")
    val meaning = UiLabels.Text("a finding (a source’s claim)")
    def box = (PointKind.BoxPixels(Layout.findingMarkerSize.value), PointKind.BoxPixels(Layout.findingMarkerSize.value))
    def key(s: Spot): PointKind.PointKey = PointKind.PointKey(s"${s.lat.value}|${s.lon.value}")
    def at(s: Spot): (Geo.Latitude, Geo.Longitude) = (Geo.Latitude(s.lat.value), Geo.Longitude(s.lon.value))
    def inner(s: Spot): Html =
      if s.items.size > 1 then markup"""<span class="hd-find-count">${s.items.size}</span>"""
      else
        Glyphs
          .badge(
            CardIcons
              .sourceKind(Source.Kinds.Wire(s.items.head.sourceKind.fold(Source.Kinds.Unknown)(_.value)), marker = true)
          )
    override def classes(s: Spot): String =
      val n = s.items.size
      val span = if n == 1 then
        for a <- s.items.head.periodStart; b <- s.items.head.periodEnd yield Years.Astronomical(b.value - a.value)
      else None
      val ring = MapView.ringOf(DotCluster.Total(n), span).value
      val unstated = Certainty.markerClass(confOf(s))
      "hd-find" + (if n > 1 then " hd-coincident" else "") + (if ring > 0 then s" ring-$ring" else "") +
        (if unstated.value.nonEmpty then s" ${unstated.value}" else "")
    def fill(s: Spot, span: (Years.Astronomical, Years.Astronomical)): String =
      val conf = confOf(s)
      if s.items.size > 1 then MapView.gradientBg(s.items, span, conf).value
      else MapView.colorFor(MapView.avgMid(s.items), span, conf).value

    /** The least certain finding's — a finding that states none is unstated, never high (#1946). */
    private[web] def confOf(s: Spot): Certainty = Certainty.ofAll(s.items.map(_.confidence))
    def member(s: Spot): Member =
      Member(
        DotKind.Finding,
        Member.N(s.items.size),
        s.items.flatMap(MapView.midOf).map(m => Member.Mids(m.value)),
        confOf(s),
        findings = s.items
      )
    def label(s: Spot): UiLabels.Text = s.items match
      case List(one) => UiLabels.Text(MapView.markerLabel(one).value)
      case many      => UiLabels.Text(s"${many.size} findings here")
    def action(s: Spot): UiLabels.Text =
      if s.items.size == 1 then UiLabels.Text("open this finding") else UiLabels.Text("list them")
    override def title(s: Spot): Option[UiLabels.Text] = MapView.markerTitle(s.items).map(t => UiLabels.Text(t.value))
    def target(s: Spot, asOf: Option[Years.Astronomical]): Option[Preview.Target] =
      MapView.hoverSource(s.items).map(l => Preview.Target.Source(Preview.Target.Source.Citekey(l.value)))

    /** One finding: its popup; several: their list. */
    def activate(s: Spot, how: Activation): Unit =
      Preview.unpin(restoreFocus = Preview.RestoreFocus(false))
      s.items match
        case List(one) => MapView.popup(s.lat, s.lon, MapView.popupHtml(one))
        case _ => ResultsList.pick(s.items, UiLabels.Text(s"${s.items.size} findings at ${s.lat}, ${s.lon}"), None)
    def open(key: PointKind.PointKey, s: Spot, row: dom.Element): Unit =
      MapView.reveal(
        PointSet.Key(key.value),
        () => s.items.headOption.foreach(one => MapView.popup(s.lat, s.lon, MapView.popupHtml(one)))
      )

  /** A site: a CIRCLE with its type's icon (the exclamation mark when unknown); its hover shows the place's card; a
    * click chooses it as the place filter and pins its card. The site-type menu keeps one type.
    */
  object Sites extends PointKind[SitePoint]:

    val dot = DotKind.Site
    val shape = Shape.Circle
    val icon = Icon.PointSite
    val layerLabel = UiLabels.Text("sites")
    val meaning = UiLabels.Text("a site")
    def box = (PointKind.BoxPixels(Layout.siteIconSize.value), PointKind.BoxPixels(Layout.siteIconSize.value))
    override def pane =
      MapStack.Pane.Sites // above every region: a site inside one still takes the click
    def key(p: SitePoint): PointKind.PointKey = PointKind.PointKey(p.id.fold(s"${p.lat}|${p.lon}")(_.value))
    def at(p: SitePoint): (Geo.Latitude, Geo.Longitude) = (p.lat, p.lon)
    def inner(p: SitePoint): Html =
      Glyphs.badge(CardIcons.siteMarker(p.known.map(t => Place.Entry.SiteType(t.id.value))))
    override def classes(p: SitePoint): String =
      MapView
        .placeClasses(MapView.ClassName("hd-site-icon hd-site-marker"), p.basis.map(b => Place.TimeBasis.Wire(b.value)))
        .value + (if p.undated.value then s" ${MapView.UndatedClass}"
                  else "")
    // a site of no known date beneath the dated ones
    override def zIndex(p: SitePoint): PointKind.ZIndex = PointKind.ZIndex(if p.undated.value then -100 else 0)
    override def attrs(p: SitePoint): Html = p.known.fold(Html.empty)(t => markup""" data-site-type="${t.id}"""")
    def fill(p: SitePoint, span: (Years.Astronomical, Years.Astronomical)): String =
      MapView
        .viridisAt(
          MapView.Mid(p.mid.fold((span._1.value + span._2.value).toDouble / 2)(_.value)),
          span
        )
        .value
    private def note(p: SitePoint): String =
      List(
        p.known.fold("site of unknown type")(_.label.value),
        if p.undated.value then "date unknown"
        else p.period.fold("")((a, b) => Fmt.periodStr(Some(Year(a.value.toLong)), Some(Year(b.value.toLong))).value)
      )
        .filter(_.nonEmpty)
        .mkString(" · ")
    def member(p: SitePoint): Member =
      Member(
        DotKind.Site,
        Member.N(1),
        p.mid.toList.map(m => Member.Mids(m.value)),
        Certainty.NoSay,
        undated = Member.Undated(if p.undated.value then 1 else 0),
        entry = Some(
          DotEntry(
            DotKind.Site,
            DotEntry.Id(key(p).value),
            DotEntry.Label(p.name.value),
            DotEntry.Icon(
              CardIcons.place(Place.Kind.Wire(p.kind.value), p.known.map(t => Place.Entry.SiteType(t.id.value))).value
            ),
            p.mid.map(m => DotEntry.Mid(m.value)),
            DotEntry.Note(note(p))
          )
        )
      )
    def label(p: SitePoint): UiLabels.Text = UiLabels.Text(s"${p.name.value} · ${p.known.fold("site")(_.label.value)}")
    def action(p: SitePoint): UiLabels.Text = UiLabels.Text("show only this place and pin its card")
    def target(p: SitePoint, asOf: Option[Years.Astronomical]): Option[Preview.Target] =
      p.id.map(i => Preview.Target.Place(Pages.PlaceId(i.value), asOf))
    def activate(p: SitePoint, how: Activation): Unit =
      p.id.foreach(id =>
        PlaceHover.activatePlace(
          PlaceHover.PlaceId(id.value),
          p.name,
          PlaceHover.PlaceKind(p.kind.value),
          () => MapView.spanMid(),
          how
        )
      )
    def open(key: PointKind.PointKey, p: SitePoint, row: dom.Element): Unit =
      p.id.foreach(id => DotCluster.pinUnder(Preview.Target.Place(Pages.PlaceId(id.value), MapView.spanMid()), row))
    override def keep(p: SitePoint): Boolean = keeps(p, UiState.siteType.now().map(SiteTile.SiteType(_)))

    /** Pure: does the site-type menu's choice keep it? "all" keeps every site. */
    def keeps(p: SitePoint, wanted: Option[SiteTile.SiteType]): Boolean =
      wanted.forall(t => p.siteType.exists(_.value == t.value))

  /** An event: a SQUARE (its corners barely rounded) with its kind's icon; the tier is its fill (attested filled,
    * catalogue only paler and dashed); its hover shows the event's card, a click pins it.
    */
  object Events extends PointKind[EventGeo]:

    val dot = DotKind.Event
    val shape = Shape.Square
    val icon = Icon.PointEvent
    val layerLabel = UiLabels.Text("events")
    val meaning = UiLabels.Text("an event")
    def box = (PointKind.BoxPixels(Layout.eventMarkerSize.value), PointKind.BoxPixels(Layout.eventMarkerSize.value))
    override def pane = MapStack.Pane.Events
    def key(e: EventGeo): PointKind.PointKey = PointKind.PointKey(e.id.value)
    def at(e: EventGeo): (Geo.Latitude, Geo.Longitude) = (Geo.Latitude(e.lat.value), Geo.Longitude(e.lon.value))
    def inner(e: EventGeo): Html =
      Glyphs.badge(Glyphs.Name(CardIcons.event(EventSummary.Kind(e.kind.value)).value))
    override def classes(e: EventGeo): String =
      s"hd-ev hd-ev--${EventMarker.tierClass(Api.EventQuery.Tier(e.tier.value))} hd-ev--${EventTaxonomy.of(EventSummary.Kind(e.kind.value)).group}" +
        (if e.precision.value == EventPrecision.Approximate then " hd-ev--approximate" else "") +
        (if undated(e) then s" ${MapView.UndatedClass}" else "")

    /** Pure: has it no known date (its outer span open on both sides)? */
    def undated(e: EventGeo): Boolean = e.timeSpan.outer.isEmpty
    override def attrs(e: EventGeo): Html = markup""" data-id="${e.id}""""
    def fill(e: EventGeo, span: (Years.Astronomical, Years.Astronomical)): String =
      Fmt.color(Fmt.ColorT(EventMarker.rampAt(e, (Year(span._1.value), Year(span._2.value))).value)).value
    private def mid(e: EventGeo): Option[Double] = e.timeSpan.outer.map((a, b) => (a.value + b.value).toDouble / 2)
    def member(e: EventGeo): Member =
      Member(
        DotKind.Event,
        Member.N(1),
        mid(e).toList.map(Member.Mids(_)),
        Certainty.NoSay,
        undated = Member.Undated(if undated(e) then 1 else 0),
        entry = Some(
          DotEntry(
            DotKind.Event,
            DotEntry.Id(e.id.value),
            DotEntry.Label(e.label.value),
            DotEntry.Icon(CardIcons.event(EventSummary.Kind(e.kind.value)).value),
            mid(e).map(DotEntry.Mid(_)),
            DotEntry.Note(EventPlayback.note(e).value)
          )
        )
      )
    def label(e: EventGeo): UiLabels.Text = UiLabels.Text(EventMarker.describe(e).value)
    def action(e: EventGeo): UiLabels.Text = UiLabels.Text("pin its card")
    def target(e: EventGeo, asOf: Option[Years.Astronomical]): Option[Preview.Target] = Some(
      Preview.Target.Event(Preview.Target.Event.Id(e.id.value))
    )

    /** Drawn at a year only when dated well enough — the feed already leaves the imprecise out
      * ([[EventPrecision.mapped]], decided in core); this keeps a stray one off the map too.
      */
    override def keep(e: EventGeo): Boolean = EventPrecision.mapped(EventSummary.Precision(e.precision.value))
    // the corpus's events above the catalogue's
    override def zIndex(e: EventGeo): PointKind.ZIndex =
      PointKind.ZIndex(if undated(e) then -100 else if e.tier.value == EventTier.Catalogue then 0 else 500)
    def activate(e: EventGeo, how: Activation): Unit =
      val el = how.element
      Preview.pin(
        Preview.Target.Event(Preview.Target.Event.Id(e.id.value)),
        () =>
          el.filter(_.isConnected).map { x =>
            val r = x.getBoundingClientRect()
            Preview.Anchor.Under(
              Preview.Anchor.Under.Left(r.left),
              Preview.Anchor.Under.Top(r.top),
              Preview.Anchor.Under.Bottom(r.bottom),
              Preview.Anchor.Under.Width(r.width)
            )
          },
        owner = el.filter(_ => how.keyboard.value),
        keyboard = Preview.KeyboardDriven(how.keyboard.value)
      )
    def open(key: PointKind.PointKey, e: EventGeo, row: dom.Element): Unit =
      DotCluster.pinUnder(Preview.Target.Event(Preview.Target.Event.Id(e.id.value)), row)

  /** A picture: a landscape PHOTO PRINT with the picture icon, in its date's colour (an undated one in its kind's); a
    * source's figure previews its source; a click opens its popup.
    */
  object Media extends PointKind[MediaGeoHit]:
    val dot = DotKind.Media
    val shape = Shape.Photo
    val icon = Icon.PointMedia
    val layerLabel = UiLabels.Text("media")
    val meaning = UiLabels.Text("a picture")
    def box = (PointKind.BoxPixels(Layout.mediaMarkerWidth.value), PointKind.BoxPixels(Layout.mediaMarkerHeight.value))
    def key(h: MediaGeoHit): PointKind.PointKey = PointKind.PointKey(s"${h.uid.value}|${h.lat}|${h.lon}")
    def at(h: MediaGeoHit): (Geo.Latitude, Geo.Longitude) = (Geo.Latitude(h.lat.value), Geo.Longitude(h.lon.value))
    def inner(h: MediaGeoHit): Html = Glyphs.badge(Glyphs.Name(CardIcons.Media))
    override def classes(h: MediaGeoHit): String = "hd-media-dot"
    private def mid(h: MediaGeoHit): Option[Double] =
      DotCluster
        .mid(h.periodStart.map(y => Period.Start(y.value)), h.periodEnd.map(y => Period.End(y.value)))
        .map(_.value)
    def fill(h: MediaGeoHit, span: (Years.Astronomical, Years.Astronomical)): String =
      mid(h).fold(s"var(${Palette.media(h.kind.getOrElse(MediaItem.Kind("scan")))})")(m =>
        MapView.viridisAt(MapView.Mid(m), span).value
      )
    private def note(h: MediaGeoHit): String =
      List(
        Fmt.display(UiLabels.Text(h.kind.fold("media")(_.value))).value,
        Fmt.periodStr(h.periodStart, h.periodEnd).value
      )
        .filter(_.nonEmpty)
        .mkString(" · ")
    def member(h: MediaGeoHit): Member =
      Member(
        DotKind.Media,
        Member.N(1),
        mid(h).toList.map(Member.Mids(_)),
        Certainty.NoSay,
        entry = Some(
          DotEntry(
            DotKind.Media,
            DotEntry.Id(h.uid.value),
            DotEntry.Label(h.assetName.value),
            DotEntry.Icon(CardIcons.Media),
            mid(h).map(DotEntry.Mid(_)),
            DotEntry.Note(note(h))
          )
        )
      )
    def label(h: MediaGeoHit): UiLabels.Text = UiLabels.Text(s"${h.assetName.value} · ${note(h)}")
    def action(h: MediaGeoHit): UiLabels.Text = UiLabels.Text("open this picture")
    def target(h: MediaGeoHit, asOf: Option[Years.Astronomical]): Option[Preview.Target] =
      h.citekey.map(c => Preview.Target.Source(Preview.Target.Source.Citekey(c.value)))
    private def popup(h: MediaGeoHit): Unit =
      val period = Fmt.periodStr(h.periodStart, h.periodEnd)
      val caption = h.caption.fold(Html.empty)(c => markup"<br><i>$c</i>")
      MapView.popup(h.lat, h.lon, MediaPopup.html(h, MediaPopup.PeriodText(period.value), caption))
    def activate(h: MediaGeoHit, how: Activation): Unit = popup(h)
    def open(key: PointKind.PointKey, h: MediaGeoHit, row: dom.Element): Unit =
      MapView.reveal(PointSet.Key(key.value), () => popup(h))

  /** Every kind, in the order the toolbar, the key and a bubble's list name them. */
  val all: List[PointKind[?]] = List(Findings, Sites, Events, Media)
