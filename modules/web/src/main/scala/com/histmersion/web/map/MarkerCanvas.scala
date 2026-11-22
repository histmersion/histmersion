package com.histmersion
package web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

final class MarkerCanvas(root: dom.HTMLElement, before: dom.Node, frame: dom.HTMLElement):
  val canvas: dom.html.Canvas = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
  canvas.className = "hd-marker-canvas"
  val proxies: dom.HTMLElement = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
  proxies.className = "hd-marker-proxies"
  root.insertBefore(canvas, before)
  root.appendChild(proxies)

  private val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val looks = mutable.HashMap.empty[String, MarkerLook]
  private var queued = false
  private var lastPanes: () => List[List[DomMarker]] = () => Nil

  private val byEl = js.Dynamic.newInstance(js.Dynamic.global.WeakMap)()

  private val observer = new dom.MutationObserver((records, _) => {
    records.foreach { r =>
      val node = r.target
      val el = node match
        case e: dom.Element => Option(e.closest(".hd-marker"))
        case n              => Option(n.parentNode).collect { case e: dom.Element => e }.flatMap(e => Option(e.closest(".hd-marker")))
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

  def schedule(): Unit =
    if !queued then
      queued = true
      val _ = dom.window.requestAnimationFrame(_ => { queued = false; paint(lastPanes) })

  def paint(panes: () => List[List[DomMarker]]): Unit =
    lastPanes = panes
    val dpr = dom.window.devicePixelRatio
    val w = frame.clientWidth
    val h = frame.clientHeight
    if canvas.width != math.round(w * dpr).toInt || canvas.height != math.round(h * dpr).toInt then
      canvas.width = math.round(w * dpr).toInt
      canvas.height = math.round(h * dpr).toInt
      canvas.style.width = s"${w}px"
      canvas.style.height = s"${h}px"
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, w.toDouble, h.toDouble)
    panes().foreach { ms =>
      ms.filter(drawable).sortBy(stacking).foreach { m =>
        lookOf(m).foreach { look =>
          val sx = MarkerLook.num(m.el.style.getPropertyValue("--sx"))
          val sy = MarkerLook.num(m.el.style.getPropertyValue("--sy"))
          look.draw(ctx, m.drawX + sx, m.drawY + sy, if m.el.classList.contains("hd-spidered") then 0.3 else 1.0)
        }
      }
    }

  private def stacking(m: DomMarker): Int = m.el.style.getPropertyValue("--z").trim.toIntOption.getOrElse(0)

  private def drawable(m: DomMarker): Boolean =
    !m.offscreen && !m.drawX.isNaN && !m.el.classList.contains(ClusterMotion.Pending)

  private def lookOf(m: DomMarker): Option[MarkerLook] =
    val held = byEl.get(m.el)
    if !js.isUndefined(held) then Option(held.asInstanceOf[MarkerLook])
    else
      val key = MarkerLook.key(m.el)
      val made = looks.get(key).orElse {
        val l = MarkerLook.of(m.el)
        l.foreach { look =>
          if looks.size > 1500 then looks.clear()
          looks(key) = look
        }
        l
      }
      made.foreach(l => byEl.set(m.el, l.asInstanceOf[js.Any]))
      made

  def lookAt(m: DomMarker): Option[MarkerLook] = lookOf(m)

object MarkerCanvas:
  val StorageKey = "histmersion.ui.markerCanvas"


  def px(s: String): Option[Double] = s.trim.stripSuffix("px").trim.toDoubleOption
