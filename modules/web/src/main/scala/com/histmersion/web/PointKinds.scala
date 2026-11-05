package com.histmersion.web

import com.histmersion.api.Palette
import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventTaxonomy, EventTier}
import com.histmersion.api.media.MediaGeoHit
import com.histmersion.api.library.Source

/** The four kinds of point (docs/map-layers.md): findings, sites, events, pictures — each a descriptor of
  * [[PointLayer]].
  */
object PointKinds:

  /** A finding's point: a DIAMOND with its source's kind (the count where several coincide); its hover shows the
    * source's card; a click opens the finding's popup (one) or lists them (several).
    */
  object Findings extends PointKind[Spot]:
    type KeyResult = KeyResult.T
    object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart1 = AtResultPart1.T
    object AtResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart2 = AtResultPart2.T
    object AtResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InnerResult = InnerResult.T
    object InnerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillResult = FillResult.T
    object FillResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart1 = FillSpanPart1.T
    object FillSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart2 = FillSpanPart2.T
    object FillSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LabelResult = LabelResult.T
    object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActionResult = ActionResult.T
    object ActionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetAsOf = TargetAsOf.T
    object TargetAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKey = OpenKey.T
    object OpenKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val dot = DotKind.Finding
    val shape = Shape.Diamond
    val icon = Icon.PointFinding
    val layerLabel = "Sources"
    val meaning = "a finding (a source’s claim)"
    def box = (Layout.findingMarkerSize.value, Layout.findingMarkerSize.value)
    def key(s: Spot): com.histmersion.web.PointKinds.Findings.KeyResult = s"${s.lat}|${s.lon}"
    def at(s: Spot): (com.histmersion.web.PointKinds.Findings.AtResultPart1, com.histmersion.web.PointKinds.Findings.AtResultPart2) = (s.lat, s.lon)
    def inner(s: Spot): com.histmersion.web.PointKinds.Findings.InnerResult =
      if s.items.size > 1 then s"""<span class="hd-find-count">${s.items.size}</span>"""
      else Glyphs.badge(CardIcons.sourceKind(s.items.head.sourceKind.getOrElse(Source.Kinds.Unknown), marker = true))
    override def classes(s: Spot): String =
      val n = s.items.size
      val span = if n == 1 then for a <- s.items.head.periodStart; b <- s.items.head.periodEnd yield b - a else None
      val ring = MapView.ringOf(n, span)
      val unstated = Certainty.markerClass(confOf(s))
      "hd-find" + (if n > 1 then " hd-coincident" else "") + (if ring > 0 then s" ring-$ring" else "") +
        (if unstated.nonEmpty then s" $unstated" else "")
    def fill(s: Spot, span: (com.histmersion.web.PointKinds.Findings.FillSpanPart1, com.histmersion.web.PointKinds.Findings.FillSpanPart2)): com.histmersion.web.PointKinds.Findings.FillResult =
      val conf = confOf(s)
      if s.items.size > 1 then MapView.gradientBg(s.items, span, conf)
      else MapView.colorFor(MapView.avgMid(s.items), span, conf)

    /** The least certain finding's — a finding that states none is unstated, never high (#1946). */
    private[web] def confOf(s: Spot): Certainty = Certainty.ofAll(s.items.map(_.confidence))
    def member(s: Spot): Member =
      Member(DotKind.Finding, s.items.size, s.items.flatMap(MapView.midOf), confOf(s), findings = s.items)
    def label(s: Spot): com.histmersion.web.PointKinds.Findings.LabelResult = s.items match
      case List(one) => MapView.markerLabel(one)
      case many      => s"${many.size} findings here"
    def action(s: Spot): com.histmersion.web.PointKinds.Findings.ActionResult = if s.items.size == 1 then "open this finding" else "list them"
    override def title(s: Spot): Option[String] = MapView.markerTitle(s.items)
    def target(s: Spot, asOf: Option[com.histmersion.web.PointKinds.Findings.TargetAsOf]): Option[Preview.Target] =
      MapView.hoverSource(s.items).map(Preview.Target.Source(_))

    /** One finding: its popup; several: their list. */
    def activate(s: Spot, how: Activation): Unit =
      Preview.unpin(restoreFocus = false)
      s.items match
        case List(one) => MapView.popup(s.lat, s.lon, MapView.popupHtml(one))
        case _         => ResultsList.pick(s.items, s"${s.items.size} findings at ${s.lat}, ${s.lon}", None)
    def open(key: com.histmersion.web.PointKinds.Findings.OpenKey, s: Spot, row: dom.Element): Unit =
      MapView.reveal(key, () => s.items.headOption.foreach(one => MapView.popup(s.lat, s.lon, MapView.popupHtml(one))))

  /** A site: a CIRCLE with its type's icon (the exclamation mark when unknown); its hover shows the place's card; a
    * click chooses it as the place filter and pins its card. The site-type menu keeps one type.
    */
  object Sites extends PointKind[SitePoint]:
    type KeyResult = KeyResult.T
    object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart1 = AtResultPart1.T
    object AtResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart2 = AtResultPart2.T
    object AtResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InnerResult = InnerResult.T
    object InnerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillResult = FillResult.T
    object FillResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart1 = FillSpanPart1.T
    object FillSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart2 = FillSpanPart2.T
    object FillSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoteResult = NoteResult.T
    object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LabelResult = LabelResult.T
    object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActionResult = ActionResult.T
    object ActionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetAsOf = TargetAsOf.T
    object TargetAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKey = OpenKey.T
    object OpenKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeepsResult = KeepsResult.T
    object KeepsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeepsWanted = KeepsWanted.T
    object KeepsWanted extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val dot = DotKind.Site
    val shape = Shape.Circle
    val icon = Icon.PointSite
    val layerLabel = "sites"
    val meaning = "a site"
    def box = (Layout.siteIconSize.value, Layout.siteIconSize.value)
    override def pane =
      com.histmersion.api.MapStack.Pane.Sites // above every region: a site inside one still takes the click
    def key(p: SitePoint): com.histmersion.web.PointKinds.Sites.KeyResult = p.id.getOrElse(s"${p.lat}|${p.lon}")
    def at(p: SitePoint): (com.histmersion.web.PointKinds.Sites.AtResultPart1, com.histmersion.web.PointKinds.Sites.AtResultPart2) = (p.lat, p.lon)
    def inner(p: SitePoint): com.histmersion.web.PointKinds.Sites.InnerResult = Glyphs.badge(CardIcons.siteMarker(p.known.map(_.id)))
    override def classes(p: SitePoint): String =
      MapView.placeClasses("hd-site-icon hd-site-marker", p.basis) + (if p.undated then s" ${MapView.UndatedClass}"
                                                                      else "")
    // a site of no known date beneath the dated ones
    override def zIndex(p: SitePoint): Int = if p.undated then -100 else 0
    override def attrs(p: SitePoint): String = p.known.fold("")(t => s""" data-site-type="${t.id}"""")
    def fill(p: SitePoint, span: (com.histmersion.web.PointKinds.Sites.FillSpanPart1, com.histmersion.web.PointKinds.Sites.FillSpanPart2)): com.histmersion.web.PointKinds.Sites.FillResult =
      MapView.viridisAt(p.mid.getOrElse((span._1 + span._2).toDouble / 2), span)
    private def note(p: SitePoint): com.histmersion.web.PointKinds.Sites.NoteResult =
      List(
        p.known.fold("site of unknown type")(_.label),
        if p.undated then "date unknown" else p.period.fold("")((a, b) => Fmt.periodStr(Some(a.toLong), Some(b.toLong)))
      )
        .filter(_.nonEmpty)
        .mkString(" · ")
    def member(p: SitePoint): Member =
      Member(
        DotKind.Site,
        1,
        p.mid.toList,
        Certainty.NoSay,
        undated = if p.undated then 1 else 0,
        entry = Some(DotEntry(DotKind.Site, key(p), p.name, CardIcons.place(p.kind, p.known.map(_.id)), p.mid, note(p)))
      )
    def label(p: SitePoint): com.histmersion.web.PointKinds.Sites.LabelResult = s"${p.name} · ${p.known.fold("site")(_.label)}"
    def action(p: SitePoint): com.histmersion.web.PointKinds.Sites.ActionResult = "show only this place and pin its card"
    def target(p: SitePoint, asOf: Option[com.histmersion.web.PointKinds.Sites.TargetAsOf]): Option[Preview.Target] = p.id.map(Preview.Target.Place(_, asOf))
    def activate(p: SitePoint, how: Activation): Unit =
      p.id.foreach(id => PlaceHover.activatePlace(id, p.name, p.kind, () => MapView.spanMid(), how))
    def open(key: com.histmersion.web.PointKinds.Sites.OpenKey, p: SitePoint, row: dom.Element): Unit =
      p.id.foreach(id => DotCluster.pinUnder(Preview.Target.Place(id, MapView.spanMid()), row))
    override def keep(p: SitePoint): Boolean = keeps(p, UiState.siteType.now())

    /** Pure: does the site-type menu's choice keep it? "all" keeps every site. */
    def keeps(p: SitePoint, wanted: Option[com.histmersion.web.PointKinds.Sites.KeepsWanted]): com.histmersion.web.PointKinds.Sites.KeepsResult = wanted.forall(t => p.siteType.contains(t))

  /** An event: a SQUARE (its corners barely rounded) with its kind's icon; the tier is its fill (attested filled,
    * catalogue only paler and dashed); its hover shows the event's card, a click pins it.
    */
  object Events extends PointKind[EventGeo]:
    type KeyResult = KeyResult.T
    object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart1 = AtResultPart1.T
    object AtResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart2 = AtResultPart2.T
    object AtResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InnerResult = InnerResult.T
    object InnerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UndatedResult = UndatedResult.T
    object UndatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillResult = FillResult.T
    object FillResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart1 = FillSpanPart1.T
    object FillSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart2 = FillSpanPart2.T
    object FillSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MidResult = MidResult.T
    object MidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LabelResult = LabelResult.T
    object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActionResult = ActionResult.T
    object ActionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetAsOf = TargetAsOf.T
    object TargetAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKey = OpenKey.T
    object OpenKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val dot = DotKind.Event
    val shape = Shape.Square
    val icon = Icon.PointEvent
    val layerLabel = "events"
    val meaning = "an event"
    def box = (Layout.eventMarkerSize.value, Layout.eventMarkerSize.value)
    override def pane = com.histmersion.api.MapStack.Pane.Events
    def key(e: EventGeo): com.histmersion.web.PointKinds.Events.KeyResult = e.id.value
    def at(e: EventGeo): (com.histmersion.web.PointKinds.Events.AtResultPart1, com.histmersion.web.PointKinds.Events.AtResultPart2) = (e.lat.value, e.lon.value)
    def inner(e: EventGeo): com.histmersion.web.PointKinds.Events.InnerResult = Glyphs.badge(CardIcons.event(e.kind))
    override def classes(e: EventGeo): String =
      s"hd-ev hd-ev--${EventMarker.tierClass(e.tier)} hd-ev--${EventTaxonomy.of(e.kind).group}" +
        (if e.precision == EventPrecision.Approximate then " hd-ev--approximate" else "") +
        (if undated(e) then s" ${MapView.UndatedClass}" else "")

    /** Pure: has it no known date (its outer span open on both sides)? */
    def undated(e: EventGeo): com.histmersion.web.PointKinds.Events.UndatedResult = e.timeSpan.outer.isEmpty
    override def attrs(e: EventGeo): String = s""" data-id="${Fmt.esc(e.id.value)}""""
    def fill(e: EventGeo, span: (com.histmersion.web.PointKinds.Events.FillSpanPart1, com.histmersion.web.PointKinds.Events.FillSpanPart2)): com.histmersion.web.PointKinds.Events.FillResult = Fmt.color(EventMarker.rampAt(e, span))
    private def mid(e: EventGeo): Option[com.histmersion.web.PointKinds.Events.MidResult] = e.timeSpan.outer.map((a, b) => (a + b).toDouble / 2)
    def member(e: EventGeo): Member =
      Member(
        DotKind.Event,
        1,
        mid(e).toList,
        Certainty.NoSay,
        undated = if undated(e) then 1 else 0,
        entry = Some(DotEntry(DotKind.Event, e.id.value, e.label, CardIcons.event(e.kind), mid(e), EventPlayback.note(e)))
      )
    def label(e: EventGeo): com.histmersion.web.PointKinds.Events.LabelResult = EventMarker.describe(e)
    def action(e: EventGeo): com.histmersion.web.PointKinds.Events.ActionResult = "pin its card"
    def target(e: EventGeo, asOf: Option[com.histmersion.web.PointKinds.Events.TargetAsOf]): Option[Preview.Target] = Some(Preview.Target.Event(e.id.value))

    /** Drawn at a year only when dated well enough — the feed already leaves the imprecise out
      * ([[EventPrecision.mapped]], decided in core); this keeps a stray one off the map too.
      */
    override def keep(e: EventGeo): Boolean = EventPrecision.mapped(e.precision)
    // the corpus's events above the catalogue's
    override def zIndex(e: EventGeo): Int =
      if undated(e) then -100 else if e.tier == EventTier.Catalogue then 0 else 500
    def activate(e: EventGeo, how: Activation): Unit =
      val el = how.element
      Preview.pin(
        Preview.Target.Event(e.id.value),
        () =>
          el.filter(_.isConnected).map { x =>
            val r = x.getBoundingClientRect()
            Preview.Anchor.Under(r.left, r.top, r.bottom, r.width)
          },
        owner = el.filter(_ => how.keyboard),
        keyboard = how.keyboard
      )
    def open(key: com.histmersion.web.PointKinds.Events.OpenKey, e: EventGeo, row: dom.Element): Unit = DotCluster.pinUnder(Preview.Target.Event(e.id.value), row)

  /** A picture: a landscape PHOTO PRINT with the picture icon, in its date's colour (an undated one in its kind's); a
    * source's figure previews its source; a click opens its popup.
    */
  object Media extends PointKind[MediaGeoHit]:
    type KeyResult = KeyResult.T
    object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart1 = AtResultPart1.T
    object AtResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResultPart2 = AtResultPart2.T
    object AtResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InnerResult = InnerResult.T
    object InnerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MidResult = MidResult.T
    object MidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillResult = FillResult.T
    object FillResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart1 = FillSpanPart1.T
    object FillSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillSpanPart2 = FillSpanPart2.T
    object FillSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoteResult = NoteResult.T
    object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LabelResult = LabelResult.T
    object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActionResult = ActionResult.T
    object ActionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetAsOf = TargetAsOf.T
    object TargetAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKey = OpenKey.T
    object OpenKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val dot = DotKind.Media
    val shape = Shape.Photo
    val icon = Icon.PointMedia
    val layerLabel = "media"
    val meaning = "a picture"
    def box = (Layout.mediaMarkerWidth.value, Layout.mediaMarkerHeight.value)
    def key(h: MediaGeoHit): com.histmersion.web.PointKinds.Media.KeyResult = s"${h.uid}|${h.lat}|${h.lon}"
    def at(h: MediaGeoHit): (com.histmersion.web.PointKinds.Media.AtResultPart1, com.histmersion.web.PointKinds.Media.AtResultPart2) = (h.lat, h.lon)
    def inner(h: MediaGeoHit): com.histmersion.web.PointKinds.Media.InnerResult = Glyphs.badge(CardIcons.Media)
    override def classes(h: MediaGeoHit): String = "hd-media-dot"
    private def mid(h: MediaGeoHit): Option[com.histmersion.web.PointKinds.Media.MidResult] = DotCluster.mid(h.periodStart, h.periodEnd)
    def fill(h: MediaGeoHit, span: (com.histmersion.web.PointKinds.Media.FillSpanPart1, com.histmersion.web.PointKinds.Media.FillSpanPart2)): com.histmersion.web.PointKinds.Media.FillResult =
      mid(h).fold(s"var(${Palette.media(h.kind.getOrElse("scan"))})")(m => MapView.viridisAt(m, span))
    private def note(h: MediaGeoHit): com.histmersion.web.PointKinds.Media.NoteResult =
      List(Fmt.display(h.kind.getOrElse("media")), Fmt.periodStr(h.periodStart, h.periodEnd))
        .filter(_.nonEmpty)
        .mkString(" · ")
    def member(h: MediaGeoHit): Member =
      Member(
        DotKind.Media,
        1,
        mid(h).toList,
        Certainty.NoSay,
        entry = Some(DotEntry(DotKind.Media, h.uid, h.assetName, CardIcons.Media, mid(h), note(h)))
      )
    def label(h: MediaGeoHit): com.histmersion.web.PointKinds.Media.LabelResult = s"${h.assetName} · ${note(h)}"
    def action(h: MediaGeoHit): com.histmersion.web.PointKinds.Media.ActionResult = "open this picture"
    def target(h: MediaGeoHit, asOf: Option[com.histmersion.web.PointKinds.Media.TargetAsOf]): Option[Preview.Target] = h.citekey.map(Preview.Target.Source(_))
    private def popup(h: MediaGeoHit): Unit =
      val period = Fmt.periodStr(h.periodStart, h.periodEnd)
      val caption = h.caption.map(c => s"<br><i>${Fmt.esc(c)}</i>").getOrElse("")
      MapView.popup(h.lat, h.lon, MediaPopup.html(h, period, caption))
    def activate(h: MediaGeoHit, how: Activation): Unit = popup(h)
    def open(key: com.histmersion.web.PointKinds.Media.OpenKey, h: MediaGeoHit, row: dom.Element): Unit = MapView.reveal(key, () => popup(h))

  /** Every kind, in the order the toolbar, the key and a bubble's list name them. */
  val all: List[PointKind[?]] = List(Findings, Sites, Events, Media)
