package com.histmersion
package web

import com.histmersion.api.Wording
import com.histmersion.web.ui.{Layout, SidebarTab}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.instance.UiConfig
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels
import com.histmersion.api.RowCount
import com.histmersion.lint.{tolerated, LintRule}

/** The results on Explore's right: "On the map" (what the map's view holds) or "Everywhere", the tabs — sources,
  * articles, events, places, pictures, each with its count —, the sort, and the rows, every one a real link (a plain
  * click shows its detail here, a modifier click or ↗ opens its page in a tab). An item chosen — in a list or on the
  * map — replaces the results with its detail ([[ResultDetail]]) until "← Results". Its width is dragged; on a phone it
  * is the bottom sheet.
  */
object ResultsPanel:
  enum KeepWidth derives CanEqual:
    case Keep, Reset
  object KeepWidth:
    extension (keepWidth: KeepWidth) def keeps: Boolean = keepWidth == Keep

  /** The width, px: the one this browser kept, else the default. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val width: Var[ResultsWidth.Width] = Var(
    ResultsWidth.clamp(ResultsWidth.stored.getOrElse(ResultsWidth.Width(Layout.sidebarDefault.value)), viewport)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def viewport: ResultsWidth.ViewportWidth =
    ResultsWidth.ViewportWidth(scala.util.Try(dom.window.innerWidth).getOrElse(Double.MaxValue))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def resizeTo(w: ResultsWidth.Width, keep: KeepWidth): Unit =
    width.set(ResultsWidth.clamp(w, viewport))
    if keep.keeps then ResultsWidth.keep(width.now())

  /** The item whose detail replaces the results. */
  val selected: Var[Option[Preview.Target]] = Var(None)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def show(t: Preview.Target): Unit =
    selected.set(Some(t))
    // on a phone the list is a sheet at the bottom: the detail must be where the reader looks
    if Viewport.narrowNow && UiState.sheet.now() == Sheet.Peek then UiState.set(UiState.sheet := Sheet.Half)()

  def back(): Unit = selected.set(None)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def activeTab: SidebarTab = SidebarTab.of(UiState.tab.now().map(SidebarTab.Key(_)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def activeTabSignal: Signal[SidebarTab] = UiState.tab.signal.map(k => SidebarTab.of(k.map(SidebarTab.Key(_))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def setActiveTab(t: SidebarTab): Unit =
    selected.set(None)
    UiState.set(UiState.tab := Some(t.key.value))(push = UiState.SetPush.Push)

  private val sorts = List("relevance", "cited", "period", "year", "title")

  /** Pure: a tab's label with its count, when known. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tabLabel(label: UiLabels.Text, count: Option[RowCount]): UiLabels.Text =
    count.fold(label)(n => UiLabels.Text(s"$label $n"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def counts(t: SidebarTab): Signal[Option[RowCount]] = t match
    case SidebarTab.Sources  => ResultsList.total.signal
    case SidebarTab.Articles => ArticlesTab.total
    case SidebarTab.Events   => EventsTab.total
    case SidebarTab.Places   => MapLists.places.signal.map(s => Some(RowCount(s.total.value)))
    case SidebarTab.Pictures => MapLists.pictures.signal.map(s => Some(RowCount(s.total.value)))

  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def scope: HtmlElement =
    div(
      cls := "btn-group results-scope",
      role := "group",
      aria.label := "Which results",
      button(
        typ := "button",
        cls := "btn btn--toggle btn--sm",
        idAttr := "results-on-map",
        aria.pressed <-- UiState.inView.signal.map(_.toString),
        "On the map",
        title := "Only what lies in the map's view",
        onClick --> (_ => ViewportToggle.setInView(true))
      ),
      button(
        typ := "button",
        cls := "btn btn--toggle btn--sm",
        idAttr := "results-everywhere",
        aria.pressed <-- UiState.inView.signal.map(v => (!v).toString),
        "Everywhere",
        title := "Wherever it is",
        onClick --> (_ => ViewportToggle.setInView(false))
      )
    )

  private def sortSelect: HtmlElement =
    label(
      cls := "results-sort",
      cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Sources),
      span(cls := "muted", "Sort by "),
      select(
        cls := "field",
        idAttr := "f-sort",
        controlled(
          value <-- UiState.sort.signal.map(_.filter(sorts.contains).getOrElse(sorts.head)),
          onChange.mapToValue --> { v => UiState.set(UiState.sort := Some(v))() }
        ),
        sorts.map(v => option(value := v, Wording.capitalize(v)))
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def element(ui: UiConfig): HtmlElement =
    val detail = selected.signal.map(_.isDefined)
    div(
      cls := "sidebar results-panel",
      // the bottom sheet's height in the narrow layout (the classes do nothing on a wide screen)
      cls <-- UiState.sheet.signal.map(s => s"sheet-${s.wire}"),
      sheetHandle,
      div(
        cls := "sidebar-resize",
        role := "separator",
        tabIndex := 0,
        aria.orientation := "vertical",
        aria.label := "Width of the results (← wider, → narrower)",
        aria.valueMin := ResultsWidth.min.value.toDouble,
        aria.valueMax <-- width.signal.map(_ =>
          ResultsWidth.max(ResultsWidth.ViewportWidth(dom.window.innerWidth)).value.toDouble
        ),
        aria.valueNow <-- width.signal.map(_.value.toDouble),
        title := "Drag, or use ← and →, to change the width of the results",
        onKeyDown --> { ev =>
          ResultsWidth
            .step(
              width.now(),
              ResultsWidth.KeyName(ev.key),
              (if ev.shiftKey then ResultsWidth.ShiftHeld.Held else ResultsWidth.ShiftHeld.Released),
              ResultsWidth.StepViewport(dom.window.innerWidth)
            )
            .foreach { w =>
              ev.preventDefault()
              resizeTo(w, keep = KeepWidth.Keep)
            }
        },
        onPointerDown --> { ev =>
          ev.preventDefault()
          val startX = ev.clientX
          val startW = width.now()
          val target = ev.target.asInstanceOf[dom.HTMLElement]
          target.setPointerCapture(ev.pointerId)
          val move = { (me: dom.PointerEvent) =>
            resizeTo(ResultsWidth.Width(startW.value - (me.clientX - startX).toInt), keep = KeepWidth.Reset)
          }: js.Function1[dom.PointerEvent, Unit]
          val up = { (_: dom.PointerEvent) =>
            target.removeEventListener("pointermove", move)
            ResultsWidth.keep(width.now())
          }: js.Function1[dom.PointerEvent, Unit]
          target.addEventListener("pointermove", move)
          target.addEventListener(
            "pointerup",
            up,
            js.Dynamic.literal(once = true).asInstanceOf[dom.EventListenerOptions]
          )
        }
      ),
      div(
        cls := "results-lists",
        cls("is-hidden") <-- detail,
        div(cls := "results-head", scope, sortSelect),
        titleFilter,
        div(
          cls := "tabs",
          role := "tablist",
          SidebarTab.values.toList.map { t =>
            button(
              typ := "button",
              role := "tab",
              dataAttr("tab") := t.key.value,
              cls <-- activeTabSignal.map(a => if a == t then "tab active" else "tab"),
              aria.selected <-- activeTabSignal.map(_ == t),
              span(cls := "tab-label", t.label),
              child.maybe <-- counts(t).map(_.map(n => span(cls := "tab-count", n.toString))),
              onClick --> { _ => setActiveTab(t) }
            )
          }
        ),
        div(cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Sources), ResultsList.element),
        div(cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Articles), ArticlesTab.pane),
        div(cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Events), EventsTab.pane),
        div(
          cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Places),
          MapLists.pane(MapLists.places, MapLists.PaneName("places"))
        ),
        div(
          cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Pictures),
          MapLists.pane(MapLists.pictures, MapLists.PaneName("pictures"))
        )
      ),
      child.maybe <-- selected.signal.map(_.map(ResultDetail.element))
    )

  /** The bottom sheet's grab handle (shown only in the narrow layout): a tap moves the sheet one step up (from full
    * back to the strip); a vertical drag lets go at the nearest of the three.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sheetHandle: HtmlElement =
    // `var`s: the drag in progress — where it started, and whether it moved enough to be a drag
    var startY = 0.0
    var dragged = false
    def h = dom.window.innerHeight.toDouble

    /** A length token of the stylesheet's, in px. Measured, not parsed: the tokens are `clamp()` and `calc()` of the
      * screen's size, which only layout resolves.
      */
    def px(name: String) =
      scala.util
        .Try {
          val probe = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
          probe.className = "length-probe"
          probe.style.setProperty("--probe", s"var($name)")
          dom.document.body.appendChild(probe)
          val v = probe.getBoundingClientRect().height
          probe.remove()
          v
        }
        .getOrElse(0.0)
    def sheetOf(ev: dom.PointerEvent) = Option(ev.target.asInstanceOf[dom.Element].closest(".sidebar"))
      .map(_.asInstanceOf[dom.HTMLElement])
    button(
      typ := "button",
      cls := "sheet-handle",
      aria.label := "resize the list",
      title <-- UiState.sheet.signal.map(s => if s == Sheet.Full then "shrink the list" else "pull the list up"),
      span(cls := "sheet-grip"),
      onPointerDown --> { ev =>
        startY = ev.clientY
        dragged = false
        ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
      },
      onPointerMove --> { ev =>
        if ev.buttons != 0
        then // pressed: a mouse hovering over the handle is not a drag
          if math.abs(ev.clientY - startY) > 12 then dragged = true
          // the sheet follows the finger; the stylesheet uses the height while `sheet-dragging` is on
          if dragged then
            sheetOf(ev).foreach { el =>
              el.classList.add("sheet-dragging")
              el.style.setProperty("--sheet-live", s"${math.max(px("--sheet-peek"), h - ev.clientY)}px")
            }
      },
      onPointerUp --> { ev =>
        sheetOf(ev).foreach(_.classList.remove("sheet-dragging"))
        val next =
          if dragged then
            Sheet.nearest(
              Sheet.Fraction((h - ev.clientY) / h),
              Sheet.Fraction(px("--sheet-peek") / h),
              Sheet.Fraction(1 - px("--sheet-full-gap") / h)
            )
          else Sheet.next(UiState.sheet.now())
        UiState.set(UiState.sheet := next)()
      }
    )

  /** The ONE title quick-filter, above the tab strip: it narrows every tab (sources by title, articles by title, events
    * by label) through [[UiState.title]].
    */
  private def titleFilter: HtmlElement =
    div(
      cls := "title-filter",
      input(
        typ := "search",
        cls := "field",
        idAttr := "f-title",
        placeholder := "filter by title…",
        aria.label := "filter by title",
        controlled(
          value <-- UiState.title.signal.map(_.getOrElse("")),
          onInput.mapToValue --> { v => UiState.set(UiState.title := Some(v).filter(_.trim.nonEmpty))() }
        )
      )
    )
