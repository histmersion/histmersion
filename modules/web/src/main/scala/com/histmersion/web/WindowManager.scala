package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom
import org.scalajs.dom.document

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object WindowManager:
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlParamName = UrlParamName.T
  object UrlParamName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlParamText = UrlParamText.T
  object UrlParamText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WireLinksWid = _root_.com.histmersion.web.WindowManager.Stacked.Id
  val WireLinksWid: _root_.com.histmersion.web.WindowManager.Stacked.Id.type = _root_.com.histmersion.web.WindowManager.Stacked.Id
  type BuildUrlFragment = BuildUrlFragment.T
  object BuildUrlFragment extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CloseWinReturnFocus = CloseWinReturnFocus.T
  object CloseWinReturnFocus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeIsOursKey = EscapeIsOursKey.T
  object EscapeIsOursKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeIsOursModified = EscapeIsOursModified.T
  object EscapeIsOursModified extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeIsOursTaken = EscapeIsOursTaken.T
  object EscapeIsOursTaken extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeIsOursTyping = EscapeIsOursTyping.T
  object EscapeIsOursTyping extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeIsOursDropdownOpen = EscapeIsOursDropdownOpen.T
  object EscapeIsOursDropdownOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private case class WinState(
      id: WinState.Id,
      key: WindowKey,
      title: WinState.Title,
      x: WinState.X,
      y: WinState.Y,
      w: WinState.W,
      h: WinState.H,
      z: WinState.Z,
      minimized: WinState.Minimized
  )

  object WinState:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type X = X.T
    object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Y = Y.T
    object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type W = W.T
    object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type H = H.T
    object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Z = Z.T
    object Z extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Minimized = Minimized.T
    object Minimized extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val windows = Var[List[WinState]](Nil)

  /** Which key each open window shows, updated AT ONCE. `windows` is a Var, and inside an Airstream transaction (an
    * event handler, a state observer) its update is only scheduled: two opens of the same key in one transaction both
    * saw "not open" and drew the window twice. The "is it open?" checks read this instead; `windows` stays what is
    * rendered.
    */
  private var shown = Map.empty[WindowKey, Int]

  /** What each window was opened FROM — the element focused at the time — so closing it from the keyboard (or its ✕)
    * gives the focus back there instead of dropping it on the page. Updated at once, like `shown`.
    */
  private var openers = Map.empty[Int, dom.HTMLElement]
  private var nextId = 1
  private var nextZ = 100
  private var dragWin = Option.empty[Int]
  private var dragOx = 0.0
  private var dragOy = 0.0
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
                cls := "btn btn--sm wm-taskbar-btn",
                w.title,
                onClick --> { _ => restore(w.id) }
              )
            }
          )
      }
    )

  /** Open `key`'s window — or, for a window of the old toolbar, show the page that took it over. */
  def open(key: WindowKey): Unit =
    Route.ofWindow(key) match
      case Some(page) => Router.go(page)
      case None       => openWindow(key)

  private def openWindow(key: WindowKey): Unit =
    shown.get(key) match
      case Some(wid) =>
        focus(wid)
        if windows.now().find(_.id == wid).exists(_.minimized) then restore(wid)
      case None =>
        val wid = nextId; nextId += 1
        nextZ += 1
        val vw = dom.window.innerWidth
        val vh = dom.window.innerHeight
        // the merge review screen is a screen: three columns of a source pair, a diff3 view
        val (w0, h0) = key match
          case WindowKey.Merge =>
            (math.min(Layout.mergeWindowMaxWidth, vw * 0.94), math.min(vh * 0.88, Layout.mergeWindowMaxHeight))
          case _ => (math.min(Layout.windowMaxWidth, vw * 0.7), math.min(vh * 0.75, Layout.windowMaxHeight))
        val offset = ((wid - 1) % Layout.windowCascadeSlots) * Layout.windowCascadeOffset
        // centred over what the results column leaves free (on a wide screen), so a pinned item never covers
        // the column it was pinned from
        val beside = if Viewport.narrowNow || key == WindowKey.Merge then 0.0 else ResultsPanel.width.now().toDouble
        val x0 =
          math.max(if key == WindowKey.Merge then 8 else 20, math.min((vw - beside - w0) / 2 + offset, vw - w0 - 8))
        val y0 = math.max(20, (if key == WindowKey.Merge then 30 else 60) + offset)
        val ws = WinState(wid, key, key.initialTitle, x0, y0, w0, h0, nextZ, minimized = false)
        shown += key -> wid
        dom.document.activeElement match
          case el: dom.HTMLElement if el != dom.document.body => openers += wid -> el
          case _                                              => ()
        windows.update(_ :+ ws)
        // the open windows are UI state: in the URL, so a reload or a shared link reopens them
        if !UiState.open.now().contains(key) then UiState.set(UiState.open := UiState.open.now() :+ key)()
        dom.window.setTimeout(() => loadContent(wid, key), 0)

  /** The windows follow [[UiState.open]], not just at start-up: back/forward or a hash link that changes it opens and
    * closes windows to match. Reconciling is idempotent — opening a key that is already open or closing one that is
    * already gone writes nothing back — so this cannot loop.
    */
  def followState(): Unit =
    val _ = UiState.open.signal.foreach(sync)(using unsafeWindowOwner)

  private def sync(keys: List[WindowKey]): Unit =
    // a toolbar window named in the hash is a page now: the router goes there (`Route.legacy`)
    val (toClose, toOpen) = reconcile(shown.keys.toList, keys.filter(Route.ofWindow(_).isEmpty))
    toClose.flatMap(shown.get).foreach(closeWin(_))
    toOpen.foreach(openWindow)

  /** Which open windows to close and which keys to open so the windows match `wanted`. Pure. */
  private[web] def reconcile(open: List[WindowKey], wanted: List[WindowKey]): (List[WindowKey], List[WindowKey]) =
    (open.filterNot(wanted.contains), wanted.filterNot(open.contains).distinct)

  /** The title of `key`'s window, once its content knows a better one than [[WindowKey.initialTitle]]: an item's own
    * title, never its identifier (which the title's tooltip keeps).
    */
  def retitle(key: WindowKey, title: com.histmersion.web.WindowManager.WinState.Title): Unit =
    Route.ofItem(key).foreach(Router.retitle(_, title))
    shown
      .get(key)
      .foreach(wid =>
        windows.update(_.map(w => if w.id == wid && w.title != title then w.modify(_.title).setTo(title) else w))
      )

  /** Close the window showing `key`, if one is open. */
  def close(key: WindowKey): Unit = shown.get(key).foreach(closeWin(_))

  /** Fetch a page window's content again — after an edit changed what it shows. */
  def reload(key: WindowKey): Unit = windows.now().find(_.key == key).foreach(w => loadContent(w.id, key))

  def closeAll(): Unit =
    shown = Map.empty
    openers = Map.empty
    windows.set(Nil)
    UiState.set(UiState.open := Nil)()

  /** Only the server-rendered pages load here; every other window's content is a Laminar component (see
    * [[renderWindow]]) that mounts and unmounts with its window.
    */
  private def loadContent(wid: com.histmersion.web.WindowManager.Stacked.Id, key: WindowKey, extraParams: Map[com.histmersion.web.WindowManager.UrlParamName, com.histmersion.web.WindowManager.UrlParamText] = Map.empty): Unit =
    key match
      case WindowKey.Source(_) | WindowKey.Article(_) =>
        val id = key.subject.getOrElse("")
        val fetchUrl = buildUrl(key, fragment = true, extraParams)
        ApiClient.fetchHtml(fetchUrl).foreach { html =>
          val contentEl = document.getElementById(s"wm-content-$wid")
          if contentEl != null then
            contentEl.innerHTML = html
            val titleEl = contentEl.querySelector("h1, h2")
            val title = if titleEl != null then titleEl.textContent.trim else key.initialTitle
            windows.update(_.map(w => if w.id == wid then w.modify(_.title).setTo(title) else w))
            // both handlers are delegated on the content root, which outlives its content: wire it once.
            // Every reload (a link to the same page inside it, an edit's refresh) added two more, and a
            // click then ran each of them — N fetches of the page for one click (#1949)
            if !contentEl.hasAttribute(WiredAttr) then
              contentEl.setAttribute(WiredAttr, "")
              wireLinks(wid, contentEl)
              InPage.wire(contentEl)
        }
      case _ => ()

  /** Marks a window's content root whose delegated click handlers are on. */
  private val WiredAttr = "data-wired"

  /** The web UI's own filter links (`/#tag=x`, a chip on a source page) — pure: the hash to MERGE, or `None` for any
    * other href.
    */
  private[web] def filterHash(href: com.histmersion.web.WindowManager.Href): Option[com.histmersion.web.UiState.UrlHash] =
    Option(href)
      .map(_.trim)
      .filter(h => (h.startsWith("/#") || h.startsWith("#")) && h.contains("="))
      .map(_.dropWhile(_ != '#'))

  /** In-window links open in the page — another window, a filter added — on a plain click only: with ⌘/Ctrl/Shift/Alt
    * (or the middle button, which sends no click) the browser opens the link's address in a new tab or window, as
    * anywhere else.
    */
  private def wireLinks(wid: com.histmersion.web.WindowManager.WireLinksWid, root: dom.Element): Unit =
    root.addEventListener(
      "click",
      { (ev: dom.MouseEvent) =>
        val anchor = Option(ev.target.asInstanceOf[dom.Element].closest("a")).filter(_ => Router.plain(ev))
        anchor.foreach { a =>
          val raw = Option(a.getAttribute("href")).getOrElse("")
          // following `/#tag=x` NAVIGATED the hash, and the popstate that followed replaced every
          // filter with that one tag; merging it keeps the reader's other filters
          filterHash(raw).foreach { h =>
            ev.preventDefault()
            UiState.addHash(h)
          }
          val m = """^/(source|article)/([a-z0-9_.-]+)(?:\?(.*))?$""".r.findFirstMatchIn(raw)
          m.foreach { mm =>
            ev.preventDefault()
            val linkId = mm.group(2)
            val target = if mm.group(1) == "article" then WindowKey.Article(linkId) else WindowKey.Source(linkId)
            val qs = Option(mm.group(3)).getOrElse("")
            val params = new dom.URLSearchParams(qs)
            val paramsMap = scala.collection.mutable.Map.empty[String, String]
            params.forEach { (value, key) => paramsMap(key) = value }
            if windows.now().find(_.id == wid).exists(_.key == target) then loadContent(wid, target, paramsMap.toMap)
            else open(target)
          }
          // a page of the web UI (a run, a goal, the backlog): its window, or the page, without a reload
          if m.isEmpty && !ev.defaultPrevented then
            Route.parse(raw.takeWhile(c => c != '?' && c != '#')).filterNot(_.serverPage).foreach { r =>
              ev.preventDefault()
              Route.window(r).fold(Router.goHref(raw.takeWhile(_ != '#')))(open)
            }
        }
      }
    )

  private def buildUrl(key: WindowKey, fragment: com.histmersion.web.WindowManager.BuildUrlFragment, extra: Map[com.histmersion.web.WindowManager.UrlParamName, com.histmersion.web.WindowManager.UrlParamText] = Map.empty): com.histmersion.web.WindowManager.Href =
    val base = key.pageHref.getOrElse("/")
    val params = new dom.URLSearchParams("")
    if fragment then params.set("fragment", "true")
    extra.foreach((k, v) => if k != "fragment" then params.set(k, v))
    val qs = params.toString
    base + (if qs.nonEmpty then "?" + qs else "")

  /** The actions of a source window: its citation graph, and the metadata editor — which asks for the API key first
    * when the server wants one and none is held.
    */
  private def sourceActions(ck: com.histmersion.web.WindowKey.Source.Citekey): HtmlElement =
    div(
      cls := "wm-actions",
      button(
        typ := "button",
        cls := "btn btn--ghost btn--sm wm-graph-btn",
        title := "Citation graph",
        Icon(Icon.Graph),
        span("Graph"),
        onClick --> (_ => open(WindowKey.Graph(ck)))
      ),
      WriteButton(Icon.Edit, "Edit", "Edit this source's metadata", () => open(WindowKey.EditSource(ck)))
    )

  private def focus(wid: com.histmersion.web.WindowManager.Stacked.Id): Unit =
    nextZ += 1
    windows.update(_.map(w => if w.id == wid then w.modify(_.z).setTo(nextZ) else w))

  private def restore(wid: com.histmersion.web.WindowManager.Stacked.Id): Unit =
    nextZ += 1
    windows.update(_.map(w => if w.id == wid then w.modify(_.minimized).setTo(false).modify(_.z).setTo(nextZ) else w))

  private def minimize(wid: com.histmersion.web.WindowManager.Stacked.Id): Unit =
    windows.update(_.map(w => if w.id == wid then w.modify(_.minimized).setTo(true) else w))

  /** Close one window. `returnFocus` when a person closed it (Escape, its ✕): the focus goes back to what opened it, if
    * that is still on the page and not inside the window being closed.
    */
  private def closeWin(wid: com.histmersion.web.WindowManager.Stacked.Id, returnFocus: com.histmersion.web.WindowManager.CloseWinReturnFocus = false): Unit =
    val key = shown.collectFirst { case (k, id) if id == wid => k }
    key.foreach(k => shown -= k)
    val opener = openers.get(wid)
    openers -= wid
    windows.update(_.filterNot(_.id == wid))
    key.foreach(k => UiState.set(UiState.open := UiState.open.now().filterNot(_ == k))())
    if returnFocus then opener.filter(el => el.isConnected && el.closest(s"#wm-$wid") == null).foreach(_.focus())

  // ── Escape ──────────────────────────────────────────────────────────────────────────────────────

  /** An open window as the Escape key sees it. */
  final private[web] case class Stacked(id: Stacked.Id, z: Stacked.Z, minimized: Stacked.Minimized)

  object Stacked:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Z = Z.T
    object Z extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Minimized = Minimized.T
    object Minimized extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The window Escape closes: the one holding the focus, else the topmost one showing; never a minimized one. Pure.
    */
  private[web] def escapeTarget(ws: List[Stacked], focused: Option[com.histmersion.web.WindowManager.Stacked.Id]): Option[com.histmersion.web.WindowManager.Stacked.Id] =
    val showing = ws.filterNot(_.minimized)
    focused.filter(f => showing.exists(_.id == f)).orElse(showing.maxByOption(_.z).map(_.id))

  /** Whether an Escape press is the window manager's: a bare Escape that nothing took first (an open dropdown, the
    * lightbox, a drawer call `preventDefault`), not typed into a field, and with no suggestion list open. Pure.
    */
  private[web] def escapeIsOurs(
      key: com.histmersion.web.WindowManager.EscapeIsOursKey,
      modified: com.histmersion.web.WindowManager.EscapeIsOursModified,
      taken: com.histmersion.web.WindowManager.EscapeIsOursTaken,
      typing: com.histmersion.web.WindowManager.EscapeIsOursTyping,
      dropdownOpen: com.histmersion.web.WindowManager.EscapeIsOursDropdownOpen
  ): Boolean =
    key == "Escape" && !modified && !taken && !typing && !dropdownOpen

  /** Input types a person types into; a checkbox, a radio button or a button is not typing. */
  private val TextInputs = Set(
    "text",
    "search",
    "email",
    "url",
    "tel",
    "password",
    "number",
    "date",
    "datetime-local",
    "month",
    "week",
    "time",
    ""
  )

  /** Is the key going into a text field (an input one types into, a textarea, a select, an editable element)? Escape
    * there belongs to the field.
    */
  private[web] def typingIn(target: dom.EventTarget): Boolean = target match
    case i: dom.HTMLInputElement => TextInputs(Option(i.getAttribute("type")).getOrElse("").toLowerCase)
    case _: dom.HTMLTextAreaElement | _: dom.HTMLSelectElement => true
    case el: dom.HTMLElement                                   => el.isContentEditable
    case _                                                     => false

  /** A suggestion list is open somewhere (an autocomplete says so with `aria-expanded`). */
  private def dropdownOpen: Boolean =
    dom.document.querySelector("[role=combobox][aria-expanded=true]") != null

  /** Escape closes the topmost (focused) window and gives the focus back to what opened it — the URL follows, as for
    * every close ([[UiState.open]]). Installed once, on `window`: every document-level handler (a dropdown, the
    * lightbox, a drawer, the period presets) has had the key first and claims it with `preventDefault`.
    */
  def installKeys(): Unit =
    dom.window.addEventListener(
      "keydown",
      { (e: dom.KeyboardEvent) =>
        val modified = e.metaKey || e.ctrlKey || e.altKey || e.shiftKey
        if escapeIsOurs(e.key, modified, e.defaultPrevented, typingIn(e.target), dropdownOpen) then
          val focused = Option(dom.document.activeElement)
            .flatMap(el => Option(el.closest(".wm-window")))
            .flatMap(_.id.stripPrefix("wm-").toIntOption)
          escapeTarget(windows.now().map(w => Stacked(w.id, w.z, w.minimized)), focused).foreach { wid =>
            e.preventDefault()
            closeWin(wid, returnFocus = true)
          }
      }
    )

  private def renderWindow(initial: WinState, wSignal: Signal[WinState]): HtmlElement =
    val wid = initial.id
    div(
      cls := "wm-window",
      idAttr := s"wm-$wid",
      cls("minimized") <-- wSignal.map(_.minimized),
      // where the window is and how big: data (dragged, resized, stacked) for `.wm-window` to use
      styleAttr <-- wSignal.map(w => s"--wx:${w.x}px;--wy:${w.y}px;--ww:${w.w}px;--wh:${w.h}px;--wz:${w.z}"),
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
            windows.update(_.map(ww => if ww.id == wid then ww.modify(_.x).setTo(nx).modify(_.y).setTo(ny) else ww))
          }
        },
        onPointerUp --> { ev =>
          if dragWin.contains(wid) then
            dragWin = None
            ev.target.asInstanceOf[dom.HTMLElement].releasePointerCapture(ev.pointerId)
        },
        span(cls := "wm-title", title := initial.key.wire, child.text <-- wSignal.map(_.title)),
        div(
          cls := "wm-buttons",
          button(
            typ := "button",
            cls := "btn btn--bare btn--icon btn--sm wm-btn",
            "−",
            title := "Minimize",
            onClick.stopPropagation --> { _ => minimize(wid) }
          ),
          initial.key.pageHref.map(h =>
            a(
              cls := "btn btn--bare btn--icon btn--sm wm-btn wm-newtab",
              href := h,
              target := "_blank",
              "↗",
              title := "Open in new tab"
            )
          ),
          button(
            typ := "button",
            cls := "btn btn--bare btn--icon btn--sm wm-btn wm-close",
            "✕",
            title := "Close (Escape)",
            onClick.stopPropagation --> { _ => closeWin(wid, returnFocus = true) }
          )
        )
      ),
      initial.key match
        case WindowKey.Source(ck)    => sourceActions(ck)
        case WindowKey.Article(slug) => ManagersView.articleActions(slug)
        case _                       => emptyNode,
      initial.key match
        case WindowKey.Graph(ck) =>
          div(cls := "wm-content wm-graph", idAttr := s"wm-content-$wid", GraphPanel.element(ck))
        case WindowKey.EditSource(ck) => div(cls := "wm-content", idAttr := s"wm-content-$wid", SourceEditor(ck))
        // the old toolbar's windows are pages now (`open` goes there); single items open as windows, and the
        // key's prompt
        case WindowKey.ApiKey      => div(cls := "wm-content", idAttr := s"wm-content-$wid", Auth.keyPane)
        case WindowKey.Run(id)     => div(cls := "wm-content", idAttr := s"wm-content-$wid", RunsView.runPane(id))
        case WindowKey.Goal(slug)  => div(cls := "wm-content", idAttr := s"wm-content-$wid", GoalsView.goalPane(slug))
        case WindowKey.Manager(id) =>
          div(cls := "wm-content", idAttr := s"wm-content-$wid", ManagersView.managerPane(id))
        case _ => div(cls := "wm-content modal-content", idAttr := s"wm-content-$wid"),
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
            windows.update(_.map(ww => if ww.id == wid then ww.modify(_.w).setTo(nw).modify(_.h).setTo(nh) else ww))
          }
        },
        onPointerUp --> { ev =>
          if resizeWin.contains(wid) then
            resizeWin = None
            ev.target.asInstanceOf[dom.HTMLElement].releasePointerCapture(ev.pointerId)
        }
      )
    )
