package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** `window.histmersionMap`: the map from the browser's console and the browser regressions
  * (modules/web/test), in the app's own terms — whatever library draws it. Read-mostly: the view can be
  * set; nothing here writes data. Zooms are the raster grid's, as everywhere in the app. */
object MapHandle:

  private def ll(a: js.Array[Double]): LatLon = LatLon(a(0), a(1))

  // `var`: how many times a base map was drawn whole (the regressions wait for the next one)
  private var drawn = 0

  def install(s: MapSurface): Unit =
    s.on(MapEventKind.BaseMapReady)(_ => drawn += 1)
    val h = js.Dynamic.literal(
      setView = ((at: js.Array[Double], z: Double, opts: js.UndefOr[js.Dynamic]) =>
        s.setView(ll(at), z, animate = opts.exists(o => !js.isUndefined(o.animate) && o.animate.asInstanceOf[Boolean]))): js.Function3[js.Array[Double], Double, js.UndefOr[js.Dynamic], Unit],
      setZoom = ((z: Double, opts: js.UndefOr[js.Dynamic]) =>
        s.setZoom(z, animate = !opts.exists(o => !js.isUndefined(o.animate) && !o.animate.asInstanceOf[Boolean]))): js.Function2[Double, js.UndefOr[js.Dynamic], Unit],
      getZoom = (() => s.zoom): js.Function0[Double],
      getMaxZoom = (() => s.maxZoom): js.Function0[Double],
      getCenter = (() => { val c = s.center; js.Dynamic.literal(lat = c.lat, lng = c.lon) }): js.Function0[js.Dynamic],
      getBounds = (() => { val b = s.bounds; js.Dynamic.literal(south = b.south, west = b.west, north = b.north, east = b.east) }): js.Function0[js.Dynamic],
      panBy = ((d: js.Array[Double]) => s.panBy(d(0), d(1))): js.Function1[js.Array[Double], Unit],
      latLngToContainerPoint = ((at: js.Array[Double]) => { val p = s.project(ll(at)); js.Dynamic.literal(x = p.x, y = p.y) })
        : js.Function1[js.Array[Double], js.Dynamic],
      /** The map point at a container point `[x, y]`: `{lat, lng}`. */
      containerPointToLatLng = ((p: js.Array[Double]) => { val ll = s.unproject(ScreenPt(p(0), p(1))); js.Dynamic.literal(lat = ll.lat, lng = ll.lon) })
        : js.Function1[js.Array[Double], js.Dynamic],
      /** A popup at `[lat, lon]` with this text (what a finding's click opens). */
      openPopup = ((at: js.Array[Double], text: String) => { val _ = s.popup(ll(at), s"""<div class="hd-popup-content zz-test-popup">${Fmt.esc(text)}</div>""", "") })
        : js.Function2[js.Array[Double], String, Unit],
      getSize = (() => { val (w, h) = s.size; js.Dynamic.literal(x = w, y = h) }): js.Function0[js.Dynamic],
      closePopup = (() => s.closePopups()): js.Function0[Unit],
      isMoving = (() => s.isMoving): js.Function0[Boolean],
      /** Still, and every tile of the view drawn. */
      isDrawn = (() => s.isDrawn): js.Function0[Boolean],
      /** `fn` once at the next `zoomend` / `moveend`. */
      once = ((event: String, fn: js.Function0[Unit]) =>
        val kind = if event == "zoomend" then MapEventKind.ZoomEnd else MapEventKind.MoveEnd
        // `var`: the subscription, taken off when it fires
        var sub: Subscription = null
        sub = s.on(kind)(_ => { sub.cancel(); fn() })): js.Function2[String, js.Function0[Unit], Unit],
      /** The ids of the shapes a set draws now (`polities`, `event-outlines`, `place-pick`, …). */
      shapes = ((set: String) => s.shapeSet(set).fold(js.Array[String]())(_.ids.toJSArray)): js.Function1[String, js.Array[String]],
      /** How many times a shape set's outlines were sent to the map (a restyle sends none). */
      shapeUploads = ((set: String) => s.shapeSet(set).fold(0)(_.uploads)): js.Function1[String, Int],
      /** A drawn shape's `{colour, alpha}`, `null` when it is not drawn. */
      shapeStyle = ((set: String, id: String) => s.shapeSet(set).flatMap(_.styleOf(id))
        .fold(null: js.Dynamic)((c, a) => js.Dynamic.literal(colour = c, alpha = a))): js.Function2[String, String, js.Dynamic],
      /** The interactive shapes under a map point, topmost first: `set:id`. */
      shapesAt = ((at: js.Array[Double]) => s.shapesAt(s.project(ll(at))).map(h => s"${h.set}:${h.id}").toJSArray)
        : js.Function1[js.Array[Double], js.Array[String]],
      /** The base map's names placed in the view now. */
      names = (() => s.drawnNames.toJSArray): js.Function0[js.Array[String]],
      /** The base map's names placed now: `{layer, text, x, y}` (a line's middle). */
      labels = (() => s.baseMapLabels.map((l, t, p) => js.Dynamic.literal(layer = l, text = t, x = p.x, y = p.y)).toJSArray)
        : js.Function0[js.Array[js.Dynamic]],
      /** The base map's areas drawn at a map point: `layer:kind` (land cover, land use, water…). */
      areasAt = ((at: js.Array[Double]) => s.baseMapAreasAt(s.project(ll(at))).toJSArray): js.Function1[js.Array[Double], js.Array[String]],
      /** A base-map layer's visibility (`visible`, `none`), `null` when the style has no such layer. */
      layerVisibility = ((id: String) => s.baseMapLayer(id).orNull): js.Function1[String, String],
      /** How many of a base-map layer's features (`contours`, …) are drawn in the view now. */
      layerDrawn = ((id: String) => s.baseMapDrawn(id)): js.Function1[String, Int],
      /** How many points the cluster layer holds, and how many zooms animated its bubbles. */
      points = (() => MapView.dots.size): js.Function0[Int],
      clusterAnimations = (() => MapView.dots.animations): js.Function0[Int],
      /** How many times a base map was drawn whole (every tile of the view). */
      baseMapsDrawn = (() => drawn): js.Function0[Int],
      capabilities = (() => { val c = s.capabilities; js.Dynamic.literal(webgl = c.webgl, software = c.software, renderer = c.renderer, webgl2 = c.webgl2) })
        : js.Function0[js.Dynamic],
      container = s.container)
    dom.window.asInstanceOf[js.Dynamic].histmersionMap = h
