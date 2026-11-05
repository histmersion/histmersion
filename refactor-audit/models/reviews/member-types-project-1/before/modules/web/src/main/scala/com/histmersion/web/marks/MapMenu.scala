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

  val locked: Var[Boolean] = Var(false)

  def setLocked(on: Boolean, surface: MapSurface): Unit =
    locked.set(on)
    surface.setLocked(on)
    Flash.say(if on then "The map is locked: it does not move until you unlock it." else "The map moves again.")

  /** A point on the map, as a target. Pure. */
  def point(at: LatLon, zoom: Double, span: Option[(Long, Long)]): Target =
    Target(
      ItemKind.Point,
      lat = Latitude.option(at.lat),
      lon = Longitude.option(wrap(at.lon)),
      zoom = MapZoom.option(math.round(zoom * 10) / 10.0),
      from = span.map(s => Year(s._1.toInt)),
      to = span.map(s => Year(s._2.toInt))
    )

  /** A longitude on the world's first copy (the map draws copies side by side). Pure. */
  def wrap(lon: Double): Double = ((lon + 180) % 360 + 360) % 360 - 180

  private def spanNow: Option[(Long, Long)] = UiState.from.now().zip(UiState.to.now())

  private def writing(act: => Unit): Unit = if Auth.writableNow then act else WindowManager.open(WindowKey.ApiKey)

  def entries(surface: MapSurface, at: LatLon): List[ContextMenu.Entry] =
    val here = point(at, surface.zoom, spanNow)
    List(
      ContextMenu.Entry(
        "Bookmark this place",
        () =>
          writing(
            MyItems
              .add(here.modify(_.from).setTo(None).modify(_.to).setTo(None), None, "Place on the map")
              .foreach(_ => Flash.say("Bookmarked the place: it is in My library › Bookmarks."))
          )
      ),
      ContextMenu.Entry("Add a note here", () => NoteEditor.write(here, "Place on the map")),
      ContextMenu.Separator,
      ContextMenu.Entry(
        if locked.now() then "Unlock the map" else "Lock the map",
        () => setLocked(!locked.now(), surface),
        pressed = Some(locked.now())
      ),
      ContextMenu.Entry(
        "Start the map here",
        () => writing(startHere(surface, at)),
        hint = Some("Where the map opens, for everyone who opens Histmersion from this server")
      ),
      ContextMenu.Entry(
        "Bookmark this view of the map",
        () => SavedViews.save(SavedViews.suggestedName(UiState.q.now(), UiState.from.now(), UiState.to.now()))
      )
    )

  /** `ui.map-start` in the settings file: always open at this point, at this zoom. */
  def startHere(surface: MapSurface, at: LatLon): Unit =
    val c = MapStartConfig(
      MapStartMode.Place.wire,
      at.lat,
      wrap(at.lon),
      math.round(surface.zoom * 10) / 10.0,
      "the view I chose"
    )
    SettingsStore
      .save(StartPlace.changes(c).map((k, v) => ConfigEdit.Change(k, Some(v))))
      .onComplete(r =>
        Flash.say(
          r.fold(e => s"The start was not saved: ${SettingsStore.message(e)}", _ => "The map opens here from now on.")
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
          val at = surface.unproject(ScreenPt(e.clientX - r.left, e.clientY - r.top))
          ContextMenu.show("Map actions", entries(surface, at), e.clientX, e.clientY, Some(box))
      }
    )
    box.addEventListener(
      "keydown",
      { (e: dom.KeyboardEvent) =>
        val t = e.target.asInstanceOf[dom.Element]
        if ContextMenu.isMenuKey(e.key, e.shiftKey) && t.closest(".hd-marker") == null && !surface.isChrome(t) then
          e.preventDefault()
          val r = box.getBoundingClientRect()
          ContextMenu.show(
            "Map actions",
            entries(surface, surface.center),
            r.left + r.width / 2,
            r.top + r.height / 2,
            Option(e.target).collect { case h: dom.HTMLElement => h }
          )
      }
    )
    val ctrl = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    ctrl.className = "map-actions-ctrl"
    surface.addControl(ctrl, Corner.TopLeft)
    val _ = render(ctrl, ContextMenu.button("Map actions", () => entries(surface, surface.center), "map-actions-btn"))
    val _ = render(
      box,
      div(
        cls := "map-lock-note",
        cls("is-hidden") <-- locked.signal.map(!_),
        role := "status",
        Icon(Icon.Lock),
        span("The map is locked"),
        button(typ := "button", cls := "btn btn--sm map-unlock", "Unlock", onClick --> (_ => setLocked(false, surface)))
      )
    )
