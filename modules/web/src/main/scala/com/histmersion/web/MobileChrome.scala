package com.histmersion.web

import com.histmersion.core.UiConfig
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The narrow (phone) layout's own controls. There the page is the map; everything else is shown on
  * demand:
  *   - the app bar: ☰ filters, the search box, ⏱ the timeline, and the map & tools drawer;
  *   - the FILTERS drawer (the filter bar, restyled) slides in from the left — ☰, or a swipe in from
  *     the left edge;
  *   - the MAP & TOOLS drawer (the layer switches, the base map, the tools) slides in from the right —
  *     its button, or a swipe in from the right edge;
  *   - a drawer closes on a swipe back, a tap on the dimmed map, Escape, or when a tool is chosen;
  *   - the timeline folds away under the bar until ⏱.
  * All of it is hidden on a wide screen by the stylesheet; its state is this object's alone (not in
  * the URL — a shared link opens on the map). */
object MobileChrome:

  /** A drawer: which edge it comes from. */
  enum Drawer(val edge: Edge):
    case Filters extends Drawer(Edge.Left)
    case MapTools extends Drawer(Edge.Right)

  enum Edge:
    case Left, Right

  // ── pure ──────────────────────────────────────────────────────────────────────────────────────

  /** How far a finger must travel sideways for a swipe, and how much more sideways than up or down. */
  val SwipeMinPx = 40.0
  val SwipeRatio = 1.5

  /** Is a gesture of (`dx`, `dy`) a horizontal swipe TOWARDS the middle from `edge` (so, opening)? */
  def swipesIn(edge: Edge, dx: Double, dy: Double): Boolean =
    horizontal(dx, dy) && (edge match
      case Edge.Left  => dx > 0
      case Edge.Right => dx < 0)

  /** …and back out towards `edge` (so, closing the drawer that came from it)? */
  def swipesOut(edge: Edge, dx: Double, dy: Double): Boolean =
    horizontal(dx, dy) && (edge match
      case Edge.Left  => dx < 0
      case Edge.Right => dx > 0)

  private def horizontal(dx: Double, dy: Double) =
    math.abs(dx) >= SwipeMinPx && math.abs(dx) > SwipeRatio * math.abs(dy)

  /** The filters that are set but not visible in the bar (the search is): the ☰ badge. */
  def hiddenFilterCount(chips: List[UiState.ActiveChip]): Int = chips.count(_.key != UiState.q.key)

  // ── state ─────────────────────────────────────────────────────────────────────────────────────

  /** The open drawer — one at a time. */
  val drawer: Var[Option[Drawer]] = Var(None)
  /** Whether the timeline is unfolded under the bar. */
  val timelineOpen: Var[Boolean] = Var(false)

  def open(d: Drawer): Unit = drawer.set(Some(d))
  def close(): Unit         = drawer.set(None)
  def toggle(d: Drawer): Unit = drawer.update(cur => if cur.contains(d) then None else Some(d))

  /** Root classes for `.histmersion-app`: which drawer is open, whether the timeline is. */
  def rootClasses: Modifier[HtmlElement] = List(
    cls("drawer-filters") <-- drawer.signal.map(_.contains(Drawer.Filters)),
    cls("drawer-maptools") <-- drawer.signal.map(_.contains(Drawer.MapTools)),
    cls("timeline-open") <-- timelineOpen.signal
  )

  // ── view ──────────────────────────────────────────────────────────────────────────────────────

  def bar(config: UiConfig): HtmlElement =
    val hidden = UiState.signal.map(_ => hiddenFilterCount(UiState.activeChips()))
    div(
      cls := "mobile-bar",
      button(typ := "button", cls := "mb-btn mb-filters", aria.label := "filters",
        aria.expanded <-- drawer.signal.map(_.contains(Drawer.Filters)),
        title := "filters",
        "☰",
        child.maybe <-- hidden.map(n => Option.when(n > 0)(span(cls := "mb-badge", n.toString))),
        onClick --> (_ => toggle(Drawer.Filters))),
      input(
        typ := "search",
        cls := "mb-search",
        placeholder := "search…",
        aria.label := "search",
        // the same state as the filter bar's box: typing in either is typing in both
        controlled(
          value <-- UiState.q.signal.map(_.getOrElse("")),
          onInput.mapToValue --> { v => UiState.set(UiState.q := Some(v.trim).filter(_.nonEmpty))() }
        ),
        onKeyDown --> { e => if e.key == "Enter" then e.target.asInstanceOf[dom.HTMLElement].blur() }
      ),
      button(typ := "button", cls := "mb-btn mb-timeline",
        aria.label := "timeline", aria.expanded <-- timelineOpen.signal,
        title <-- timelineOpen.signal.map(o => if o then "hide the timeline" else "show the timeline"),
        cls("active") <-- timelineOpen.signal,
        span(cls := "mb-span", child.text <-- spanLabel(config)),
        onClick --> (_ => timelineOpen.update(!_))),
      button(typ := "button", cls := "mb-btn mb-maptools", aria.label := "map layers and tools",
        title := "map layers and tools",
        aria.expanded <-- drawer.signal.map(_.contains(Drawer.MapTools)),
        Icon(Icon.Layers),
        onClick --> (_ => toggle(Drawer.MapTools)))
    )

  /** The span the map shows, short: `3500 BCE–500 BCE`. With the timeline folded, this is what says
    * which time the map is at. */
  private def spanLabel(config: UiConfig): Signal[String] =
    UiState.from.signal.combineWith(UiState.to.signal).map { (a, b) =>
      Fmt.periodStr(a.getOrElse(config.time.defaultFrom), b.getOrElse(config.time.defaultTo))
    }

  /** A drawer's heading: its name and a close button. */
  def drawerHead(name: String): HtmlElement =
    div(cls := "drawer-head",
      span(cls := "drawer-title", name),
      button(typ := "button", cls := "wm-btn drawer-close", aria.label := s"close $name", "✕",
        onClick --> (_ => close())))

  /** The dimmed map behind an open drawer: a tap closes it. */
  def scrim: HtmlElement =
    div(cls := "drawer-scrim", cls("shown") <-- drawer.signal.map(_.isDefined), onClick --> (_ => close()))

  /** Thin strips along the left and right edges of the map that catch a swipe in, so the map does
    * not pan instead. */
  def edges: List[HtmlElement] = List(edgeStrip(Edge.Left, Drawer.Filters), edgeStrip(Edge.Right, Drawer.MapTools))

  private def edgeStrip(edge: Edge, d: Drawer): HtmlElement =
    // `var`s: where the gesture began
    var x0 = 0.0
    var y0 = 0.0
    div(
      cls := s"edge-grab edge-${edge.toString.toLowerCase}",
      aria.hidden := true,
      onPointerDown --> { e =>
        x0 = e.clientX; y0 = e.clientY
        e.target.asInstanceOf[dom.HTMLElement].setPointerCapture(e.pointerId)
      },
      onPointerUp --> { e => if swipesIn(edge, e.clientX - x0, e.clientY - y0) then open(d) }
    )

  /** A swipe on an open drawer towards its edge closes it; Escape closes it too. Installed once. */
  def install(): Unit =
    // `var`s: the gesture in progress, and the drawer it began on
    var x0 = 0.0
    var y0 = 0.0
    var on = Option.empty[Drawer]
    def drawerAt(t: dom.EventTarget): Option[Drawer] = t match
      case el: dom.Element =>
        if el.closest(".fbar") != null then Some(Drawer.Filters)
        else if el.closest(".viewport-toggle") != null then Some(Drawer.MapTools)
        else None
      case _ => None
    dom.document.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
      on = if Viewport.narrowNow then drawerAt(e.target).filter(drawer.now().contains) else None
      x0 = e.clientX; y0 = e.clientY
    })
    dom.document.addEventListener("pointerup", { (e: dom.PointerEvent) =>
      on.foreach(d => if swipesOut(d.edge, e.clientX - x0, e.clientY - y0) then close())
      on = None
    })
    dom.document.addEventListener("keydown", { (e: dom.KeyboardEvent) =>
      if e.key == "Escape" && drawer.now().isDefined then close()
    })
