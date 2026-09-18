package histdb.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{document, window}

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ArticleModal:

  private var current: Option[String] = None
  private var opener: Option[dom.Element] = None
  private var lastQ: String = ""
  private var lightboxEl: Option[dom.Element] = None

  private case class MatchState(marks: List[dom.Element], nav: Option[dom.Element], var idx: Int)
  private var active: Option[MatchState] = None

  private val modalHidden = Var(true)

  val element: HtmlElement =
    div(
      idAttr := "modal",
      cls := "modal-backdrop",
      display <-- modalHidden.signal.map(h => if h then "none" else "flex"),
      onClick --> { ev =>
        if ev.target == ev.currentTarget then close()
      },
      div(
        cls := "modal",
        tabIndex := -1,
        role := "dialog",
        aria.labelledBy := "modal-title",
        div(
          cls := "modal-bar",
          button(idAttr := "modal-back", "◀",
            onClick --> { _ => window.history.back() }),
          button(idAttr := "modal-fwd", "▶",
            onClick --> { _ => window.history.forward() }),
          span(idAttr := "modal-title"),
          a(idAttr := "modal-newtab", target := "_blank", "↗"),
          button(idAttr := "modal-close", "✕",
            onClick --> { _ => close() })
        ),
        div(idAttr := "modal-content"),
        onKeyDown --> { ev =>
          if ev.key == "Tab" then trapFocus(ev)
        }
      ),
      onMountCallback { _ =>
        UiState.signal.foreach { _ =>
          val want = UiState.open.now()
          syncNav()
          val wantStr = want.orNull
          val currentStr = current.orNull
          if wantStr != currentStr then
            want.flatMap(parseKey) match
              case None =>
                if current.isDefined then closeFromState()
              case Some((kind, id)) =>
                doOpen(id, Map.empty, kind, fromState = true)
        }(using unsafeWindowOwner)

        document.addEventListener("keydown", { (ev: dom.KeyboardEvent) =>
          if !ev.metaKey && !ev.ctrlKey && !ev.altKey then
            val tag = Option(ev.target).map(_.asInstanceOf[dom.Element].tagName.toLowerCase).getOrElse("")
            val editable = tag == "input" || tag == "textarea" ||
              (ev.target != null && ev.target.asInstanceOf[dom.HTMLElement].isContentEditable)
            if !editable then
              if ev.key == "Escape" then
                lightboxEl match
                  case Some(_) => closeLightbox()
                  case None    => if !modalHidden.now() then close()
              else if ev.key == "n" then
                active.foreach { _ =>
                  ev.preventDefault()
                  nextMatch()
                }
        })

        document.addEventListener("click", { (ev: dom.MouseEvent) =>
          val img = Option(ev.target).flatMap { t =>
            val el = t.asInstanceOf[dom.Element]
            Option(el.closest("img")).orElse(if el.tagName == "IMG" then Some(el) else None)
          }
          img.foreach { imgEl =>
            val src = imgEl.getAttribute("src")
            if src != null && src.contains("/assets/") && imgEl.closest(".lightbox") == null then
              ev.preventDefault()
              openLightbox(src, imgEl.getAttribute("alt"))
          }
        })
      }
    )

  def open(id: String, opts: Map[String, String] = Map.empty): Unit =
    doOpen(id, opts, "source", fromState = false)

  def openSlug(slug: String, opts: Map[String, String] = Map.empty): Unit =
    doOpen(slug, opts, "article", fromState = false)

  def close(): Unit = closeImpl(fromState = false)

  def restore(): Unit =
    UiState.open.now().flatMap(parseKey).foreach { case (kind, id) =>
      doOpen(id, Map.empty, kind, fromState = true)
    }

  private def doOpen(id: String, opts: Map[String, String], kind: String, fromState: Boolean): Unit =
    val q = opts.getOrElse("q", currentQ())
    lastQ = q

    val key = keyOf(id, kind)

    if !fromState then
      if !current.contains(key) then
        current = Some(key)
        UiState.set(("open", key))(push = true)
    else
      current = Some(key)

    if modalHidden.now() then opener = Option(document.activeElement).map(_.asInstanceOf[dom.Element])

    val newtab = Option(document.getElementById("modal-newtab")).map(_.asInstanceOf[dom.HTMLAnchorElement])
    newtab.foreach(_.href = buildUrl(id, q, full = false, fragment = false, kind))

    val contentEl = document.getElementById("modal-content")
    if contentEl != null then contentEl.innerHTML = "<p class=\"muted\">loading…</p>"

    modalHidden.set(false)
    syncNav()

    val fetchUrl = buildUrl(id, q, full = false, fragment = true, kind)
    ApiClient.fetchHtml(fetchUrl).foreach { html =>
      if contentEl != null then
        contentEl.innerHTML = html
        val box = document.querySelector("#modal .modal")
        if box != null then box.asInstanceOf[dom.HTMLElement].scrollTop = 0
        setTitle()
        initMatches(contentEl)
        Citations.process(contentEl)
        focusModal()
        wireContentLinks(contentEl)
    }

  private def wireContentLinks(contentEl: dom.Element): Unit =
    contentEl.addEventListener("click", { (ev: dom.MouseEvent) =>
      val target = ev.target.asInstanceOf[dom.Element]
      val anchor = Option(target.closest("a"))
      anchor.foreach { a =>
        val raw = Option(a.getAttribute("href")).getOrElse("")

        if raw.startsWith("/#") || raw.startsWith("#") then
          ev.preventDefault()
          val hash = raw.substring(raw.indexOf('#') + 1)
          UiState.addHash(hash)
          close()
        else
          val m = """^/(source|article)/([a-z0-9_.-]+)(?:\?(.*))?$""".r.findFirstMatchIn(raw)
          m.foreach { mm =>
            ev.preventDefault()
            val kind = mm.group(1)
            val id = mm.group(2)
            val params = new dom.URLSearchParams(Option(mm.group(3)).getOrElse(""))
            val q = Option(params.get("q")).filter(_.nonEmpty).getOrElse("")
            doOpen(id, Map("q" -> q), kind, fromState = false)
          }
      }
    })

  private def closeImpl(fromState: Boolean): Unit =
    modalHidden.set(true)
    val contentEl = document.getElementById("modal-content")
    if contentEl != null then contentEl.innerHTML = ""
    active = None
    val titleEl = document.getElementById("modal-title")
    if titleEl != null then titleEl.textContent = ""

    if !fromState && current.isDefined then
      UiState.set(("open", None: Option[String]))(push = true)

    current = None
    opener.foreach { el =>
      try el.asInstanceOf[dom.HTMLElement].focus()
      catch case _: Throwable => ()
    }
    opener = None

  private def closeFromState(): Unit = closeImpl(fromState = true)

  private def buildUrl(id: String, q: String, full: Boolean, fragment: Boolean, kind: String): String =
    val base = if kind == "article" then "/article/" else "/source/"
    val params = new dom.URLSearchParams("")
    if fragment then params.set("fragment", "true")
    if full then params.set("full", "true")
    if q.nonEmpty then params.set("q", q)
    val qs = params.toString
    base + js.URIUtils.encodeURIComponent(id) + (if qs.nonEmpty then "?" + qs else "")

  private def keyOf(id: String, kind: String): String =
    s"${if kind == "article" then "article" else "source"}:$id"

  private def parseKey(k: String): Option[(String, String)] =
    val m = "^(source|article):(.+)$".r.findFirstMatchIn(k)
    m.map(mm => (mm.group(1), mm.group(2)))

  private def currentQ(): String =
    UiState.q.now().getOrElse(lastQ)

  private def setTitle(): Unit =
    val titleEl = document.getElementById("modal-title")
    val contentEl = document.getElementById("modal-content")
    if titleEl != null && contentEl != null then
      val h = contentEl.querySelector("h1, h2")
      titleEl.textContent = if h != null then h.textContent.trim else "source"

  private def syncNav(): Unit =
    val back = document.getElementById("modal-back")
    val fwd = document.getElementById("modal-fwd")
    if back != null then
      back.asInstanceOf[dom.HTMLButtonElement].disabled = UiState.currentSeq < 1
    if fwd != null then
      fwd.asInstanceOf[dom.HTMLButtonElement].disabled = UiState.currentSeq >= UiState.maxSeq

  private def focusModal(): Unit =
    val box = document.querySelector("#modal .modal")
    if box != null then box.asInstanceOf[dom.HTMLElement].focus()

  private def trapFocus(ev: dom.KeyboardEvent): Unit =
    val box = document.querySelector("#modal .modal")
    if box == null then { ev.preventDefault(); return }
    val focusable = "a[href], button:not([disabled]), input, select, textarea, [tabindex]:not([tabindex=\"-1\"])"
    val items = box.querySelectorAll(focusable)
    val visible = (0 until items.length).map(items(_).asInstanceOf[dom.HTMLElement]).filter(_.offsetParent != null)
    if visible.isEmpty then
      ev.preventDefault()
      box.asInstanceOf[dom.HTMLElement].focus()
    else
      val first = visible.head
      val last = visible.last
      val active = document.activeElement
      if ev.shiftKey && (active == first || active == box) then
        ev.preventDefault()
        last.focus()
      else if !ev.shiftKey && active == last then
        ev.preventDefault()
        first.focus()

  private def initMatches(root: dom.Element): Unit =
    val marks = root.querySelectorAll("mark[id^=\"hl\"]")
    val markList = (0 until marks.length).map(marks(_).asInstanceOf[dom.Element]).toList
    val nav = Option(root.querySelector(".match-nav"))
    active = if markList.nonEmpty then Some(MatchState(markList, nav, -1)) else None
    nav.foreach { n =>
      n.addEventListener("click", { (ev: dom.MouseEvent) =>
        val target = ev.target.asInstanceOf[dom.Element]
        if target.closest("a.next-match") != null then
          ev.preventDefault()
          nextMatch()
      })
    }

  private def nextMatch(): Unit =
    active.foreach { st =>
      if st.marks.nonEmpty then
        if st.idx >= 0 && st.idx < st.marks.length then
          val prev = st.marks(st.idx).asInstanceOf[dom.HTMLElement]
          prev.classList.remove("hl-current")
          prev.style.outline = ""
        st.idx = (st.idx + 1) % st.marks.length
        val el = st.marks(st.idx).asInstanceOf[dom.HTMLElement]
        el.classList.add("hl-current")
        el.style.outline = "2px solid currentColor"
        el.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "center", behavior = "smooth"))
    }

  private def openLightbox(src: String, alt: String): Unit =
    closeLightbox()
    val box = document.createElement("div")
    box.asInstanceOf[dom.HTMLElement].className = "lightbox"
    box.setAttribute("role", "dialog")
    box.setAttribute("aria-label", if alt != null && alt.nonEmpty then alt else "figure")
    box.asInstanceOf[dom.HTMLElement].style.cssText =
      "position:fixed;inset:0;z-index:9999;display:flex;align-items:center;" +
      "justify-content:center;background:rgba(0,0,0,.82);cursor:zoom-out;padding:2rem"
    val img = document.createElement("img").asInstanceOf[dom.HTMLImageElement]
    img.src = src
    img.alt = if alt != null then alt else ""
    img.style.cssText = "max-width:100%;max-height:100%;box-shadow:0 2px 24px rgba(0,0,0,.6)"
    box.appendChild(img)
    box.addEventListener("click", (_: dom.MouseEvent) => closeLightbox())
    document.body.appendChild(box)
    lightboxEl = Some(box)

  private def closeLightbox(): Unit =
    lightboxEl.foreach(_.remove())
    lightboxEl = None
