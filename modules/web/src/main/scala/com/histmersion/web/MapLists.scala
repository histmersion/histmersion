package com.histmersion
package web

import com.histmersion.api.{Api, Paging, Plural}
import com.raquo.laminar.api.L.*
import com.histmersion.web.map.TheMap

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.media.{MediaGeoHit, MediaThumb}
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.UiLabels
import com.histmersion.api.exchange.Bundle
import com.histmersion.web.map.MapEventKind
import com.histmersion.web.marks.RowActions
import com.histmersion.web.marks.ItemRef
import com.histmersion.api.personal.Target
import com.histmersion.api.personal.ItemKey
import com.histmersion.api.library.Title

/** The Places and Pictures tabs of the results: "On the map", what the map's sites and pictures layers hold in its view
  * (with what they only count there); "Everywhere", the places named by the filter's words, or every picture of the
  * span.
  */
object MapLists:
  type Asked = Asked.T
  object Asked extends Newtype[String, Pure]
  type CountLine = CountLine.T
  object CountLine extends Newtype[String, Pure]
  type ListedCount = ListedCount.T
  object ListedCount extends Newtype[Int, Pure]
  type OnMap = OnMap.T
  object OnMap extends Newtype[Boolean, Pure]
  type SiteNote = SiteNote.T
  object SiteNote extends Newtype[String, Pure]
  type TotalCount = TotalCount.T
  object TotalCount extends Newtype[Int, Pure]

  /** Pure: the count line — `12 places on the map`, `40 listed of 312 (zoom in to list the rest)`. */
  type PaneName = PaneName.T
  object PaneName extends Newtype[String, Pure]

  def countLine(
      listed: ListedCount,
      total: TotalCount,
      one: Plural.Noun.Singular,
      many: Plural.Noun.Plural,
      onMap: OnMap
  ): CountLine =
    val where = if onMap.value then " on the map" else ""
    val count = Plural.count(Plural.CountN(total.value.toLong), one, many)
    CountLine(
      if total.value <= listed.value then s"$count$where"
      else s"$count$where · $listed listed${if onMap.value then ", zoom in to list the rest" else ""}"
    )

  /** Pure: a site's row note — its type, its dates, or that it has none. */
  def siteNote(p: SitePoint): SiteNote =
    SiteNote(
      List(
        p.known.fold("site")(_.label.value),
        if p.undated.value then "date unknown"
        else
          p.period.fold("")((a, b) =>
            Fmt
              .periodStr(
                Some(Year(a.value.toLong)),
                Some(Year(b.value.toLong))
              )
              .value
          )
      )
        .filter(_.nonEmpty)
        .mkString(" · ")
    )

  private def siteRow(p: SitePoint): Option[MapListRow] =
    p.id.map(id =>
      MapListRow(
        MapListRow.Key(id.value),
        MapListRow.Label(p.name.value),
        MapListRow.Note(siteNote(p).value),
        Glyphs
          .svg(CardIcons.place(Place.Kind.Wire(p.kind.value), p.known.map(t => Place.Entry.SiteType(t.id.value)))),
        MapListRow.Href(Route.Place(Route.Place.Id(id.value)).path),
        () => ResultsPanel.show(Preview.Target.Place(Preview.Target.Place.Id(id.value), MapView.spanMid())),
        MapListRow.Undated(p.undated.value)
      )
    )

  private def placeRow(h: Place.Hit): MapListRow =
    MapListRow(
      MapListRow.Key(h.id.value),
      MapListRow.Label(h.label.value),
      MapListRow.Note(
        List(
          Fmt.display(UiLabels.Text(h.kind.value)).value,
          Fmt.periodStr(h.periodStart, h.periodEnd).value
        ).filter(_.nonEmpty).mkString(" · ")
      ),
      Glyphs.svg(CardIcons.place(Place.Kind.Wire(h.kind.value), None)),
      MapListRow.Href(Route.Place(Route.Place.Id(h.id.value)).path),
      () => ResultsPanel.show(Preview.Target.Place(Preview.Target.Place.Id(h.id.value), MapView.spanMid()))
    )

  private def pictureRow(h: MediaGeoHit): MapListRow =
    MapListRow(
      MapListRow.Key(h.uid.value),
      MapListRow.Label(h.assetName.value),
      MapListRow.Note(
        List(
          Fmt.display(UiLabels.Text(h.kind.fold("picture")(_.value))).value,
          Fmt.periodStr(h.periodStart, h.periodEnd).value
        )
          .filter(_.nonEmpty)
          .mkString(" · ")
      ),
      Glyphs.svg(Glyphs.Name(CardIcons.Media)),
      MapListRow.Href(MediaThumb.filePath(Bundle.Asset.Uid(h.uid.value)).value),
      () => PointKinds.Media.open(PointKinds.Media.key(h), h, org.scalajs.dom.document.body)
    )

  // ── the state, following the map and the filter ───────────────────────────────────────────────────

  val places: Var[MapListState] = Var(MapListState(Nil, MapListState.Total(0), MapListState.Note("")))
  val pictures: Var[MapListState] = Var(MapListState(Nil, MapListState.Total(0), MapListState.Note("")))
  private val placesLatest = Latest()
  private val picturesLatest = Latest()

  // `var`: what the lists were last asked for everywhere (asked again only when it changed)
  private var askedEverywhere: Option[Asked] = None

  /** The lists follow the map's layers as they read their tiles, the view, and the filter. */
  def follow(config: UiConfig): Unit =
    given Owner = unsafeWindowOwner
    MapView.sitesLayer.changed.signal.foreach(_ => if UiState.inView.now() then refresh(config))
    MapView.mediaLayer.changed.signal.foreach(_ => if UiState.inView.now() then refresh(config))
    UiState.signal.foreach(_ => refresh(config))
    TheMap.whenReady { surface =>
      val _ = surface.on(MapEventKind.MoveEnd)(_ => if UiState.inView.now() then refresh(config))
    }

  /** Read both lists again: from the map's layers, or (everywhere) from the server when what is asked changed. */
  def refresh(config: UiConfig): Unit =
    val onMap = UiState.inView.now()
    if onMap then
      askedEverywhere = None
      val (ss, sCounted) = MapView.sitesLayer.inViewNow()
      val sRows = ss.toList.flatMap(siteRow).sortBy(_.label.value.toLowerCase)
      places.set(
        MapListState(
          sRows,
          MapListState.Total(sRows.size + sCounted.value),
          if UiState.layers.now()(MapLayer.Sites) then
            MapListState.Note(
              countLine(
                ListedCount(sRows.size),
                TotalCount(sRows.size + sCounted.value),
                Plural.Noun.Singular("place"),
                Plural.Noun.Plural("places"),
                onMap = OnMap(true)
              ).value
            )
          else MapListState.Note("Turn on Sites under Layers to list the places on the map.")
        )
      )
      val (ms, mCounted) = MapView.mediaLayer.inViewNow()
      val mRows = ms.toList.map(pictureRow).distinctBy(_.key).sortBy(_.label.value.toLowerCase)
      pictures.set(
        MapListState(
          mRows,
          MapListState.Total(mRows.size + mCounted.value),
          if UiState.layers.now()(MapLayer.Media) then
            MapListState.Note(
              countLine(
                ListedCount(mRows.size),
                TotalCount(mRows.size + mCounted.value),
                Plural.Noun.Singular("picture"),
                Plural.Noun.Plural("pictures"),
                onMap = OnMap(true)
              ).value
            )
          else MapListState.Note("Turn on Media under Layers to list the pictures on the map.")
        )
      )
    else
      val f0 = UiState.toFilter()
      val key = Asked(
        s"${UiState.q.now()}|${f0.period}|${UiState.mediaProvider.now()}|${UiState.mediaLicense.now()}"
      )
      if !askedEverywhere.contains(key) then
        askedEverywhere = Some(key)
        everywhere(config)

  private def everywhere(config: UiConfig): Unit =
    UiState.q.now().map(_.trim).filter(_.nonEmpty) match
      case Some(words) =>
        placesLatest(
          ApiClient.places(
            Some(Api.Search.Text(words)),
            MapView.spanMid().map(y => Api.AggregateQuery.AsOf(y.value)),
            Some(Paging.Limit(config.pageSize.value))
          )
        )(
          hs =>
            places.set(
              MapListState(
                hs.map(placeRow),
                MapListState.Total(hs.size),
                MapListState.Note(
                  s"${countLine(ListedCount(hs.size), TotalCount(hs.size), Plural.Noun.Singular("place"), Plural.Noun.Plural("places"), onMap = OnMap(false))} named like “$words”"
                )
              )
            ),
          m => places.set(MapListState(Nil, MapListState.Total(0), MapListState.Note(s"Could not list places: $m")))
        )
      case None =>
        places.set(
          MapListState(
            Nil,
            MapListState.Total(0),
            MapListState.Note("Type words in the filters to find places by name everywhere.")
          )
        )
    val f = UiState.toFilter()
    picturesLatest(
      ApiClient.mediaGeo(
        Api.MediaGeoQuery(
          from = f.period.map(p => Api.MediaGeoQuery.From(p.start.value)),
          to = f.period.map(p => Api.MediaGeoQuery.To(p.end.value)),
          limit = Some(Api.MediaGeoQuery.Limit(config.pageSize.value)),
          provider = UiState.mediaProvider.now().map(Api.MediaGeoQuery.Provider(_)),
          license = UiState.mediaLicense.now().map(Api.MediaGeoQuery.License(_))
        )
      )
    )(
      hs => {
        val rows = hs.map(pictureRow).distinctBy(_.key);
        pictures.set(
          MapListState(
            rows,
            MapListState.Total(rows.size),
            MapListState.Note(
              countLine(
                ListedCount(rows.size),
                TotalCount(rows.size),
                Plural.Noun.Singular("picture"),
                Plural.Noun.Plural("pictures"),
                onMap = OnMap(false)
              ).value
            )
          )
        )
      },
      m => pictures.set(MapListState(Nil, MapListState.Total(0), MapListState.Note(s"Could not list pictures: $m")))
    )

  /** One tab's list. */
  def pane(state: Var[MapListState], name: PaneName): HtmlElement =
    div(
      cls := s"map-list map-list-$name",
      div(cls := "count-bar", span(cls := "count", child.text <-- state.signal.map(_.note.value))),
      ul(cls := "list", role := "list", children <-- state.signal.map(_.rows.map(row)))
    )

  private def row(r: MapListRow): HtmlElement =
    li(
      cls := "hit map-row",
      cls("is-undated") := r.undated.value,
      a(
        cls := "row-link",
        href := r.href.value,
        span(cls := "cite-tip-icon pick-icon", onMountCallback(ctx => HtmlSink.replace(ctx.thisNode.ref, r.icon))),
        span(cls := "row-title", r.label.value),
        onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); r.open() } }
      ),
      Option.when(r.note.value.nonEmpty)(div(cls := "meta", r.note.value)),
      RowActions(
        ItemRef(
          Target.item(r.kind, ItemKey(r.key.value)),
          Title(r.label.value)
        ),
        ItemRef.Href(r.href.value),
        r.note.value
      )
    )
