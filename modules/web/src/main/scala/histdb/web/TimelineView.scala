package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TimelineView:

  private val H         = 58
  private val CTX_H     = 20
  private val BASE      = 42
  private val MIN_SPAN  = 10L
  private val ZOOM      = 2.0
  private val HANDLE_W  = 8.0

  // ── state ─────────────────────────────────────────────────────────────────────────────────────
  private var limits: TimeConfig = uninitialized

  private val spanFrom  = Var(0L)
  private val spanTo    = Var(0L)
  private val bars      = Var(List.empty[TimeBucket])
  private val bucketVar = Var(Option.empty[Long])
  private val loading   = Var(false)
  private val failedAt  = Var(Option.empty[String])
  private val folded    = Var(false)
  private val dragState = Var(Option.empty[DragInfo])
  private val periodPresets = Var(List.empty[HistoricalPeriod])
  private val showPresets = Var(false)

  private val renderTick = Var(0)

  private enum HandleKind:
    case Left, Right, Pan

  private case class DragInfo(
      x: Double, cur: Double,
      kind: HandleKind = HandleKind.Pan,
      origFrom: Long = 0, origTo: Long = 0
  )

  private var reqId: Int          = 0
  private var timer: Int          = 0
  private var svgEl: dom.Element  = uninitialized
  private var ctxSvgEl: dom.Element = uninitialized

  def currentSpan: (Long, Long) = (spanFrom.now(), spanTo.now())

  // ── span algebra ──────────────────────────────────────────────────────────────────────────────

  private def clamp(a0: Long, b0: Long): (Long, Long) =
    var a = Math.round(Math.min(a0.toDouble, b0.toDouble))
    var b = Math.round(Math.max(a0.toDouble, b0.toDouble))
    if b - a < MIN_SPAN then
      val mid = (a.toDouble + b.toDouble) / 2
      a = Math.round(mid - MIN_SPAN.toDouble / 2)
      b = a + MIN_SPAN
    val width = Math.min(b - a, limits.max - limits.min)
    if a < limits.min then { a = limits.min; b = a + width }
    if b > limits.max then { b = limits.max; a = b - width }
    (a, Math.max(a + MIN_SPAN, b))

  private def zoomSpan(f: Double, anchor: Option[Double]): (Long, Long) =
    val sf = spanFrom.now().toDouble
    val st = spanTo.now().toDouble
    val a  = anchor.getOrElse((sf + st) / 2)
    clamp(Math.round(a - (a - sf) * f), Math.round(a + (st - a) * f))

  private def setSpan(from: Long, to: Long, push: Boolean = true): Unit =
    val (nf, nt) = clamp(from, to)
    if nf == spanFrom.now() && nt == spanTo.now() then ()
    else
      spanFrom.set(nf)
      spanTo.set(nt)
      bumpRender()
      schedule()
      if push then UiState.set(("from", nf), ("to", nt))()

  private def bumpRender(): Unit = renderTick.update(_ + 1)

  // ── data ──────────────────────────────────────────────────────────────────────────────────────

  private def schedule(): Unit =
    dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => load(), 150)

  private def load(): Unit =
    val id = { reqId += 1; reqId }
    loading.set(true)
    bumpRender()
    val sf = spanFrom.now()
    val st = spanTo.now()
    val f  = UiState.toFilter().copy(period = Some(Period(sf, st)))
    ApiClient.aggregate(Api.AggregateQuery(Some("time"), Some("starts"), f)).onComplete { result =>
      if id == reqId then
        result.fold(
          _ => failedAt.set(Some("counts unavailable")),
          h => {
            bars.set(h.buckets)
            bucketVar.set(Some(h.bucket))
            failedAt.set(None)
          }
        )
        loading.set(false)
        bumpRender()
    }

  private def loadPresets(): Unit =
    if periodPresets.now().nonEmpty then return
    ApiClient.periods().foreach { ps =>
      periodPresets.set(ps.filter(_.parentId.isEmpty).sortBy(_.period.start))
    }

  // ── drawing helpers ───────────────────────────────────────────────────────────────────────────

  private def elWidth(el: dom.Element): Double =
    if el == null then 600.0
    else
      val w = el.asInstanceOf[dom.SVGElement].clientWidth.toDouble
      if w > 0 then w
      else
        val r = el.getBoundingClientRect()
        if r.width > 0 then r.width else 600.0

  private def years(): Double = spanTo.now().toDouble + 1 - spanFrom.now().toDouble
  private def xOf(y: Double, w: Double): Double  = (y - spanFrom.now().toDouble) / years() * w
  private def yearAt(px: Double, w: Double): Long = Math.round(spanFrom.now().toDouble + (px / w) * years())

  private def fullRange: Double = limits.max.toDouble - limits.min.toDouble
  private def ctxXOf(y: Double, w: Double): Double = (y - limits.min.toDouble) / fullRange * w

  // ── public entry point ────────────────────────────────────────────────────────────────────────

  def init(config: UiConfig): HtmlElement =
    limits = config.time
    val sf = UiState.from.now().getOrElse(config.time.defaultFrom)
    val st = UiState.to.now().getOrElse(config.time.defaultTo)
    val (cf, ct) = clamp(sf, st)
    spanFrom.set(cf)
    spanTo.set(ct)
    if cf != sf || ct != st then UiState.set(("from", cf), ("to", ct))()

    try folded.set(dom.window.localStorage.getItem("histdb.tlFolded") == "1")
    catch case _: Exception => ()

    val fromInput = input(
      cls   := "tl-year",
      idAttr := "tl-from",
      title := "from — e.g. 3300 BCE, -3300, c. 1500 BC",
      controlled(
        value <-- spanFrom.signal.map(Fmt.yr),
        onInput.mapToValue --> Observer.empty
      )
    )
    val toInput = input(
      cls   := "tl-year",
      idAttr := "tl-to",
      title := "to — e.g. 1200 CE, -1200",
      controlled(
        value <-- spanTo.signal.map(Fmt.yr),
        onInput.mapToValue --> Observer.empty
      )
    )

    def commitBoxes(fromEl: dom.html.Input, toEl: dom.html.Input): Unit =
      val a = Fmt.parseYear(fromEl.value)
      val b = Fmt.parseYear(toEl.value)
      if a.isEmpty then fromEl.classList.add("bad")
      if b.isEmpty then toEl.classList.add("bad")
      (a, b) match
        case (Some(av), Some(bv)) =>
          fromEl.classList.remove("bad")
          toEl.classList.remove("bad")
          setSpan(av, bv)
        case _ => ()

    val infoText = htmlTag("span")(
      cls := "tl-info muted",
      child.text <-- bucketVar.signal.combineWith(loading.signal).map { (bk, ld) =>
        val maxCount = bars.now().map(_.count).maxOption.getOrElse(0)
        bk match
          case None    => if ld then "..." else "no data"
          case Some(b) => s"$b-yr buckets · sources starting · peak $maxCount"
      }
    )

    val badgeEl = htmlTag("span")(
      cls := "tl-badge",
      idAttr := "tl-badge",
      hidden <-- failedAt.signal.map(_.isEmpty),
      child.text <-- failedAt.signal.map(_.getOrElse(""))
    )

    val presetsDropdown = div(
      cls := "tl-presets-dropdown",
      hidden <-- showPresets.signal.map(!_),
      children <-- periodPresets.signal.map { ps =>
        if ps.isEmpty then List(div(cls := "tl-preset-loading", "loading…"))
        else ps.map { p =>
          button(
            cls := "tl-preset-item",
            s"${p.label}  ${Fmt.yr(p.period.start)}–${Fmt.yr(p.period.end)}",
            onClick --> { _ =>
              setSpan(p.period.start, p.period.end)
              showPresets.set(false)
            }
          )
        }
      },
      onMountCallback { _ =>
        dom.document.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
          if showPresets.now() then
            val target = e.target.asInstanceOf[dom.Element]
            if target.closest(".tl-preset-wrap") == null then
              showPresets.set(false)
        })
      }
    )

    val tlDiv = div(
      cls := "tl",
      cls("tl-folded") <-- folded.signal,
      div(cls := "tl-ctl",
        label(forId := "tl-from", "span"),
        fromInput,
        htmlTag("span")(cls := "tl-arrow", "→"),
        toInput,
        button(cls := "tl-btn", title := "zoom in ×2",
          aria.label := "zoom in", "+",
          onClick --> { _ =>
            val (zf, zt) = zoomSpan(1.0 / ZOOM, None)
            setSpan(zf, zt)
          }),
        button(cls := "tl-btn", title := "zoom out ×2",
          aria.label := "zoom out", "−",
          onClick --> { _ =>
            val (zf, zt) = zoomSpan(ZOOM, None)
            setSpan(zf, zt)
          }),
        div(cls := "tl-preset-wrap",
          button(
            cls := "tl-btn tl-btn-presets",
            title := "period presets",
            aria.label := "period presets",
            "⏱",
            onClick --> { _ =>
              loadPresets()
              showPresets.update(!_)
            }
          ),
          presetsDropdown
        ),
        button(cls := "tl-btn tl-reset", title := "reset to default span",
          aria.label := "reset", "⟲",
          onClick --> { _ => setSpan(config.time.defaultFrom, config.time.defaultTo) }),
        button(
          cls := "tl-btn tl-fold",
          title <-- folded.signal.map(f => if f then "show the timeline" else "collapse the timeline"),
          aria.label <-- folded.signal.map(f => if f then "show the timeline" else "collapse the timeline"),
          aria.expanded <-- folded.signal.map(f => !f),
          child.text <-- folded.signal.map(f => if f then "▾" else "▴"),
          onClick --> { _ =>
            val next = !folded.now()
            folded.set(next)
            try dom.window.localStorage.setItem("histdb.tlFolded", if next then "1" else "0")
            catch case _: Exception => ()
          }
        ),
        infoText,
        badgeEl
      ),
      // Context strip: full range overview with viewport indicator
      div(
        cls := "tl-ctx-wrap",
        onMountCallback { ctx =>
          val svgNs = "http://www.w3.org/2000/svg"
          val svg = dom.document.createElementNS(svgNs, "svg").asInstanceOf[dom.SVGElement]
          svg.setAttribute("class", "tl-ctx-svg")
          svg.setAttribute("preserveAspectRatio", "none")
          svg.setAttribute("height", CTX_H.toString)
          ctxSvgEl = svg
          ctx.thisNode.ref.appendChild(svg)

          renderContext()
          renderTick.signal.foreach(_ => renderContext())(using ctx.owner)

          svg.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
            if e.button == 0 then
              val rect = svg.getBoundingClientRect()
              val w    = if rect.width > 0 then rect.width else 600.0
              val clickYear = limits.min.toDouble + ((e.clientX - rect.left) / w) * fullRange
              val half = (spanTo.now().toDouble - spanFrom.now().toDouble) / 2
              val nf = Math.round(clickYear - half)
              val nt = Math.round(clickYear + half)
              setSpan(nf, nt)
          })
        }
      ),
      // Main SVG strip with draggable handles
      div(
        cls := "tl-svg-wrap",
        onMountCallback { ctx =>
          val svgNs = "http://www.w3.org/2000/svg"
          val svgRoot = dom.document.createElementNS(svgNs, "svg").asInstanceOf[dom.SVGElement]
          svgRoot.setAttribute("class", "tl-svg")
          svgRoot.setAttribute("preserveAspectRatio", "none")
          svgRoot.setAttribute("height", H.toString)
          svgEl = svgRoot
          ctx.thisNode.ref.appendChild(svgRoot)

          renderImperative()
          renderTick.signal.foreach(_ => renderImperative())(using ctx.owner)

          svgRoot.addEventListener("wheel", { (e: dom.WheelEvent) =>
            e.preventDefault()
            if e.deltaY != 0 then
              val rect   = svgRoot.getBoundingClientRect()
              val px     = e.clientX - rect.left
              val w      = if rect.width > 0 then rect.width else 600.0
              val anchor = spanFrom.now().toDouble + (px / w) * years()
              val factor = if e.deltaY > 0 then ZOOM else 1.0 / ZOOM
              val (zf, zt) = zoomSpan(factor, Some(anchor))
              setSpan(zf, zt)
          }, js.Dynamic.literal(passive = false).asInstanceOf[dom.EventListenerOptions])

          svgRoot.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
            if e.button == 0 then
              val rect = svgRoot.getBoundingClientRect()
              val w    = if rect.width > 0 then rect.width else 600.0
              val x    = e.clientX - rect.left
              val sf   = spanFrom.now()
              val st   = spanTo.now()

              val kind =
                if x <= HANDLE_W then HandleKind.Left
                else if x >= w - HANDLE_W then HandleKind.Right
                else HandleKind.Pan

              dragState.set(Some(DragInfo(x, x, kind, sf, st)))
              svgRoot.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
              bumpRender()
          })
          svgRoot.addEventListener("pointermove", { (e: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              val rect = svgRoot.getBoundingClientRect()
              val w    = if rect.width > 0 then rect.width else 600.0
              val cur  = e.clientX - rect.left
              val dx   = cur - d.x

              d.kind match
                case HandleKind.Left =>
                  val yearDelta = (dx / w * years()).toLong
                  val nf = d.origFrom + yearDelta
                  setSpan(nf, d.origTo, push = false)
                case HandleKind.Right =>
                  val yearDelta = (dx / w * years()).toLong
                  val nt = d.origTo + yearDelta
                  setSpan(d.origFrom, nt, push = false)
                case HandleKind.Pan =>
                  dragState.set(Some(d.copy(cur = cur)))
                  bumpRender()
            }
          })
          val endDrag: js.Function1[dom.PointerEvent, Unit] = { (_: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              dragState.set(None)
              d.kind match
                case HandleKind.Left | HandleKind.Right =>
                  UiState.set(("from", spanFrom.now()), ("to", spanTo.now()))()
                case HandleKind.Pan =>
                  if Math.abs(d.cur - d.x) >= 3 then
                    val w = elWidth(svgEl)
                    val y0 = yearAt(Math.min(d.x, d.cur), w)
                    val y1 = yearAt(Math.max(d.x, d.cur), w)
                    setSpan(y0, y1)
                  else bumpRender()
            }
          }
          svgRoot.addEventListener("pointerup", endDrag)
          svgRoot.addEventListener("pointercancel", endDrag)

          if js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined" then
            val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(
              { (_: js.Any) => renderImperative(); renderContext() }: js.Function1[js.Any, Unit]
            )
            obs.observe(svgRoot)

          val fromEl = dom.document.getElementById("tl-from").asInstanceOf[dom.html.Input]
          val toEl   = dom.document.getElementById("tl-to").asInstanceOf[dom.html.Input]
          if fromEl != null && toEl != null then
            val doCommit = () => commitBoxes(fromEl, toEl)
            fromEl.addEventListener("change", (_: dom.Event) => doCommit())
            toEl.addEventListener("change", (_: dom.Event) => doCommit())
            fromEl.addEventListener("keydown", { (e: dom.KeyboardEvent) =>
              if e.key == "Enter" then { doCommit(); fromEl.blur() }
            })
            toEl.addEventListener("keydown", { (e: dom.KeyboardEvent) =>
              if e.key == "Enter" then { doCommit(); toEl.blur() }
            })

          UiState.signal.foreach { _ =>
            val uf = UiState.from.now().getOrElse(spanFrom.now())
            val ut = UiState.to.now().getOrElse(spanTo.now())
            if uf != spanFrom.now() || ut != spanTo.now() then
              val (cf2, ct2) = clamp(uf, ut)
              spanFrom.set(cf2)
              spanTo.set(ct2)
              bumpRender()
            schedule()
          }(using ctx.owner)

          load()
        }
      )
    )
    tlDiv

  private def renderContext(): Unit =
    if ctxSvgEl == null then return
    val svg = ctxSvgEl
    val w   = elWidth(svg)
    svg.setAttribute("viewBox", s"0 0 ${w.toInt} $CTX_H")

    while svg.firstChild != null do svg.removeChild(svg.firstChild)

    val ns  = "http://www.w3.org/2000/svg"
    val bs  = bars.now()
    val sf  = spanFrom.now()
    val st  = spanTo.now()

    // Background
    val bg = dom.document.createElementNS(ns, "rect")
    bg.setAttribute("x", "0"); bg.setAttribute("y", "0")
    bg.setAttribute("width", w.toString); bg.setAttribute("height", CTX_H.toString)
    bg.setAttribute("class", "tl-ctx-bg")
    svg.appendChild(bg)

    // Bars across full range
    if bs.nonEmpty then
      val maxCount = bs.map(_.count).max.max(1)
      val barH = CTX_H - 4
      for b <- bs do
        val x0 = Math.max(0.0, ctxXOf(b.start.toDouble, w))
        val x1 = Math.min(w, ctxXOf(b.end.toDouble + 1, w))
        if x1 > x0 then
          val h = if b.count == 0 then 0.0
                  else Math.max(1.0, (b.count.toDouble / maxCount) * barH)
          val rect = dom.document.createElementNS(ns, "rect")
          rect.setAttribute("class", "tl-ctx-bar")
          rect.setAttribute("x", x0.toString)
          rect.setAttribute("y", (CTX_H - 2 - h).toString)
          rect.setAttribute("width", Math.max(1.0, x1 - x0 - 0.3).toString)
          rect.setAttribute("height", h.toString)
          svg.appendChild(rect)

    // Viewport indicator
    val vx0 = Math.max(0.0, ctxXOf(sf.toDouble, w))
    val vx1 = Math.min(w, ctxXOf(st.toDouble + 1, w))
    val vp = dom.document.createElementNS(ns, "rect")
    vp.setAttribute("class", "tl-ctx-viewport")
    vp.setAttribute("x", vx0.toString); vp.setAttribute("y", "0")
    vp.setAttribute("width", Math.max(4.0, vx1 - vx0).toString)
    vp.setAttribute("height", CTX_H.toString)
    svg.appendChild(vp)

    // Viewport edges
    for ex <- List(vx0, vx1) do
      val edge = dom.document.createElementNS(ns, "line")
      edge.setAttribute("class", "tl-ctx-edge")
      edge.setAttribute("x1", ex.toString); edge.setAttribute("y1", "0")
      edge.setAttribute("x2", ex.toString); edge.setAttribute("y2", CTX_H.toString)
      svg.appendChild(edge)

    // Labels at edges of viewport
    val fromLabel = Fmt.yr(sf)
    val toLabel   = Fmt.yr(st)
    val fromTxt = dom.document.createElementNS(ns, "text")
    fromTxt.setAttribute("class", "tl-ctx-label")
    fromTxt.setAttribute("x", (vx0 + 3).toString)
    fromTxt.setAttribute("y", (CTX_H - 3).toString)
    fromTxt.textContent = fromLabel
    svg.appendChild(fromTxt)

    val toTxt = dom.document.createElementNS(ns, "text")
    toTxt.setAttribute("class", "tl-ctx-label tl-ctx-label-end")
    toTxt.setAttribute("x", (vx1 - 3).toString)
    toTxt.setAttribute("y", (CTX_H - 3).toString)
    toTxt.textContent = toLabel
    svg.appendChild(toTxt)

  private def renderImperative(): Unit =
    if svgEl == null then ()
    else
      val svg = svgEl
      val w   = elWidth(svg)
      svg.setAttribute("viewBox", s"0 0 ${w.toInt} $H")
      val ld = loading.now()
      if ld then svg.classList.add("tl-loading")
      else svg.classList.remove("tl-loading")

      while svg.firstChild != null do svg.removeChild(svg.firstChild)

      val sf = spanFrom.now()
      val st = spanTo.now()
      val bs = bars.now()
      val maxCount = if bs.isEmpty then 0 else bs.map(_.count).max

      val ns = "http://www.w3.org/2000/svg"

      // Bars.
      for b <- bs do
        val x0 = Math.max(0.0, xOf(b.start.toDouble, w))
        val x1 = Math.min(w, xOf(b.end.toDouble + 1, w))
        if x1 > x0 then
          val h = if maxCount == 0 then 0.0
                  else Math.max(if b.count > 0 then 2.0 else 0.0, (b.count.toDouble / maxCount) * (BASE - 6))
          val mid = (b.start.toDouble + b.end.toDouble) / 2
          val t   = (mid - limits.min.toDouble) / (limits.max.toDouble - limits.min.toDouble)
          val barW = Math.max(1.0, x1 - x0 - 0.5)
          val rect = dom.document.createElementNS(ns, "rect")
          rect.setAttribute("class", "tl-bar")
          rect.setAttribute("x", x0.toString)
          rect.setAttribute("y", (BASE - h).toString)
          rect.setAttribute("width", barW.toString)
          rect.setAttribute("height", h.toString)
          rect.setAttribute("fill", Fmt.color(t))
          val titleEl = dom.document.createElementNS(ns, "title")
          titleEl.textContent = s"${Fmt.yr(b.start)}–${Fmt.yr(b.end)}: ${b.count} source(s)"
          rect.appendChild(titleEl)
          svg.appendChild(rect)

      // Axis.
      val axisLine = dom.document.createElementNS(ns, "line")
      axisLine.setAttribute("class", "tl-axis")
      axisLine.setAttribute("x1", "0")
      axisLine.setAttribute("y1", (BASE + 0.5).toString)
      axisLine.setAttribute("x2", w.toString)
      axisLine.setAttribute("y2", (BASE + 0.5).toString)
      svg.appendChild(axisLine)

      // Ticks.
      val endLabel  = Fmt.yr(st)
      val textWidth = (label: String) => label.length * 6.2
      val endStart  = w - 2 - textWidth(endLabel) - 8

      if bs.nonEmpty then
        val every = Math.ceil(bs.length.toDouble / 6).toInt.max(1)
        var i = 0
        while i < bs.length do
          if i % every == 0 then
            val b = bs(i)
            val x = xOf(b.start.toDouble, w)
            if x >= HANDLE_W && x <= w - HANDLE_W - 4 then
              val tick = dom.document.createElementNS(ns, "line")
              tick.setAttribute("class", "tl-tick")
              tick.setAttribute("x1", x.toString); tick.setAttribute("y1", BASE.toString)
              tick.setAttribute("x2", x.toString); tick.setAttribute("y2", (BASE + 4).toString)
              svg.appendChild(tick)
              val lbl = Fmt.yr(b.start)
              if x + 3 + textWidth(lbl) < endStart then
                val txt = dom.document.createElementNS(ns, "text")
                txt.setAttribute("class", "tl-label")
                txt.setAttribute("x", (x + 3).toString)
                txt.setAttribute("y", (H - 2).toString)
                txt.textContent = lbl
                svg.appendChild(txt)
          i += 1

      // End label.
      val endTxt = dom.document.createElementNS(ns, "text")
      endTxt.setAttribute("class", "tl-label tl-label-end")
      endTxt.setAttribute("x", (w - HANDLE_W - 2).toString)
      endTxt.setAttribute("y", (H - 2).toString)
      endTxt.textContent = endLabel
      svg.appendChild(endTxt)

      // Drag overlay (zoom selection).
      dragState.now().foreach { d =>
        if d.kind == HandleKind.Pan then
          val dx0 = Math.min(d.x, d.cur)
          val dx1 = Math.max(d.x, d.cur)
          if Math.abs(d.cur - d.x) >= 3 then
            val dragRect = dom.document.createElementNS(ns, "rect")
            dragRect.setAttribute("class", "tl-drag")
            dragRect.setAttribute("x", dx0.toString)
            dragRect.setAttribute("y", "0")
            dragRect.setAttribute("width", Math.max(1.0, dx1 - dx0).toString)
            dragRect.setAttribute("height", BASE.toString)
            svg.appendChild(dragRect)
      }

      // Drag handles — left edge
      val lHandle = dom.document.createElementNS(ns, "rect")
      lHandle.setAttribute("class", "tl-handle tl-handle-left")
      lHandle.setAttribute("x", "0")
      lHandle.setAttribute("y", "0")
      lHandle.setAttribute("width", HANDLE_W.toString)
      lHandle.setAttribute("height", H.toString)
      lHandle.setAttribute("rx", "3")
      svg.appendChild(lHandle)

      val lGrip = dom.document.createElementNS(ns, "g")
      lGrip.setAttribute("class", "tl-grip")
      for dy <- List(-4, 0, 4) do
        val dot = dom.document.createElementNS(ns, "circle")
        dot.setAttribute("cx", (HANDLE_W / 2).toString)
        dot.setAttribute("cy", ((H / 2) + dy).toString)
        dot.setAttribute("r", "1")
        lGrip.appendChild(dot)
      svg.appendChild(lGrip)

      // Right handle
      val rHandle = dom.document.createElementNS(ns, "rect")
      rHandle.setAttribute("class", "tl-handle tl-handle-right")
      rHandle.setAttribute("x", (w - HANDLE_W).toString)
      rHandle.setAttribute("y", "0")
      rHandle.setAttribute("width", HANDLE_W.toString)
      rHandle.setAttribute("height", H.toString)
      rHandle.setAttribute("rx", "3")
      svg.appendChild(rHandle)

      val rGrip = dom.document.createElementNS(ns, "g")
      rGrip.setAttribute("class", "tl-grip")
      for dy <- List(-4, 0, 4) do
        val dot = dom.document.createElementNS(ns, "circle")
        dot.setAttribute("cx", (w - HANDLE_W / 2).toString)
        dot.setAttribute("cy", ((H / 2) + dy).toString)
        dot.setAttribute("r", "1")
        rGrip.appendChild(dot)
      svg.appendChild(rGrip)

      // Also render context strip when main strip updates
      renderContext()
