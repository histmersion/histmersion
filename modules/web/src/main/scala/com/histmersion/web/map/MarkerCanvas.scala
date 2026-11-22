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

  private val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val looks = mutable.HashMap.empty[String, Option[MarkerLook]]
  private val templates = mutable.HashMap.empty[String, Option[MarkerLook]]
  private val noColours = js.Array[String]()
  private var queued = false
  private var lastPanes: () => List[List[DomMarker]] = () => Nil

  private val byEl = js.Dynamic.newInstance(js.Dynamic.global.WeakMap)()

  private val observer = new dom.MutationObserver((records, _) => {
    records.foreach { r =>
      val el = r.target match
        case e: dom.Element => Option(e.closest(".hd-marker"))
        case n              =>
          Option(n.parentNode).collect { case e: dom.Element => e }.flatMap(e => Option(e.closest(".hd-marker")))
      el.foreach(e => byEl.delete(e))
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
    info(m).foreach(d =>
      region(d.look.asInstanceOf[MarkerLook], d.colours.asInstanceOf[js.Array[String]], dom.window.devicePixelRatio)
    )

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
    panes().foreach { ms =>
      layer.length = 0
      ms.foreach { m =>
        if drawable(m) then
          held(m).foreach { d =>
            d.x = m.drawX
            d.y = m.drawY
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
        look.blit(
          ctx,
          atlas,
          r,
          dpr,
          d.x.asInstanceOf[Double] + d.sx.asInstanceOf[Double],
          d.y.asInstanceOf[Double] + d.sy.asInstanceOf[Double],
          d.alpha.asInstanceOf[Double]
        )
      }
    }

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
    templates.getOrElseUpdate(
      MarkerLook.templateKey(el), {
        val first = snapshot(el, MarkerLook.Sentinels)
        val second = snapshot(el, MarkerLook.Second)
        for
          a <- first
          b <- second
          if sameSprites(a.sprite(js.Array(MarkerLook.Second*), 1), b.sprite(noColours, 1))
        yield a
      }
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
          x = 0.0,
          y = 0.0
        )
        byEl.set(m.el, d)
        d
      }

object MarkerCanvas:
  val StorageKey = "histmersion.ui.markerCanvas"

  def px(s: String): Option[Double] = s.trim.stripSuffix("px").trim.toDoubleOption
