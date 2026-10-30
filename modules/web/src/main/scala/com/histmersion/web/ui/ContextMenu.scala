package com.histmersion.web.ui

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The ONE menu of actions on a thing — a row, the reading pane, the map, the timeline: opened by a right
  * click, by the keyboard's menu key or Shift+F10 on the thing, or by its "⋯" button. Every action in it is
  * also somewhere a person can see; none is only here. One menu is open at a time; ↑ ↓ Home End move, Enter
  * runs, Escape or Tab closes it and gives the focus back. */
object ContextMenu:

  /** One action. `disabled`: shown, with why it cannot run now (`hint`). */
  final case class Entry(label: String, run: () => Unit, disabled: Boolean = false, hint: Option[String] = None,
      pressed: Option[Boolean] = None)

  /** A line between groups of actions. */
  val Separator: Entry = Entry("", () => ())

  private final case class Open(title: String, entries: List[Entry], x: Double, y: Double, back: Option[dom.HTMLElement])

  private val open = Var(Option.empty[Open])

  def isOpen: Boolean = open.now().isDefined

  /** The menu key: the keyboard's own, or Shift+F10. Pure. */
  def isMenuKey(key: String, shift: Boolean): Boolean = key == "ContextMenu" || (shift && key == "F10")

  /** Where a menu of `w`×`h` opened at (`x`, `y`) stands so that it stays inside a `vw`×`vh` viewport (with a
    * margin), flipped to the other side of the point where it would not fit. Pure. */
  def place(x: Double, y: Double, w: Double, h: Double, vw: Double, vh: Double, margin: Double = 8): (Double, Double) =
    val px = if x + w + margin > vw then math.max(margin, x - w) else x
    val py = if y + h + margin > vh then math.max(margin, y - h) else y
    (math.max(margin, math.min(px, vw - w - margin)), math.max(margin, math.min(py, vh - h - margin)))

  def show(title: String, entries: List[Entry], x: Double, y: Double, back: Option[dom.HTMLElement]): Unit =
    open.set(Some(Open(title, entries, x, y, back)))

  def close(returnFocus: Boolean = true): Unit =
    val was = open.now()
    open.set(None)
    if returnFocus then was.flatMap(_.back).filter(_.isConnected).foreach(_.focus())

  /** Under (or, near the bottom, over) an element: how the keyboard and the "⋯" button open it. */
  def showUnder(el: dom.HTMLElement, title: String, entries: List[Entry]): Unit =
    val r = el.getBoundingClientRect()
    show(title, entries, r.left, r.bottom + 2, Some(el))

  /** Where a right click keeps the browser's own menu: in a text field, over selected text, on a link inside
    * what is read (its "open in a new tab" and "copy link" are the link's, not the item's). */
  private val NativeHere = "input, textarea, select, [contenteditable], .note-text a, .reading-links a, .reading-findings a, .detail-card a"

  private def nativeWanted(ev: dom.MouseEvent): Boolean =
    val selected = scala.util.Try(dom.window.getSelection().toString.trim.nonEmpty).getOrElse(false)
    selected || Option(ev.target).collect { case e: dom.Element => e }.exists(_.closest(NativeHere) != null)

  /** A right click and the menu key on the element open its menu; `entries` is read when it opens. */
  def on(title: String, entries: () => List[Entry]): Modifier[HtmlElement] =
    List(
      onContextMenu --> { ev =>
        val es = if nativeWanted(ev) then Nil else entries()
        if es.nonEmpty then
          ev.preventDefault()
          ev.stopPropagation()
          show(title, es, ev.clientX, ev.clientY, Option(ev.currentTarget.asInstanceOf[dom.HTMLElement]))
      },
      onKeyDown --> { ev =>
        if isMenuKey(ev.key, ev.shiftKey) then
          val es = entries()
          if es.nonEmpty then
            ev.preventDefault()
            ev.stopPropagation()
            val focused = Option(dom.document.activeElement).collect { case e: dom.HTMLElement => e }
            showUnder(focused.getOrElse(ev.currentTarget.asInstanceOf[dom.HTMLElement]), title, es)
      })

  /** The visible way in: a "⋯" button that opens the same menu under itself. */
  def button(title: String, entries: () => List[Entry], extraCls: String = ""): HtmlElement =
    L.button(typ := "button", cls := s"btn btn--icon btn--sm menu-btn $extraCls".trim, aria.label := title,
      L.title := title, aria.hasPopup := true,
      aria.expanded <-- open.signal.map(_.exists(_.title == title)),
      com.histmersion.web.Icon(com.histmersion.web.Icon.More),
      onClick --> { ev =>
        ev.stopPropagation()
        if open.now().exists(_.title == title) then close(returnFocus = false)
        else showUnder(ev.currentTarget.asInstanceOf[dom.HTMLElement], title, entries())
      })

  private def items(menu: dom.Element): List[dom.HTMLElement] =
    val ns = menu.querySelectorAll("[role=menuitem]:not([disabled]), [role=menuitemcheckbox]:not([disabled])")
    (0 until ns.length).toList.map(ns(_).asInstanceOf[dom.HTMLElement])

  /** The menu, mounted once by the page. */
  lazy val element: HtmlElement =
    div(
      cls := "context-menu-root",
      child.maybe <-- open.signal.map(_.map(menu))
    )

  private def menu(o: Open): HtmlElement =
    div(
      cls := "context-menu",
      role := "menu",
      aria.label := o.title,
      tabIndex := -1,
      styleAttr := s"--menu-x:${o.x}px;--menu-y:${o.y}px",
      onMountCallback { ctx =>
        val el = ctx.thisNode.ref
        val r  = el.getBoundingClientRect()
        val (x, y) = place(o.x, o.y, r.width, r.height, dom.window.innerWidth, dom.window.innerHeight)
        el.style.setProperty("--menu-x", s"${x}px")
        el.style.setProperty("--menu-y", s"${y}px")
        items(el).headOption.fold(el.focus())(_.focus())
      },
      documentEvents(_.onPointerDown) --> { ev =>
        val t = ev.target.asInstanceOf[dom.Element]
        if t.closest(".context-menu") == null && t.closest(".menu-btn") == null then close(returnFocus = false)
      },
      onKeyDown --> { ev =>
        val el  = ev.currentTarget.asInstanceOf[dom.Element]
        val all = items(el)
        val at  = all.indexWhere(_ == dom.document.activeElement)
        def go(i: Int): Unit = if all.nonEmpty then { ev.preventDefault(); all((i + all.size) % all.size).focus() }
        ev.key match
          case "ArrowDown" => go(at + 1)
          case "ArrowUp"   => go(if at < 0 then all.size - 1 else at - 1)
          case "Home"      => go(0)
          case "End"       => go(all.size - 1)
          case "Escape"    => ev.preventDefault(); ev.stopPropagation(); close()
          case "Tab"       => close(returnFocus = false)
          case _           => ()
      },
      p(cls := "context-menu-title", o.title),
      o.entries.map { e =>
        if e == Separator then div(cls := "context-menu-sep", role := "separator")
        else
          L.button(
            typ := "button",
            cls := "context-menu-item",
            role := (if e.pressed.isDefined then "menuitemcheckbox" else "menuitem"),
            e.pressed.map(on => aria.checked := on.toString),
            disabled := e.disabled,
            e.hint.map(h => L.title := h),
            span(cls := "context-menu-label", e.label),
            e.hint.filter(_ => e.disabled).map(h => span(cls := "context-menu-hint", h)),
            onClick --> { _ =>
              close()
              e.run()
            })
      }
    )
