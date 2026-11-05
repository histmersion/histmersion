package com.histmersion.web

import com.histmersion.api.{Api, Plural}
import com.raquo.laminar.api.L.*
import com.histmersion.web.map.TheMap

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.media.{MediaGeoHit, MediaThumb}
import com.histmersion.api.catalogue.Place

/** The Places and Pictures tabs of the results: "On the map", what the map's sites and pictures layers hold in its view
  * (with what they only count there); "Everywhere", the places named by the filter's words, or every picture of the
  * span.
  */
object MapLists:

  /** Pure: the count line — `12 places on the map`, `40 listed of 312 (zoom in to list the rest)`. */
  def countLine(listed: Int, total: Int, one: String, many: String, onMap: Boolean): String =
    val where = if onMap then " on the map" else ""
    if total <= listed then s"${Plural.count(total, one, many)}$where"
    else
      s"${Plural.count(total, one, many)}$where · $listed listed${if onMap then ", zoom in to list the rest" else ""}"

  /** Pure: a site's row note — its type, its dates, or that it has none. */
  def siteNote(p: SitePoint): String =
    List(
      p.known.fold("site")(_.label),
      if p.undated then "date unknown" else p.period.fold("")((a, b) => Fmt.periodStr(Some(a.toLong), Some(b.toLong)))
    )
      .filter(_.nonEmpty)
      .mkString(" · ")

  private def siteRow(p: SitePoint): Option[MapListRow] =
    p.id.map(id =>
      MapListRow(
        id,
        p.name,
        siteNote(p),
        Glyphs.svg(CardIcons.place(p.kind, p.known.map(_.id))),
        Route.Place(id).path,
        () => ResultsPanel.show(Preview.Target.Place(id, MapView.spanMid())),
        p.undated
      )
    )

  private def placeRow(h: Place.Hit): MapListRow =
    MapListRow(
      h.id,
      h.label,
      List(Fmt.display(h.kind), Fmt.periodStr(h.periodStart, h.periodEnd)).filter(_.nonEmpty).mkString(" · "),
      Glyphs.svg(CardIcons.place(h.kind, None)),
      Route.Place(h.id).path,
      () => ResultsPanel.show(Preview.Target.Place(h.id, MapView.spanMid()))
    )

  private def pictureRow(h: MediaGeoHit): MapListRow =
    MapListRow(
      h.uid,
      h.assetName,
      List(Fmt.display(h.kind.getOrElse("picture")), Fmt.periodStr(h.periodStart, h.periodEnd))
        .filter(_.nonEmpty)
        .mkString(" · "),
      Glyphs.svg(CardIcons.Media),
      MediaThumb.filePath(h.uid),
      () => PointKinds.Media.open(PointKinds.Media.key(h), h, org.scalajs.dom.document.body)
    )

  // ── the state, following the map and the filter ───────────────────────────────────────────────────

  val places: Var[MapListState] = Var(MapListState(Nil, 0, ""))
  val pictures: Var[MapListState] = Var(MapListState(Nil, 0, ""))
  private val placesLatest = Latest()
  private val picturesLatest = Latest()

  // `var`: what the lists were last asked for everywhere (asked again only when it changed)
  private var askedEverywhere: Option[String] = None

  /** The lists follow the map's layers as they read their tiles, the view, and the filter. */
  def follow(config: UiConfig): Unit =
    given Owner = unsafeWindowOwner
    MapView.sitesLayer.changed.signal.foreach(_ => if UiState.inView.now() then refresh(config))
    MapView.mediaLayer.changed.signal.foreach(_ => if UiState.inView.now() then refresh(config))
    UiState.signal.foreach(_ => refresh(config))
    if TheMap.isUp then
      val _ = TheMap.surface.on(com.histmersion.web.map.MapEventKind.MoveEnd)(_ =>
        if UiState.inView.now() then refresh(config)
      )

  /** Read both lists again: from the map's layers, or (everywhere) from the server when what is asked changed. */
  def refresh(config: UiConfig): Unit =
    val onMap = UiState.inView.now()
    if onMap then
      askedEverywhere = None
      val (ss, sCounted) = MapView.sitesLayer.inViewNow()
      val sRows = ss.toList.flatMap(siteRow).sortBy(_.label.toLowerCase)
      places.set(
        MapListState(
          sRows,
          sRows.size + sCounted,
          if UiState.layers.now()(MapLayer.Sites) then
            countLine(sRows.size, sRows.size + sCounted, "place", "places", onMap = true)
          else "Turn on Sites under Layers to list the places on the map."
        )
      )
      val (ms, mCounted) = MapView.mediaLayer.inViewNow()
      val mRows = ms.toList.map(pictureRow).distinctBy(_.key).sortBy(_.label.toLowerCase)
      pictures.set(
        MapListState(
          mRows,
          mRows.size + mCounted,
          if UiState.layers.now()(MapLayer.Media) then
            countLine(mRows.size, mRows.size + mCounted, "picture", "pictures", onMap = true)
          else "Turn on Media under Layers to list the pictures on the map."
        )
      )
    else
      val f0 = UiState.toFilter()
      val key = s"${UiState.q.now()}|${f0.period}|${UiState.mediaProvider.now()}|${UiState.mediaLicense.now()}"
      if !askedEverywhere.contains(key) then
        askedEverywhere = Some(key)
        everywhere(config)

  private def everywhere(config: UiConfig): Unit =
    UiState.q.now().map(_.trim).filter(_.nonEmpty) match
      case Some(words) =>
        placesLatest(ApiClient.places(Some(words), MapView.spanMid(), Some(config.pageSize)))(
          hs =>
            places.set(
              MapListState(
                hs.map(placeRow),
                hs.size,
                s"${countLine(hs.size, hs.size, "place", "places", onMap = false)} named like “$words”"
              )
            ),
          m => places.set(MapListState(Nil, 0, s"Could not list places: $m"))
        )
      case None => places.set(MapListState(Nil, 0, "Type words in the filters to find places by name everywhere."))
    val f = UiState.toFilter()
    picturesLatest(
      ApiClient.mediaGeo(
        Api.MediaGeoQuery(
          from = f.period.map(_.start),
          to = f.period.map(_.end),
          limit = Some(config.pageSize),
          provider = UiState.mediaProvider.now(),
          license = UiState.mediaLicense.now()
        )
      )
    )(
      hs => {
        val rows = hs.map(pictureRow).distinctBy(_.key);
        pictures.set(
          MapListState(rows, rows.size, countLine(rows.size, rows.size, "picture", "pictures", onMap = false))
        )
      },
      m => pictures.set(MapListState(Nil, 0, s"Could not list pictures: $m"))
    )

  /** One tab's list. */
  def pane(state: Var[MapListState], name: String): HtmlElement =
    div(
      cls := s"map-list map-list-$name",
      div(cls := "count-bar", span(cls := "count", child.text <-- state.signal.map(_.note))),
      ul(cls := "list", role := "list", children <-- state.signal.map(_.rows.map(row)))
    )

  private def row(r: MapListRow): HtmlElement =
    li(
      cls := "hit map-row",
      cls("is-undated") := r.undated,
      a(
        cls := "row-link",
        href := r.href,
        span(cls := "cite-tip-icon pick-icon", onMountCallback(ctx => ctx.thisNode.ref.innerHTML = r.icon)),
        span(cls := "row-title", r.label),
        onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); r.open() } }
      ),
      Option.when(r.note.nonEmpty)(div(cls := "meta", r.note)),
      marks.RowActions(marks.ItemRef(com.histmersion.api.personal.Target.item(r.kind, r.key), r.label), r.href, r.note)
    )
