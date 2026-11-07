package com.histmersion.web.marks

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.instance.{MapStartConfig, MapStartMode}
import com.histmersion.api.personal.{MapZoom, Target}
import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.web.*
import com.histmersion.web.map.{Corner, LatLon, MapSurface, ScreenPt}
import com.histmersion.web.settings.{SettingsStore, StartPlace}
import com.histmersion.web.ui.{ContextMenu, Flash}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The map's own actions, at a point (a right click there) or at its centre (its "⋯" button, or the menu key while the
  * map has the focus): bookmark the place, leave a note there, lock the map, start the map here, bookmark this view of
  * the map. A locked map takes no drag, wheel, key or pinch until it is unlocked, and says so over it.
  */
object MapMenu:

  val locked: Var[MapSurface.Locked] = Var(MapSurface.Locked(false))

  def setLocked(on: MapSurface.Locked, surface: MapSurface): Unit =
    locked.set(on)
    surface.setLocked(on)
    Flash.say(if on.value then com.histmersion.web.ui.Flash.Text("The map is locked: it does not move until you unlock it.") else com.histmersion.web.ui.Flash.Text("The map moves again."))

  /** A point on the map, as a target. Pure. */
  def point(at: LatLon, zoom: MapSurface.Zoom, span: Option[(Year, Year)]): Target =
    Target(
      ItemKind.Point,
      lat = Latitude.option(at.lat.value),
      lon = Longitude.option(wrap(at.lon).value),
      zoom = MapZoom.option(math.round(zoom.value * 10) / 10.0),
      from = span.map(_._1),
      to = span.map(_._2)
    )

  /** A longitude on the world's first copy (the map draws copies side by side). Pure. */
  def wrap(lon: LatLon.Lon): LatLon.Lon = LatLon.Lon(((lon.value + 180) % 360 + 360) % 360 - 180)

  private def spanNow: Option[(Year, Year)] =
    UiState.from.now().zip(UiState.to.now()).map((a, b) => (Year(a), Year(b)))

  private def writing(act: => Unit): Unit = if Auth.writableNow then act else WindowManager.open(WindowKey.ApiKey)

  def entries(surface: MapSurface, at: LatLon): List[ContextMenu.Entry] =
    val here = point(at, surface.zoom, spanNow)
    List(
      ContextMenu.Entry(
        com.histmersion.web.ui.ContextMenu.Entry.Label("Bookmark this place"),
        () =>
          writing(
            MyItems
              .add(here.modify(_.from).setTo(None).modify(_.to).setTo(None), None, com.histmersion.api.library.Title("Place on the map"))
              .foreach(_ => Flash.say(Flash.Text("Bookmarked the place: it is in My library › Bookmarks.")))
          )
      ),
      ContextMenu.Entry(com.histmersion.web.ui.ContextMenu.Entry.Label("Add a note here"), () => NoteEditor.write(here, com.histmersion.api.library.Title("Place on the map"))),
      ContextMenu.Separator,
      ContextMenu.Entry(
        if locked.now().value then com.histmersion.web.ui.ContextMenu.Entry.Label("Unlock the map") else com.histmersion.web.ui.ContextMenu.Entry.Label("Lock the map"),
        () => setLocked(MapSurface.Locked(!locked.now().value), surface),
        pressed = Some(ContextMenu.Entry.Pressed(locked.now().value))
      ),
      ContextMenu.Entry(
        com.histmersion.web.ui.ContextMenu.Entry.Label("Start the map here"),
        () => writing(startHere(surface, at)),
        hint = Some(com.histmersion.web.ui.ContextMenu.Entry.Hint("Where the map opens, for everyone who opens Histmersion from this server"))
      ),
      ContextMenu.Entry(
        com.histmersion.web.ui.ContextMenu.Entry.Label("Bookmark this view of the map"),
        () => SavedViews.save(SavedViews.suggestedName(UiState.q.now(), UiState.from.now().map(Year(_)), UiState.to.now().map(Year(_))))
      )
    )

  /** `ui.map-start` in the settings file: always open at this point, at this zoom. */
  def startHere(surface: MapSurface, at: LatLon): Unit =
    val c = MapStartConfig(
      MapStartMode.Place.wire,
      Latitude(at.lat.value),
      Longitude(wrap(at.lon).value),
      MapStartConfig.Zoom(math.round(surface.zoom.value * 10) / 10.0),
      com.histmersion.api.instance.MapStartConfig.Label("the view I chose")
    )
    SettingsStore
      .save(StartPlace.changes(c).map((k, v) => ConfigEdit.Change(ConfigEdit.Change.Key(k.value), Some(ConfigEdit.Change.Value(v.value)))))
      .onComplete(r =>
        Flash.say(
          r.fold(e => Flash.Text(s"The start was not saved: ${SettingsStore.message(e)}"), _ => com.histmersion.web.ui.Flash.Text("The map opens here from now on."))
        )
      )

  /** The right click, the menu key and the "⋯" button on `surface`; the lock's notice over it. */
  def install(surface: MapSurface): Unit =
    val box = surface.container
    box.addEventListener(
      "contextmenu",
      { (e: dom.MouseEvent) =>
        val t = e.target.asInstanceOf[dom.Element]
        if t.closest(".hd-marker, .map-lock-note") == null && !surface.isChrome(t) then
          e.preventDefault()
          val r = box.getBoundingClientRect()
          val at = surface.unproject(ScreenPt(ScreenPt.X(e.clientX - r.left), ScreenPt.Y(e.clientY - r.top)))
          ContextMenu.show(com.histmersion.web.ui.ContextMenu.ShowTitle("Map actions"), entries(surface, at), ContextMenu.ShowX(e.clientX), ContextMenu.ShowY(e.clientY), Some(box))
      }
    )
    box.addEventListener(
      "keydown",
      { (e: dom.KeyboardEvent) =>
        val t = e.target.asInstanceOf[dom.Element]
        if ContextMenu.isMenuKey(ContextMenu.KeyName(e.key), e.shiftKey) && t.closest(".hd-marker") == null && !surface.isChrome(t) then
          e.preventDefault()
          val r = box.getBoundingClientRect()
          ContextMenu.show(
            com.histmersion.web.ui.ContextMenu.ShowTitle("Map actions"),
            entries(surface, surface.center),
            ContextMenu.ShowX(r.left + r.width / 2),
            ContextMenu.ShowY(r.top + r.height / 2),
            Option(e.target).collect { case h: dom.HTMLElement => h }
          )
      }
    )
    val ctrl = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    ctrl.className = "map-actions-ctrl"
    surface.addControl(ctrl, Corner.TopLeft)
    val _ = render(ctrl, ContextMenu.button(com.histmersion.web.ui.ContextMenu.ButtonTitle("Map actions"), () => entries(surface, surface.center), com.histmersion.web.ui.ContextMenu.ButtonExtraCls("map-actions-btn")))
    val _ = render(
      box,
      div(
        cls := "map-lock-note",
        cls("is-hidden") <-- locked.signal.map(l => !l.value),
        role := "status",
        Icon(Icon.Lock),
        span("The map is locked"),
        button(typ := "button", cls := "btn btn--sm map-unlock", "Unlock", onClick --> (_ => setLocked(MapSurface.Locked(false), surface)))
      )
    )
