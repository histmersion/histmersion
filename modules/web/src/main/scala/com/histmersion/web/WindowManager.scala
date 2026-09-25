package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom
import org.scalajs.dom.document

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object WindowManager:

  private case class WinState(
      id: Int,
      key: String,
      title: String,
      x: Double,
      y: Double,
      w: Double,
      h: Double,
      z: Int,
      minimized: Boolean
  )

  private val windows  = Var[List[WinState]](Nil)
  private var nextId   = 1
  private var nextZ    = 100
  private var dragWin  = Option.empty[Int]
  private var dragOx   = 0.0
  private var dragOy   = 0.0
  private var resizeWin = Option.empty[Int]
  private var resizeOx = 0.0
  private var resizeOy = 0.0
  private var resizeOw = 0.0
  private var resizeOh = 0.0

  def element: HtmlElement =
    div(
      cls := "wm-container",
      children <-- windows.signal.split(_.id) { (wid, initial, wSignal) =>
        renderWindow(initial, wSignal)
      },
      child <-- windows.signal.map { ws =>
        val mins = ws.filter(_.minimized)
        if mins.isEmpty then emptyNode
        else
          div(
            cls := "wm-taskbar",
            mins.map { w =>
              button(
                cls := "wm-taskbar-btn",
                w.title,
                onClick --> { _ => restore(w.id) }
              )
            }
          )
      }
    )

  def open(key: String, kind: String, id: String): Unit =
    val existing = windows.now().find(_.key == key)
    existing match
      case Some(w) =>
        focus(w.id)
        if w.minimized then restore(w.id)
      case None =>
        val wid = nextId; nextId += 1
        nextZ += 1
        val vw = dom.window.innerWidth
        val vh = dom.window.innerHeight
        val w0 = math.min(Layout.windowMaxWidth, vw * 0.7)
        val h0 = math.min(vh * 0.75, Layout.windowMaxHeight)
        val offset = ((wid - 1) % Layout.windowCascadeSlots) * Layout.windowCascadeOffset
        val x0 = math.max(20, (vw - w0) / 2 + offset)
        val y0 = math.max(20, 60 + offset)
        val ws = WinState(wid, key, "loading…", x0, y0, w0, h0, nextZ, minimized = false)
        windows.update(_ :+ ws)
        dom.window.setTimeout(() => loadContent(wid, kind, id), 0)

  def closeAll(): Unit = windows.set(Nil)

  private def loadContent(wid: Int, kind: String, id: String, extraParams: Map[String, String] = Map.empty): Unit =
    if kind == "graph" then
      val contentEl = document.getElementById(s"wm-content-$wid")
      if contentEl != null then
        windows.update(_.map(w => if w.id == wid then w.copy(title = s"graph · $id") else w))
        GraphPanel.render(id, contentEl)
    else
      val fetchUrl = buildUrl(kind, id, fragment = true, extraParams)
      ApiClient.fetchHtml(fetchUrl).foreach { html =>
        val contentEl = document.getElementById(s"wm-content-$wid")
        if contentEl != null then
          contentEl.innerHTML = html
          val titleEl = contentEl.querySelector("h1, h2")
          val title = if titleEl != null then titleEl.textContent.trim else id
          windows.update(_.map(w => if w.id == wid then w.copy(title = title) else w))
          wireLinks(wid, contentEl)
          InPage.wire(contentEl)
          if kind == "source" then addGraphButton(contentEl, id)
      }

  private def wireLinks(wid: Int, root: dom.Element): Unit =
    root.addEventListener("click", { (ev: dom.MouseEvent) =>
      val anchor = Option(ev.target.asInstanceOf[dom.Element].closest("a"))
      anchor.foreach { a =>
        val raw = Option(a.getAttribute("href")).getOrElse("")
        val m = """^/(source|article)/([a-z0-9_.-]+)(?:\?(.*))?$""".r.findFirstMatchIn(raw)
        m.foreach { mm =>
          ev.preventDefault()
          val kind = mm.group(1)
          val linkId = mm.group(2)
          val qs = Option(mm.group(3)).getOrElse("")
          val params = new dom.URLSearchParams(qs)
          val paramsMap = scala.collection.mutable.Map.empty[String, String]
          params.forEach { (value, key) => paramsMap(key) = value }
          val curKey = windows.now().find(_.id == wid).map(_.key).getOrElse("")
          val targetKey = s"$kind:$linkId"
          if targetKey == curKey then
            loadContent(wid, kind, linkId, paramsMap.toMap)
          else
            open(targetKey, kind, linkId)
        }
      }
    })

  private def addGraphButton(root: dom.Element, citekey: String): Unit =
    val h1 = root.querySelector("h1, h2")
    if h1 != null then
      val btn = document.createElement("button").asInstanceOf[dom.html.Button]
      btn.className = "wm-graph-btn"
      btn.textContent = "graph"
      btn.title = "citation graph"
      btn.addEventListener("click", { (_: dom.MouseEvent) =>
        GraphPanel.open(citekey)
      })
      h1.parentNode.insertBefore(btn, h1.nextSibling)

  private def buildUrl(kind: String, id: String, fragment: Boolean, extra: Map[String, String] = Map.empty): String =
    val base = if kind == "article" then "/article/" else "/source/"
    val params = new dom.URLSearchParams("")
    if fragment then params.set("fragment", "true")
    extra.foreach((k, v) => if k != "fragment" then params.set(k, v))
    val qs = params.toString
    base + js.URIUtils.encodeURIComponent(id) + (if qs.nonEmpty then "?" + qs else "")

  private def focus(wid: Int): Unit =
    nextZ += 1
    windows.update(_.map(w => if w.id == wid then w.copy(z = nextZ) else w))

  private def restore(wid: Int): Unit =
    nextZ += 1
    windows.update(_.map(w => if w.id == wid then w.copy(minimized = false, z = nextZ) else w))

  private def minimize(wid: Int): Unit =
    windows.update(_.map(w => if w.id == wid then w.copy(minimized = true) else w))

  private def closeWin(wid: Int): Unit =
    windows.update(_.filterNot(_.id == wid))

  private def renderWindow(initial: WinState, wSignal: Signal[WinState]): HtmlElement =
    val wid = initial.id
    div(
      cls := "wm-window",
      idAttr := s"wm-$wid",
      display <-- wSignal.map(w => if w.minimized then "none" else "flex"),
      left <-- wSignal.map(w => s"${w.x}px"),
      top <-- wSignal.map(w => s"${w.y}px"),
      width <-- wSignal.map(w => s"${w.w}px"),
      height <-- wSignal.map(w => s"${w.h}px"),
      zIndex <-- wSignal.map(_.z),
      onPointerDown --> { _ => focus(wid) },
      div(
        cls := "wm-titlebar",
        onPointerDown --> { ev =>
          ev.preventDefault()
          val cur = windows.now().find(_.id == wid)
          cur.foreach { w =>
            dragWin = Some(wid)
            dragOx = ev.clientX - w.x
            dragOy = ev.clientY - w.y
          }
          ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
        },
        onPointerMove --> { ev =>
          dragWin.filter(_ == wid).foreach { _ =>
            val nx = math.max(0, ev.clientX - dragOx)
            val ny = math.max(0, ev.clientY - dragOy)
            windows.update(_.map(ww => if ww.id == wid then ww.copy(x = nx, y = ny) else ww))
          }
        },
        onPointerUp --> { ev =>
          if dragWin.contains(wid) then
            dragWin = None
            ev.target.asInstanceOf[dom.HTMLElement].releasePointerCapture(ev.pointerId)
        },
        span(cls := "wm-title", child.text <-- wSignal.map(_.title)),
        div(
          cls := "wm-buttons",
          button(cls := "wm-btn", "−", title := "Minimize",
            onClick.stopPropagation --> { _ => minimize(wid) }),
          a(cls := "wm-btn wm-newtab", href := buildUrl(
            if initial.key.startsWith("article:") then "article" else "source",
            initial.key.dropWhile(_ != ':').drop(1),
            fragment = false
          ), target := "_blank", "↗", title := "Open in new tab"),
          button(cls := "wm-btn wm-close", "✕", title := "Close",
            onClick.stopPropagation --> { _ => closeWin(wid) })
        )
      ),
      div(
        cls := "wm-content modal-content",
        idAttr := s"wm-content-$wid"
      ),
      div(
        cls := "wm-resize-handle",
        onPointerDown --> { ev =>
          ev.preventDefault()
          ev.stopPropagation()
          val cur = windows.now().find(_.id == wid)
          cur.foreach { w =>
            resizeWin = Some(wid)
            resizeOx = ev.clientX
            resizeOy = ev.clientY
            resizeOw = w.w
            resizeOh = w.h
          }
          ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
        },
        onPointerMove --> { ev =>
          resizeWin.filter(_ == wid).foreach { _ =>
            val nw = math.max(Layout.windowMinWidth.value, resizeOw + (ev.clientX - resizeOx))
            val nh = math.max(Layout.windowMinHeight.value, resizeOh + (ev.clientY - resizeOy))
            windows.update(_.map(ww => if ww.id == wid then ww.copy(w = nw, h = nh) else ww))
          }
        },
        onPointerUp --> { ev =>
          if resizeWin.contains(wid) then
            resizeWin = None
            ev.target.asInstanceOf[dom.HTMLElement].releasePointerCapture(ev.pointerId)
        }
      )
    )
