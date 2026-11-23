package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.web.map.MapSurface
import com.histmersion.api.MapStack
import com.histmersion.api.Html.html as markup

/** `window.histmersionMap`: the map from the browser's console and the browser regressions (modules/web/test), in the
  * app's own terms — whatever library draws it. Read-mostly: the view can be set; nothing here writes data. Zooms are
  * the raster grid's, as everywhere in the app.
  */
object MapHandle:

  private def ll(a: js.Array[Double]): LatLon = LatLon(LatLon.Lat(a(0)), LatLon.Lon(a(1)))

  // `var`: how many times a base map was drawn whole (the regressions wait for the next one)
  private var drawn = 0

  private var serial = 0

  private val pointPane: MapStack.Pane => Boolean = _.point == MapStack.Pane.Point(true)

  private val paneName: MapStack.Pane => String = _.cssClass.stripPrefix("hd-pane--")

  private val faceSelector = ".hd-ev, .hd-find, .hd-site-marker, .hd-cluster, .hd-point, .hd-note"

  private def within(el: dom.Element): List[dom.Element] =
    val all = el.querySelectorAll("*")
    el :: (0 until all.length).map(all(_)).toList

  private def attr(els: List[dom.Element], name: String): js.Any =
    els.find(_.hasAttribute(name)).map(e => e.getAttribute(name): js.Any).getOrElse(null)

  private def stroke(face: dom.Element, part: String): js.Any =
    Option(face.querySelector(part)).fold(null: js.Any) { e =>
      val s = dom.window.getComputedStyle(e)
      js.Dynamic.literal(
        colour = s.getPropertyValue("stroke"),
        width = s.getPropertyValue("stroke-width").stripSuffix("px").toDoubleOption.getOrElse(0.0)
      )
    }

  private def px(v: String): Double = v.stripSuffix("px").toDoubleOption.getOrElse(0.0)

  private def zOf(e: dom.Element): Int = dom.window.getComputedStyle(e).zIndex.toIntOption.getOrElse(0)

  private def shape(face: dom.Element): js.Dynamic =
    val s = dom.window.getComputedStyle(face)
    val before = dom.window.getComputedStyle(face, "::before")
    val r = face.getBoundingClientRect()
    val (cx, cy) = (r.left + r.width / 2, r.top + r.height / 2)
    def centred(e: dom.Element): js.Dynamic =
      val b = e.getBoundingClientRect()
      js.Dynamic.literal(dx = b.left + b.width / 2 - cx, dy = b.top + b.height / 2 - cy, w = b.width)
    val rings = face.querySelectorAll(".hd-bubble circle")
    val count = Option(face.querySelector(".hd-dot-count"))
    val disc = Option(face.querySelector(".hd-bubble"))
    js.Dynamic.literal(
      w = r.width,
      h = r.height,
      radius = px(s.getPropertyValue("border-top-left-radius")),
      radiusRaw = s.getPropertyValue("border-top-left-radius"),
      edge = s.getPropertyValue("border-top-color"),
      edgeW = px(s.getPropertyValue("border-top-width")),
      glyph = Option(face.querySelector("svg.glyph")).fold(0.0)(_.getBoundingClientRect().width),
      ink = Option(face.querySelector(".glyph-ink"))
        .fold(0.0)(e => px(dom.window.getComputedStyle(e).getPropertyValue("stroke-width"))),
      turned = before.getPropertyValue("transform"),
      beforeW = px(before.getPropertyValue("width")),
      beforeEdge = before.getPropertyValue("border-top-style"),
      rings = (0 until rings.length).map(i => centred(rings(i))).toJSArray,
      countAt = count.fold(null: js.Any)(centred),
      countOnTop = (for c <- count; d <- disc yield zOf(c) > zOf(d)).getOrElse(false)
    )

  private def measured[A](el: dom.Element)(read: => A): A =
    if el.closest(".hd-marker-list") == null then read
    else
      el.setAttribute("data-measure", "")
      try read
      finally el.removeAttribute("data-measure")

  private def where(origin: dom.DOMRect, m: DomMarker): js.Dynamic =
    TheMap.markerCanvas
      .filter(c => c.proxies.contains(m.el))
      .fold {
        val r = m.el.getBoundingClientRect()
        js.Dynamic.literal(
          x = r.left + r.width / 2 - origin.left,
          y = r.top + r.height / 2 - origin.top,
          clientX = r.left + r.width / 2,
          clientY = r.top + r.height / 2,
          w = r.width,
          h = r.height,
          offscreen = m.el.classList.contains("is-offscreen")
        )
      }(_.placed(m))

  private def describe(
      origin: dom.DOMRect,
      pane: MapStack.Pane,
      m: DomMarker,
      ghost: Boolean,
      detail: Boolean
  ): js.Dynamic =
    val els = within(m.el)
    val flags = els.flatMap(e => (0 until e.classList.length).map(e.classList(_))).distinct
    val face = Option(m.el.querySelector(faceSelector)).filterNot(_ => m.el.matches(faceSelector)).getOrElse(m.el)
    val at = where(origin, m)
    val look = dom.window.getComputedStyle(face)
    val count = Option(m.el.querySelector(".hd-dot-count")).flatMap(_.textContent.trim.toIntOption)
    val tag = m.el.asInstanceOf[js.Dynamic]
    if js.isUndefined(tag.hdSerial) then
      serial += 1
      tag.hdSerial = serial
    js.Dynamic.literal(
      serial = tag.hdSerial,
      pane = paneName(pane),
      x = at.x,
      y = at.y,
      clientX = at.clientX,
      clientY = at.clientY,
      w = at.w,
      h = at.h,
      z = zOf(m.el),
      flags = flags.toJSArray,
      id = attr(els, "data-id"),
      ck = attr(els, "data-ck"),
      label = attr(els, "aria-label"),
      title = attr(List(m.el), "title"),
      data = {
        val o = js.Dictionary.empty[String]
        els.reverse.foreach { e =>
          val ds = e.asInstanceOf[dom.HTMLElement].dataset
          ds.keys.foreach(k => o(k) = ds(k))
        }
        o
      },
      count = count.fold(null: js.Any)(n => n),
      glyphs = face.querySelectorAll("svg.glyph").length,
      badges = face.querySelectorAll("svg.glyph--badge").length,
      rects = face.querySelectorAll("svg rect").length,
      circles = face.querySelectorAll("svg circle").length,
      radius = look.borderRadius,
      edge = look.borderTopWidth.stripSuffix("px").toDoubleOption.getOrElse(0.0),
      colour = look.getPropertyValue("--c").trim,
      shape = if detail then measured(m.el)(shape(face)) else null,
      ink = stroke(face, ".glyph-ink"),
      halo = stroke(face, ".glyph-halo"),
      fill = look.backgroundColor,
      border = look.borderStyle,
      opacity = look.opacity.toDoubleOption.getOrElse(1.0),
      ghost = ghost,
      offscreen = at.offscreen
    )

  private def matches(d: js.Dynamic, spec: js.Dynamic): Boolean =
    def list(v: js.Dynamic): List[String] =
      if js.isUndefined(v) || v == null then Nil
      else if js.Array.isArray(v) then v.asInstanceOf[js.Array[String]].toList
      else List(v.asInstanceOf[String])
    val flags = d.flags.asInstanceOf[js.Array[String]].toSet
    def same(k: String): Boolean =
      val want = spec.selectDynamic(k)
      js.isUndefined(want) || (d.selectDynamic(k): js.Any).equals(want: js.Any)
    val data = d.data.asInstanceOf[js.Dictionary[String]]
    val wantData =
      if js.isUndefined(spec.data) then Map.empty[String, js.Any]
      else spec.data.asInstanceOf[js.Dictionary[js.Any]].toMap
    list(spec.flag).forall(flags) && !list(spec.not).exists(flags) &&
    List("id", "ck", "pane", "ghost", "offscreen").forall(same) &&
    wantData.forall((k, v) => if v == null then !data.contains(k) else data.get(k).exists(x => (x: js.Any).equals(v)))

  private def markers(spec: js.UndefOr[js.Dynamic]): js.Array[js.Dynamic] =
    if !TheMap.isUp then js.Array()
    else
      val origin = TheMap.surface.container.getBoundingClientRect()
      val detail = spec.exists(sp => !js.isUndefined(sp.detail) && sp.detail.asInstanceOf[Boolean])
      val every = spec.exists(sp => !js.isUndefined(sp.every) && sp.every.asInstanceOf[Boolean])
      val all = MapStack.domPanes.filter(p => every || pointPane(p)).flatMap { p =>
        val pane = TheMap.pane(p)
        pane.live.map(describe(origin, p, _, false, detail)) ++ pane.leaving.map(describe(origin, p, _, true, detail))
      }
      spec.fold(all)(sp => all.filter(matches(_, sp))).toJSArray

  private def markerAt(at: js.Array[Double]): js.Dynamic =
    if !TheMap.isUp then null
    else
      val origin = TheMap.surface.container.getBoundingClientRect()
      val hit = TheMap.markerCanvas.fold(
        Option(dom.document.elementFromPoint(origin.left + at(0), origin.top + at(1)))
          .flatMap(e => Option(e.closest(".hd-marker")))
      )(c => c.hit(origin.left + at(0), origin.top + at(1)).map(_.el))
      val found = for
        el <- hit
        p <- MapStack.domPanes
          .filter(pointPane)
          .find(p => TheMap.pane(p).live.exists(_.el eq el) || TheMap.pane(p).leaving.exists(_.el eq el))
        m <- (TheMap.pane(p).live.map(_ -> false) ++ TheMap.pane(p).leaving.map(_ -> true)).find(_._1.el eq el)
      yield describe(origin, p, m._1, m._2, false)
      found.orNull

  def install(s: MapSurface): Unit =
    s.on(MapEventKind.BaseMapReady)(_ => drawn += 1)
    val h = js.Dynamic.literal(
      setView = (
          (at: js.Array[Double], z: Double, opts: js.UndefOr[js.Dynamic]) =>
            s.setView(
              ll(at),
              MapSurface.Zoom(z),
              animate = MapSurface.SetViewAnimate(
                opts.exists(o => !js.isUndefined(o.animate) && o.animate.asInstanceOf[Boolean])
              )
            )
      ): js.Function3[js.Array[Double], Double, js.UndefOr[js.Dynamic], Unit],
      setZoom = (
          (z: Double, opts: js.UndefOr[js.Dynamic]) =>
            s.setZoom(
              MapSurface.Zoom(z),
              animate = MapSurface.SetViewAnimate(
                !opts.exists(o => !js.isUndefined(o.animate) && !o.animate.asInstanceOf[Boolean])
              )
            )
      ): js.Function2[Double, js.UndefOr[js.Dynamic], Unit],
      getZoom = (() => s.zoom.value): js.Function0[Double],
      getMaxZoom = (() => s.maxZoom.value): js.Function0[Double],
      getCenter = (() => { val c = s.center; js.Dynamic.literal(lat = c.lat.value, lng = c.lon.value) }): js.Function0[
        js.Dynamic
      ],
      getBounds = (() => {
        val b = s.bounds;
        js.Dynamic.literal(south = b.south.value, west = b.west.value, north = b.north.value, east = b.east.value)
      }): js.Function0[js.Dynamic],
      panBy =
        ((d: js.Array[Double]) => s.panBy(MapSurface.PanByDx(d(0)), MapSurface.PanByDy(d(1)))): js.Function1[js.Array[
          Double
        ], Unit],
      latLngToContainerPoint = ((at: js.Array[Double]) => {
        val p = s.project(ll(at)); js.Dynamic.literal(x = p.x.value, y = p.y.value)
      }): js.Function1[js.Array[Double], js.Dynamic],
      /** The map point at a container point `[x, y]`: `{lat, lng}`. */
      containerPointToLatLng = ((p: js.Array[Double]) => {
        val ll = s.unproject(ScreenPt(ScreenPt.X(p(0)), ScreenPt.Y(p(1))));
        js.Dynamic.literal(lat = ll.lat.value, lng = ll.lon.value)
      }): js.Function1[js.Array[Double], js.Dynamic],
      /** A popup at `[lat, lon]` with this text (what a finding's click opens). */
      openPopup = (
          (at: js.Array[Double], text: String) => {
            val _ = s.popup(
              ll(at),
              markup"""<div class="hd-popup-content zz-test-popup">$text</div>""",
              MapSurface.PopupCls("")
            )
          }
      ): js.Function2[js.Array[Double], String, Unit],
      getSize = (() => { val (w, h) = s.size; js.Dynamic.literal(x = w.value, y = h.value) }): js.Function0[js.Dynamic],
      resizes = (() => s.resizeCount.value): js.Function0[Int],
      closePopup = (() => s.closePopups()): js.Function0[Unit],
      isMoving = (() => s.isMoving): js.Function0[Boolean],
      /** Still, and every tile of the view drawn. */
      isDrawn = (() => s.isDrawn): js.Function0[Boolean],
      /** `fn` once at the next `zoomend` / `moveend`. */
      once = (
          (event: String, fn: js.Function0[Unit]) =>
            val kind = if event == "zoomend" then MapEventKind.ZoomEnd else MapEventKind.MoveEnd
            // `var`: the subscription, taken off when it fires
            var sub: Subscription = null
            sub = s.on(kind) { _ => sub.cancel(); fn() }
      ): js.Function2[String, js.Function0[Unit], Unit],
      /** The ids of the shapes a set draws now (`polities`, `event-outlines`, `place-pick`, …). */
      shapes = (
          (set: String) => s.shapeSet(MapSurface.ShapeSetId(set)).fold(js.Array[String]())(_.ids.map(_.value).toJSArray)
      ): js.Function1[String, js.Array[
        String
      ]],
      /** How many times a shape set's outlines were sent to the map (a restyle sends none). */
      shapeUploads =
        ((set: String) => s.shapeSet(MapSurface.ShapeSetId(set)).fold(0)(_.uploads.value)): js.Function1[String, Int],
      /** A drawn shape's `{colour, alpha}`, `null` when it is not drawn. */
      shapeStyle = (
          (set: String, id: String) =>
            s.shapeSet(MapSurface.ShapeSetId(set))
              .flatMap(_.styleOf(ShapeFeature.Id(id)))
              .fold(null: js.Dynamic)((c, a) => js.Dynamic.literal(colour = c.value, alpha = a.value))
      ): js.Function2[String, String, js.Dynamic],
      /** The interactive shapes under a map point, topmost first: `set:id`. */
      shapesAt = (
          (at: js.Array[Double]) => s.shapesAt(s.project(ll(at))).map(h => s"${h.set}:${h.id}").toJSArray
      ): js.Function1[js.Array[Double], js.Array[String]],
      /** The base map's names placed in the view now. */
      names = (() => s.drawnNames.map(_.value).toJSArray): js.Function0[js.Array[String]],
      /** The base map's names placed now: `{layer, text, x, y}` (a line's middle). */
      labels = (
          () =>
            s.baseMapLabels
              .map((l, t, p) => js.Dynamic.literal(layer = l.value, text = t.value, x = p.x.value, y = p.y.value))
              .toJSArray
      ): js.Function0[js.Array[js.Dynamic]],
      /** The base map's areas drawn at a map point: `layer:kind` (land cover, land use, water…). */
      areasAt =
        ((at: js.Array[Double]) => s.baseMapAreasAt(s.project(ll(at))).map(_.value).toJSArray): js.Function1[js.Array[
          Double
        ], js.Array[String]],
      /** A base-map layer's visibility (`visible`, `none`), `null` when the style has no such layer. */
      layerVisibility =
        ((id: String) => s.baseMapLayer(MapSurface.LayerId(id)).map(_.value).orNull): js.Function1[String, String],
      /** How many of a base-map layer's features (`contours`, …) are drawn in the view now. */
      layerDrawn = ((id: String) => s.baseMapDrawn(MapSurface.LayerId(id)).value): js.Function1[String, Int],
      /** How many points the cluster layer holds, and how many zooms animated its bubbles. */
      markers = ((spec: js.UndefOr[js.Dynamic]) => markers(spec)): js.Function1[js.UndefOr[js.Dynamic], js.Array[
        js.Dynamic
      ]],
      markerAt = ((at: js.Array[Double]) => markerAt(at)): js.Function1[js.Array[Double], js.Dynamic],
      spiderLegs = (() => s.container.querySelectorAll(".hd-spider-legs line").length): js.Function0[Int],
      spots = (
          (names: js.UndefOr[js.Array[String]]) =>
            val origin = s.container.getBoundingClientRect()
            MapStack.domPanes
              .filter(p => names.fold(pointPane(p))(_.contains(paneName(p))))
              .flatMap { p =>
                val pane = TheMap.pane(p)
                pane.live.map(_ -> false) ++ pane.leaving.map(_ -> true)
              }
              .map { (m, ghost) =>
                val at = where(origin, m)
                val tag = m.el.asInstanceOf[js.Dynamic]
                if js.isUndefined(tag.hdSerial) then
                  serial += 1
                  tag.hdSerial = serial
                js.Dynamic.literal(serial = tag.hdSerial, x = at.x, y = at.y, offscreen = at.offscreen, ghost = ghost)
              }
              .toJSArray
      ): js.Function1[js.UndefOr[js.Array[String]], js.Array[js.Dynamic]],
      markersIn = (
          (names: js.UndefOr[js.Array[String]]) =>
            MapStack.domPanes
              .filter(p => names.fold(true)(_.contains(paneName(p))))
              .map { p =>
                val pane = TheMap.pane(p)
                pane.live.size + pane.leaving.size
              }
              .sum
      ): js.Function1[js.UndefOr[js.Array[String]], Int],
      points = (() => MapView.dots.size.value): js.Function0[Int],
      clusterAnimations = (() => MapView.dots.animations.value): js.Function0[Int],
      /** What each tiled layer shows: `{items, cells, counted}` — its items drawn as themselves, the cells of its
        * crowded tiles, and the items those cells count.
        */
      tiled = (() =>
        def of(t: TiledPoints[?]) = {
          val (i, c, n) = t.counts; js.Dynamic.literal(items = i.value, cells = c.value, counted = n.value)
        }
        js.Dynamic
          .literal(sites = of(MapView.sitesLayer), media = of(MapView.mediaLayer), events = of(EventMarkers.layer))
      ): js.Function0[js.Dynamic],
      /** How many times a base map was drawn whole (every tile of the view). */
      baseMapsDrawn = (() => drawn): js.Function0[Int],
      capabilities = (() => {
        val c = s.capabilities;
        js.Dynamic.literal(
          webgl = c.webgl.value,
          software = c.software.value,
          renderer = c.renderer.value,
          webgl2 = c.webgl2.value
        )
      }): js.Function0[js.Dynamic],
      container = s.container
    )
    dom.window.asInstanceOf[js.Dynamic].histmersionMap = h
