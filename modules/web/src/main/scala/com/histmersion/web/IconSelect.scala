package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** THE menu with icons (a native `<select>` cannot show one): a button showing the current choice — its
  * icon and label — that opens a listbox of the options, each with the icon the map draws for it (an
  * event kind's square badge, a site type's circle, a tier's filled or dashed sample, a picture's print).
  * The WAI-ARIA "select-only combobox" pattern: the button has `aria-haspopup="listbox"` and
  * `aria-expanded`; the listbox takes the focus and names its active option with
  * `aria-activedescendant`; ↑/↓, Home/End and typing a name move it, Enter or Space choose, Escape or Tab
  * close and give the focus back, a click outside closes. The listbox is placed like the cards
  * ([[Preview.underFit]]): under the button, above it when there is no room, capped and scrolling when
  * long. The value is a [[UiState]] field, so the menu, the URL and the map cannot disagree. */
object IconSelect:

  /** One option: its value ("" = no filter), its label, its icon's markup, the group it is listed under. */
  final case class Opt(value: String, label: String, icon: String, group: Option[String] = None)

  /** Pure: the option a typed prefix names — the first after `from` (wrapping) whose label starts with it. */
  def typeAhead(opts: List[Opt], from: Int, prefix: String): Option[Int] =
    val p = prefix.toLowerCase
    if p.isEmpty || opts.isEmpty then None
    else
      val n = opts.size
      (1 to n).map(i => (from + i) % n).find(i => opts(i).label.toLowerCase.startsWith(p))
        .orElse(Option.when(opts.lift(from).exists(_.label.toLowerCase.startsWith(p)))(from))

  /** Pure: the active index after a key — ↑/↓ by one (clamped), Home/End to the ends; `None` for any
    * other key. */
  def step(key: String, at: Int, n: Int): Option[Int] =
    if n == 0 then None
    else key match
      case "ArrowDown" => Some((at + 1).min(n - 1))
      case "ArrowUp"   => Some((at - 1).max(0))
      case "Home"      => Some(0)
      case "End"       => Some(n - 1)
      case _           => None

  // `var`: ids for the listbox and its options, unique on the page
  private var seq = 0

  def apply(name: String, field: Field[Option[String]], options: Signal[List[Opt]]): HtmlElement =
    seq += 1
    val id = s"isel-$seq"
    val isOpen = Var(false)
    val active = Var(0)
    val opts   = Var[List[Opt]](Nil)
    // `var`s: the type-ahead's letters and when the last came; the button and the listbox elements; the
    // outside-click listener while open
    var typed = ""
    var typedAt = 0.0
    var buttonEl: dom.html.Element = null
    var listEl: dom.html.Element = null
    var outside: Option[scala.scalajs.js.Function1[dom.Event, Unit]] = None

    def chosenIndex(): Int = opts.now().indexWhere(_.value == field.now().getOrElse("")).max(0)

    def place(): Unit =
      if buttonEl != null && listEl != null then
        val r = buttonEl.getBoundingClientRect()
        listEl.style.setProperty("--pop-minw", s"${r.width}px")
        val w = listEl.getBoundingClientRect().width
        val h = listEl.scrollHeight.toDouble + (listEl.offsetHeight - listEl.clientHeight)
        val fit = Preview.underFit(r.left, r.top, r.bottom, r.width, w, h, dom.window.innerWidth, dom.window.innerHeight)
        // the position is data for `.icon-select-list`, not a style of its own
        listEl.style.setProperty("--pop-x", s"${fit.x}px")
        listEl.style.setProperty("--pop-y", s"${fit.y}px")
        listEl.style.setProperty("--pop-maxh", s"${fit.maxH}px")

    def scrollToActive(): Unit =
      if listEl != null then
        Option(listEl.querySelector(s"#$id-${active.now()}")).foreach(_.asInstanceOf[scala.scalajs.js.Dynamic].scrollIntoView(
          scala.scalajs.js.Dynamic.literal(block = "nearest")))

    def openList(): Unit =
      active.set(chosenIndex())
      isOpen.set(true)
      place()
      listEl.focus()
      scrollToActive()
      val f: scala.scalajs.js.Function1[dom.Event, Unit] = (e: dom.Event) => e.target match
        case n: dom.Node if listEl.contains(n) || buttonEl.contains(n) => ()
        case _ => close(refocus = false)
      outside = Some(f)
      dom.document.addEventListener("pointerdown", f, useCapture = true)

    def close(refocus: Boolean): Unit =
      if isOpen.now() then
        isOpen.set(false)
        outside.foreach(f => dom.document.removeEventListener("pointerdown", f, useCapture = true))
        outside = None
        if refocus && buttonEl != null then buttonEl.focus()

    def choose(i: Int): Unit =
      opts.now().lift(i).foreach(o => UiState.set(field := Option(o.value).filter(_.nonEmpty))())
      close(refocus = true)

    def onListKey(e: dom.KeyboardEvent): Unit =
      val n = opts.now().size
      step(e.key, active.now(), n) match
        case Some(i) =>
          e.preventDefault()
          active.set(i)
          scrollToActive()
        case None => e.key match
          case "Enter" | " " => e.preventDefault(); choose(active.now())
          case "Escape"      => e.preventDefault(); e.stopPropagation(); close(refocus = true)
          case "Tab"         => close(refocus = false)
          case k if k.length == 1 && !e.ctrlKey && !e.metaKey && !e.altKey =>
            val now = scala.scalajs.js.Date.now()
            typed = if now - typedAt > 700 then k else typed + k
            typedAt = now
            typeAhead(opts.now(), if typed.length == 1 then active.now() else (active.now() - 1 + n) % n, typed)
              .foreach { i => active.set(i); scrollToActive() }
          case _ => ()

    val current = opts.signal.combineWith(field.signal).map((os, v) => os.find(_.value == v.getOrElse("")).orElse(os.headOption))

    div(
      cls := "icon-select",
      options --> opts.writer,
      button(
        typ := "button",
        cls := "btn icon-select-button",
        aria.label := name, title := name,
        htmlAttr("aria-haspopup", com.raquo.laminar.codecs.StringAsIsCodec) := "listbox",
        aria.expanded <-- isOpen.signal,
        aria.controls := s"$id-list",
        onMountCallback(ctx => buttonEl = ctx.thisNode.ref),
        child <-- current.map(_.fold(emptyNode: Node)(o =>
          span(cls := "icon-select-icon", onMountCallback(c => c.thisNode.ref.innerHTML = o.icon)))),
        span(cls := "icon-select-label", child.text <-- current.map(o => s"$name: ${o.fold("")(_.label)}")),
        span(cls := "icon-select-caret", aria.hidden := true, "▾"),
        onClick --> { _ => if isOpen.now() then close(refocus = true) else openList() },
        onKeyDown --> { e =>
          if e.key == "ArrowDown" || e.key == "ArrowUp" then
            e.preventDefault()
            openList()
        }
      ),
      ul(
        idAttr := s"$id-list",
        cls := "icon-select-list",
        cls("is-hidden") <-- isOpen.signal.map(!_),
        role := "listbox",
        tabIndex := -1,
        aria.label := name,
        aria.activeDescendant <-- active.signal.map(i => s"$id-$i"),
        onMountCallback(ctx => listEl = ctx.thisNode.ref),
        onKeyDown --> (e => onListKey(e)),
        children <-- opts.signal.combineWith(field.signal).map { (os, v) =>
          val chosen = v.getOrElse("")
          os.zipWithIndex.flatMap { (o, i) =>
            val head = o.group.filter(g => i == 0 || os(i - 1).group != o.group)
              .map(g => li(cls := "icon-select-group", role := "presentation", g)).toList
            head :+ li(
              idAttr := s"$id-$i",
              cls := "icon-select-option",
              cls("is-active") <-- active.signal.map(_ == i),
              role := "option",
              aria.selected := (o.value == chosen),
              dataAttr("value") := o.value,
              span(cls := "icon-select-icon", onMountCallback(c => c.thisNode.ref.innerHTML = o.icon)),
              span(o.label),
              onMouseMove --> { _ => if active.now() != i then active.set(i) },
              onClick --> { _ => choose(i) }
            )
          }
        }
      ),
      windowEvents(_.onResize) --> { _ => if isOpen.now() then place() },
      onUnmountCallback(_ => close(refocus = false))
    )
