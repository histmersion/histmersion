package histdb.web

import com.raquo.laminar.api.L.*
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
      children <-- windows.signal.map(_.map(renderWindow)),
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
        val w0 = math.min(780, vw * 0.7)
        val h0 = math.min(vh * 0.75, 600)
        val offset = ((wid - 1) % 5) * 28
        val x0 = math.max(20, (vw - w0) / 2 + offset)
        val y0 = math.max(20, 60 + offset)
        val ws = WinState(wid, key, "loading…", x0, y0, w0, h0, nextZ, minimized = false)
        windows.update(_ :+ ws)
        loadContent(wid, kind, id)

  def closeAll(): Unit = windows.set(Nil)

  private def loadContent(wid: Int, kind: String, id: String): Unit =
    if kind == "graph" then
      val contentEl = document.getElementById(s"wm-content-$wid")
      if contentEl != null then
        windows.update(_.map(w => if w.id == wid then w.copy(title = s"graph · $id") else w))
        GraphPanel.render(id, contentEl)
      return

    val fetchUrl = buildUrl(kind, id, fragment = true)
    ApiClient.fetchHtml(fetchUrl).foreach { html =>
      val contentEl = document.getElementById(s"wm-content-$wid")
      if contentEl != null then
        contentEl.innerHTML = html
        val titleEl = contentEl.querySelector("h1, h2")
        val title = if titleEl != null then titleEl.textContent.trim else id
        windows.update(_.map(w => if w.id == wid then w.copy(title = title) else w))
        Citations.process(contentEl)
        wireLinks(wid, contentEl)
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
          val key = s"$kind:$linkId"
          open(key, kind, linkId)
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

  private def buildUrl(kind: String, id: String, fragment: Boolean): String =
    val base = if kind == "article" then "/article/" else "/source/"
    val params = new dom.URLSearchParams("")
    if fragment then params.set("fragment", "true")
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

  private def renderWindow(w: WinState): HtmlElement =
    div(
      cls := "wm-window",
      idAttr := s"wm-${w.id}",
      display := (if w.minimized then "none" else "flex"),
      left := s"${w.x}px",
      top := s"${w.y}px",
      width := s"${w.w}px",
      height := s"${w.h}px",
      zIndex := w.z,
      onPointerDown --> { _ => focus(w.id) },
      div(
        cls := "wm-titlebar",
        onPointerDown --> { ev =>
          ev.preventDefault()
          dragWin = Some(w.id)
          dragOx = ev.clientX - w.x
          dragOy = ev.clientY - w.y
          ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
        },
        onPointerMove --> { ev =>
          dragWin.filter(_ == w.id).foreach { _ =>
            val nx = math.max(0, ev.clientX - dragOx)
            val ny = math.max(0, ev.clientY - dragOy)
            windows.update(_.map(ww => if ww.id == w.id then ww.copy(x = nx, y = ny) else ww))
          }
        },
        onPointerUp --> { ev =>
          if dragWin.contains(w.id) then
            dragWin = None
            ev.target.asInstanceOf[dom.HTMLElement].releasePointerCapture(ev.pointerId)
        },
        span(cls := "wm-title", w.title),
        div(
          cls := "wm-buttons",
          button(cls := "wm-btn", "−", title := "Minimize",
            onClick.stopPropagation --> { _ => minimize(w.id) }),
          a(cls := "wm-btn wm-newtab", href := buildUrl(
            if w.key.startsWith("article:") then "article" else "source",
            w.key.dropWhile(_ != ':').drop(1),
            fragment = false
          ), target := "_blank", "↗", title := "Open in new tab"),
          button(cls := "wm-btn wm-close", "✕", title := "Close",
            onClick.stopPropagation --> { _ => closeWin(w.id) })
        )
      ),
      div(
        cls := "wm-content modal-content",
        idAttr := s"wm-content-${w.id}"
      ),
      div(
        cls := "wm-resize-handle",
        onPointerDown --> { ev =>
          ev.preventDefault()
          ev.stopPropagation()
          resizeWin = Some(w.id)
          resizeOx = ev.clientX
          resizeOy = ev.clientY
          resizeOw = w.w
          resizeOh = w.h
          ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
        },
        onPointerMove --> { ev =>
          resizeWin.filter(_ == w.id).foreach { _ =>
            val nw = math.max(300, resizeOw + (ev.clientX - resizeOx))
            val nh = math.max(200, resizeOh + (ev.clientY - resizeOy))
            windows.update(_.map(ww => if ww.id == w.id then ww.copy(w = nw, h = nh) else ww))
          }
        },
        onPointerUp --> { ev =>
          if resizeWin.contains(w.id) then
            resizeWin = None
            ev.target.asInstanceOf[dom.HTMLElement].releasePointerCapture(ev.pointerId)
        }
      )
    )
