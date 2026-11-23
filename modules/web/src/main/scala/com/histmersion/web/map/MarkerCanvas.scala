package com.histmersion
package web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

final class MarkerCanvas(root: dom.HTMLElement, before: dom.Node, frame: dom.HTMLElement):
  val canvas: dom.html.Canvas = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
  canvas.className = "hd-marker-canvas"
  val proxies: dom.HTMLElement = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
  proxies.className = "hd-marker-proxies hd-marker-list"
  private val studio = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
  studio.className = "hd-marker-proxies"
  root.insertBefore(canvas, before)
  root.appendChild(proxies)
  root.appendChild(studio)

  def group(pane: String): dom.HTMLElement =
    val g = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    g.className = "hd-marker-group"
    g.setAttribute("data-pane", pane)
    proxies.appendChild(g)
    g

  private var focused: DomMarker = null

  private def markerOf(t: dom.EventTarget): Option[DomMarker] =
    Option(t)
      .collect { case e: dom.Element => e }
      .flatMap(e => Option(e.closest(".hd-marker")))
      .flatMap(e =>
        Option(e.asInstanceOf[js.Dynamic].hdMarker).filterNot(js.isUndefined).map(_.asInstanceOf[DomMarker])
      )

  proxies.addEventListener(
    "focusin",
    (e: dom.FocusEvent) =>
      markerOf(e.target).foreach { m =>
        if (focused ne null) && (focused ne m) && (focused ne hovered) then unanchor(focused)
        focused = m
        anchor(m)
      },
    true
  )

  private val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val looks = mutable.HashMap.empty[String, Option[MarkerLook]]
  private val templates = mutable.HashMap.empty[String, Option[MarkerLook]]
  private val validated = mutable.HashMap.empty[String, Boolean]
  private val noColours = js.Array[String]()
  private var queued = false
  private var lastPanes: () => List[List[DomMarker]] = () => Nil

  private val byEl = js.Dynamic.newInstance(js.Dynamic.global.WeakMap)()
  private val keys = js.Dynamic.newInstance(js.Dynamic.global.WeakMap)()

  private val observer = new dom.MutationObserver((records, _) => {
    records.foreach { r =>
      val el = r.target match
        case e: dom.Element => Option(e.closest(".hd-marker"))
        case n              =>
          Option(n.parentNode).collect { case e: dom.Element => e }.flatMap(e => Option(e.closest(".hd-marker")))
      el.foreach { e =>
        byEl.delete(e)
        keys.delete(e)
        if r.attributeName == "class" then e.asInstanceOf[js.Dynamic].hdT0 = dom.window.performance.now()
      }
      (0 until r.addedNodes.length).map(r.addedNodes(_)).foreach { n =>
        n.asInstanceOf[js.Dynamic].hdT0 = dom.window.performance.now()
      }
    }
    schedule()
  })
  observer.observe(
    proxies,
    new dom.MutationObserverInit:
      subtree = true
      childList = true
      attributes = true
      characterData = true
  )

  private var dirty = false
  private var width = -1.0
  private var height = -1.0

  def schedule(): Unit =
    dirty = true
    if !queued then
      queued = true
      val _ = dom.window.requestAnimationFrame { _ =>
        queued = false
        if dirty then paint(lastPanes)
      }

  def resized(): Unit = width = -1.0

  private val atlas = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
  private val atlasSide = 2048
  atlas.width = atlasSide
  atlas.height = atlasSide
  private val actx = atlas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val regions = js.Dynamic.newInstance(js.Dynamic.global.Map)()
  private var generation = 0.0
  private var atlasDpr = 0.0
  private var ax = 0
  private var ay = 0
  private var shelf = 0
  private var nextId = 0

  private def clearAtlas(dpr: Double): Unit =
    actx.setTransform(1, 0, 0, 1, 0, 0)
    actx.clearRect(0, 0, atlasSide.toDouble, atlasSide.toDouble)
    regions.clear()
    generation += 1
    atlasDpr = dpr
    ax = 0
    ay = 0
    shelf = 0

  private def region(look: MarkerLook, colours: js.Array[String], dpr: Double): js.Array[Double] =
    if dpr != atlasDpr then clearAtlas(dpr)
    if look.atlasId < 0 then
      look.atlasId = nextId
      nextId += 1
    val key = s"${look.atlasId}|${colours.mkString("|")}"
    val held = regions.get(key)
    if !js.isUndefined(held) then held.asInstanceOf[js.Array[Double]]
    else
      val sw = math.min(look.spriteWidth(dpr), atlasSide)
      val sh = math.min(look.spriteHeight(dpr), atlasSide)
      if ax + sw > atlasSide then
        ax = 0
        ay += shelf
        shelf = 0
      if ay + sh > atlasSide then clearAtlas(dpr)
      look.render(actx, ax.toDouble, ay.toDouble, colours, dpr)
      val r = js.Array(generation, ax.toDouble, ay.toDouble, sw.toDouble, sh.toDouble)
      ax += sw + 1
      shelf = math.max(shelf, sh + 1)
      regions.set(key, r)
      r

  def prepare(m: DomMarker): Unit =
    warm(List(m))
    info(m).foreach(d =>
      region(d.look.asInstanceOf[MarkerLook], d.colours.asInstanceOf[js.Array[String]], dom.window.devicePixelRatio)
    )
    schedule()

  private def batch(els: List[dom.Element], cs: List[String]): List[Option[MarkerLook]] =
    val clones = els.map(MarkerLook.withColours(_, cs))
    clones.foreach(studio.appendChild)
    val read = clones.map(MarkerLook.of)
    clones.foreach(studio.removeChild)
    read

  private def warm(ms: List[DomMarker]): Unit =
    val fresh = mutable.LinkedHashMap.empty[String, dom.Element]
    ms.foreach { m =>
      if m.el.isConnected && js.isUndefined(byEl.get(m.el)) then
        val key = MarkerLook.templateKey(m.el)
        val _ = keys.set(m.el, key)
        if !templates.contains(key) && !fresh.contains(key) then fresh(key) = m.el
    }
    if fresh.nonEmpty then
      if templates.size + fresh.size > 500 then templates.clear()
      if validated.size > 2000 then validated.clear()
      val firsts = fresh.toList.zip(batch(fresh.values.toList, MarkerLook.Sentinels))
      val asked = firsts
        .collect { case ((key, el), Some(look)) if !validated.contains(MarkerLook.shapeKey(key)) => (key, el, look) }
        .distinctBy((key, _, _) => MarkerLook.shapeKey(key))
      asked.zip(batch(asked.map(_._2), MarkerLook.Second)).foreach { case ((key, _, a), b) =>
        validated(MarkerLook.shapeKey(key)) =
          b.exists(b => sameSprites(a.sprite(js.Array(MarkerLook.Second*), 1), b.sprite(noColours, 1)))
      }
      firsts.foreach { case ((key, _), first) =>
        templates(key) = first.filter(_ => validated.getOrElse(MarkerLook.shapeKey(key), false))
      }

  def paint(panes: () => List[List[DomMarker]]): Unit =
    lastPanes = panes
    dirty = false
    val dpr = dom.window.devicePixelRatio
    if width < 0 then
      width = frame.clientWidth.toDouble
      height = frame.clientHeight.toDouble
    if canvas.width != math.round(width * dpr).toInt || canvas.height != math.round(height * dpr).toInt then
      canvas.width = math.round(width * dpr).toInt
      canvas.height = math.round(height * dpr).toInt
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, width, height)
    val layer = js.Array[js.Dynamic]()
    val now = dom.window.performance.now()
    val still = stillNow
    var animating = false
    panes().foreach { ms =>
      layer.length = 0
      ms.foreach { m =>
        if drawable(m) then
          held(m).foreach { d =>
            d.x = m.drawX
            d.y = m.drawY
            d.marker = m.asInstanceOf[js.Any]
            layer.push(d)
          }
      }
      if layer.exists(_.z.asInstanceOf[Int] != 0) then
        val _ = layer
          .asInstanceOf[js.Dynamic]
          .sort((a: js.Dynamic, b: js.Dynamic) => a.z.asInstanceOf[Int] - b.z.asInstanceOf[Int])
      layer.foreach { d =>
        val look = d.look.asInstanceOf[MarkerLook]
        val r = region(look, d.colours.asInstanceOf[js.Array[String]], dpr)
        val mo = motionOf(d, now, still)
        animating ||= mo.running
        val x = d.x.asInstanceOf[Double] + d.sx.asInstanceOf[Double] + mo.dx
        val y = d.y.asInstanceOf[Double] + d.sy.asInstanceOf[Double] + mo.dy
        val grown = (hovered ne null) && (d.marker.asInstanceOf[DomMarker] eq hovered) && d.grows.asInstanceOf[Boolean]
        val scale = mo.scale * (if grown then 1.12 else 1.0)
        if scale != 1.0 then
          ctx.save()
          ctx.translate(x, y)
          ctx.scale(scale, scale)
          ctx.translate(-x, -y)
        if mo.alpha > 0 && scale > 0 then look.blit(ctx, atlas, r, dpr, x, y, d.alpha.asInstanceOf[Double] * mo.alpha)
        if scale != 1.0 then ctx.restore()
      }
    }
    if animating then schedule()
    if (focused ne null) && (focused.el.parentNode eq null) then focused = null
    if focused ne null then anchor(focused)

  private def motionOf(d: js.Dynamic, now: Double, still: Boolean): MarkerMotion.Motion =
    MarkerMotion.motion(
      d.present.asInstanceOf[Set[String]],
      d.mx.asInstanceOf[Double],
      d.my.asInstanceOf[Double],
      now - d.t0.asInstanceOf[Double],
      still
    )

  private def stillNow: Boolean = dom.document.documentElement.getAttribute("data-map-motion") == "still"

  private var hovered: DomMarker = null
  private var downAt: (Double, Double) = (Double.NaN, Double.NaN)

  def placed(m: DomMarker): js.Dynamic =
    val r = frame.getBoundingClientRect()
    val d = info(m)
    val mo = d.map(motionOf(_, dom.window.performance.now(), stillNow))
    val sx = d.fold(0.0)(_.sx.asInstanceOf[Double]) + mo.fold(0.0)(_.dx)
    val sy = d.fold(0.0)(_.sy.asInstanceOf[Double]) + mo.fold(0.0)(_.dy)
    val look = d.map(_.look.asInstanceOf[MarkerLook])
    js.Dynamic.literal(
      x = m.drawX + sx,
      y = m.drawY + sy,
      clientX = r.left + m.drawX + sx,
      clientY = r.top + m.drawY + sy,
      w = look.fold(0.0)(_.w),
      h = look.fold(0.0)(_.h),
      offscreen = m.offscreen
    )

  def hit(clientX: Double, clientY: Double): Option[DomMarker] =
    val r = frame.getBoundingClientRect()
    val (x, y) = (clientX - r.left, clientY - r.top)
    val now = dom.window.performance.now()
    val still = stillNow
    lastPanes().reverseIterator
      .flatMap { ms =>
        ms.filter(drawable)
          .flatMap(m => held(m).map(m -> _))
          .sortBy((_, d) => d.z.asInstanceOf[Int])
          .reverseIterator
      }
      .find { (m, d) =>
        val look = d.look.asInstanceOf[MarkerLook]
        val mo = motionOf(d, now, still)
        !d.inert.asInstanceOf[Boolean] && mo.alpha > 0 &&
        math.abs(x - m.drawX - d.sx.asInstanceOf[Double] - mo.dx) <= look.w / 2 &&
        math.abs(y - m.drawY - d.sy.asInstanceOf[Double] - mo.dy) <= look.h / 2
      }
      .map(_._1)

  private def anchor(m: DomMarker): Unit =
    val at = placed(m)
    m.el.asInstanceOf[dom.HTMLElement].style.setProperty("--ax", s"${at.x.asInstanceOf[Double] + 10000}px")
    m.el.asInstanceOf[dom.HTMLElement].style.setProperty("--ay", s"${at.y.asInstanceOf[Double]}px")

  private def unanchor(m: DomMarker): Unit =
    m.el.asInstanceOf[dom.HTMLElement].style.setProperty("--ax", "-9999px")
    m.el.asInstanceOf[dom.HTMLElement].style.setProperty("--ay", "-9999px")

  private def hoverTo(next: Option[DomMarker], e: dom.MouseEvent): Unit =
    val now = next.orNull
    if now ne hovered then
      Option(hovered).flatMap(m => Option(m.handlers)).foreach(_.out(e))
      Option(hovered).filter(_ ne focused).foreach(unanchor)
      next.foreach(anchor)
      hovered = now
      frame.classList.toggle("is-over-marker", now ne null)
      next.flatMap(m => Option(m.handlers)).foreach(_.over(e))
      schedule()
    else next.flatMap(m => Option(m.handlers)).foreach(_.move(e))

  private def onMap(e: dom.Event): Boolean = e.target match
    case t: dom.Element => (t eq root) || ((t.parentNode eq root) && t.tagName.toLowerCase == "canvas")
    case _              => false

  frame.addEventListener(
    "mousemove",
    (e: dom.MouseEvent) => hoverTo(if onMap(e) then hit(e.clientX, e.clientY) else None, e),
    useCapture = true
  )
  frame.addEventListener("mouseleave", (e: dom.MouseEvent) => hoverTo(None, e))
  frame.addEventListener(
    "mousedown",
    (e: dom.MouseEvent) => downAt = (e.clientX, e.clientY),
    useCapture = true
  )
  frame.addEventListener(
    "click",
    (e: dom.MouseEvent) =>
      val still = math.hypot(e.clientX - downAt._1, e.clientY - downAt._2) <= 4 || downAt._1.isNaN
      if still && onMap(e) then
        hit(e.clientX, e.clientY).foreach { m =>
          anchor(m)
          Option(m.handlers).foreach(_.click(e))
        }
    ,
    useCapture = true
  )

  private def drawable(m: DomMarker): Boolean = !m.offscreen && !m.drawX.isNaN

  private def snapshot(el: dom.Element, cs: List[String]): Option[MarkerLook] =
    val clone = MarkerLook.withColours(el, cs)
    studio.appendChild(clone)
    val look = MarkerLook.of(clone)
    val _ = studio.removeChild(clone)
    look

  private def sameSprites(a: dom.html.Canvas, b: dom.html.Canvas): Boolean =
    a.width == b.width && a.height == b.height && a.width > 0 && a.height > 0 && {
      val da = a.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D].getImageData(0, 0, a.width, a.height).data
      val db = b.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D].getImageData(0, 0, b.width, b.height).data
      (0 until da.length).forall(i => math.abs(da(i) - db(i)) <= 2)
    }

  private def template(el: dom.Element): Option[MarkerLook] =
    if templates.size > 500 then templates.clear()
    if validated.size > 2000 then validated.clear()
    val warmed = keys.get(el)
    val key = if js.isUndefined(warmed) then MarkerLook.templateKey(el) else warmed.asInstanceOf[String]
    val _ = keys.delete(el)
    templates.getOrElseUpdate(
      key,
      snapshot(el, MarkerLook.Sentinels).filter(a =>
        validated.getOrElseUpdate(
          MarkerLook.shapeKey(key),
          snapshot(el, MarkerLook.Second).exists(b =>
            sameSprites(a.sprite(js.Array(MarkerLook.Second*), 1), b.sprite(noColours, 1))
          )
        )
      )
    )

  private def held(m: DomMarker): Option[js.Dynamic] = info(m).filterNot(_.pending.asInstanceOf[Boolean])

  private def info(m: DomMarker): Option[js.Dynamic] =
    val cached = byEl.get(m.el)
    if !js.isUndefined(cached) then Option(cached)
    else
      val made = template(m.el).map(t => (t, MarkerLook.colours(m.el))).orElse {
        val key = MarkerLook.key(m.el)
        if looks.size > 1500 then looks.clear()
        looks.getOrElseUpdate(key, snapshot(m.el, Nil)).map(l => (l, noColours))
      }
      made.map { (l, c) =>
        val d = js.Dynamic.literal(
          look = l.asInstanceOf[js.Any],
          colours = c,
          z = MarkerLook.inlineVar(m.el, "--z").toIntOption.getOrElse(0),
          sx = MarkerLook.num(MarkerLook.inlineVar(m.el, "--sx")),
          sy = MarkerLook.num(MarkerLook.inlineVar(m.el, "--sy")),
          alpha = if m.el.classList.contains("hd-spidered") then 0.3 else 1.0,
          pending = m.el.classList.contains(ClusterMotion.Pending),
          inert = m.el.classList.contains("hd-cluster-ghost") || m.el.classList.contains("hd-spider-legs"),
          grows = m.el.querySelector(".hd-point") != null,
          present = MarkerLook.Transient
            .filter(t => m.el.classList.contains(t) || m.el.querySelector(s".$t") != null)
            .toSet
            .asInstanceOf[js.Any],
          mx = MarkerLook.num(MarkerLook.inlineVar(m.el, "--move-x")),
          my = MarkerLook.num(MarkerLook.inlineVar(m.el, "--move-y")),
          t0 = Option(m.el.asInstanceOf[js.Dynamic].hdT0)
            .filterNot(js.isUndefined)
            .map(_.asInstanceOf[Double])
            .getOrElse(dom.window.performance.now()),
          marker = null,
          x = 0.0,
          y = 0.0
        )
        byEl.set(m.el, d)
        d
      }

object MarkerCanvas:
  val StorageKey = "histmersion.ui.markerCanvas"

  def px(s: String): Option[Double] = s.trim.stripSuffix("px").trim.toDoubleOption
