package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Timeline strip (plan section 1.7). The VISIBLE SPAN IS THE FILTER: whatever the strip shows is
  * the period sent to the server. Bars = how many sources match each adaptive time bucket.
  *
  * Ported from timeline.js. */
object TimelineView:

  private val H         = 58
  private val BASE      = 42 // y of the axis inside the SVG
  private val MIN_SPAN  = 10L
  private val ZOOM      = 2.0

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

  /** A render-tick counter bumped on every state change that should redraw the SVG. */
  private val renderTick = Var(0)

  private case class DragInfo(x: Double, cur: Double)

  private var reqId: Int          = 0
  private var timer: Int          = 0
  private var svgEl: dom.Element  = uninitialized

  /** Current from/to of the visible span. */
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

  // ── drawing helpers ───────────────────────────────────────────────────────────────────────────

  private def svgWidth(): Double =
    if svgEl == null then 600.0
    else
      val w = svgEl.asInstanceOf[dom.SVGElement].clientWidth.toDouble
      if w > 0 then w
      else
        val r = svgEl.getBoundingClientRect()
        if r.width > 0 then r.width else 600.0

  private def years(): Double = spanTo.now().toDouble + 1 - spanFrom.now().toDouble
  private def xOf(y: Double, w: Double): Double  = (y - spanFrom.now().toDouble) / years() * w
  private def yearAt(px: Double, w: Double): Long = Math.round(spanFrom.now().toDouble + (px / w) * years())

  // ── public entry point ────────────────────────────────────────────────────────────────────────

  def init(config: UiConfig): HtmlElement =
    limits = config.time
    val sf = UiState.from.now().getOrElse(config.time.defaultFrom)
    val st = UiState.to.now().getOrElse(config.time.defaultTo)
    val (cf, ct) = clamp(sf, st)
    spanFrom.set(cf)
    spanTo.set(ct)
    if cf != sf || ct != st then UiState.set(("from", cf), ("to", ct))()

    // Restore fold state from localStorage.
    try folded.set(dom.window.localStorage.getItem("histdb.tlFolded") == "1")
    catch case _: Exception => ()

    val fromInput = input(
      cls   := "tl-year",
      idAttr := "tl-from",
      title := "from - e.g. 3300 BCE, -3300, c. 1500 BC",
      controlled(
        value <-- spanFrom.signal.map(Fmt.yr),
        onInput.mapToValue --> Observer.empty
      )
    )
    val toInput = input(
      cls   := "tl-year",
      idAttr := "tl-to",
      title := "to - e.g. 1200 CE, -1200",
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
          case Some(b) => s"$b-yr buckets . sources starting . peak $maxCount"
      }
    )

    val badgeEl = htmlTag("span")(
      cls := "tl-badge",
      idAttr := "tl-badge",
      hidden <-- failedAt.signal.map(_.isEmpty),
      child.text <-- failedAt.signal.map(_.getOrElse(""))
    )

    val tlDiv = div(
      cls := "tl",
      cls("tl-folded") <-- folded.signal,
      div(cls := "tl-ctl",
        label(forId := "tl-from", "span"),
        fromInput,
        htmlTag("span")(cls := "tl-arrow", "→"),
        toInput,
        button(cls := "tl-btn", title := "zoom in x2 (around the centre)",
          aria.label := "zoom in", "+",
          onClick --> { _ =>
            val (zf, zt) = zoomSpan(1.0 / ZOOM, None)
            setSpan(zf, zt)
          }),
        button(cls := "tl-btn", title := "zoom out x2 (around the centre)",
          aria.label := "zoom out", "−",
          onClick --> { _ =>
            val (zf, zt) = zoomSpan(ZOOM, None)
            setSpan(zf, zt)
          }),
        button(cls := "tl-btn", title := "reset to the default span",
          aria.label := "reset the span", "⟲",
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
        badgeEl,
        htmlTag("span")(cls := "tl-hint muted", "+ - zoom . reset . drag to zoom in . wheel to zoom")
      ),
      // The SVG strip, hidden when folded (CSS handles display, but we still mount it).
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

          // Imperatively render the SVG content.
          renderImperative()

          // Re-render on state ticks.
          renderTick.signal.foreach(_ => renderImperative())(using ctx.owner)

          // Mouse wheel zoom.
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

          // Pointer drag = zoom in.
          svgRoot.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
            if e.button == 0 then
              val rect = svgRoot.getBoundingClientRect()
              val x    = e.clientX - rect.left
              dragState.set(Some(DragInfo(x, x)))
              svgRoot.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
              bumpRender()
          })
          svgRoot.addEventListener("pointermove", { (e: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              val rect = svgRoot.getBoundingClientRect()
              dragState.set(Some(d.copy(cur = e.clientX - rect.left)))
              bumpRender()
            }
          })
          val endDrag: js.Function1[dom.PointerEvent, Unit] = { (_: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              dragState.set(None)
              if Math.abs(d.cur - d.x) >= 3 then
                val w = svgWidth()
                val y0 = yearAt(Math.min(d.x, d.cur), w)
                val y1 = yearAt(Math.max(d.x, d.cur), w)
                setSpan(y0, y1)
              else bumpRender()
            }
          }
          svgRoot.addEventListener("pointerup", endDrag)
          svgRoot.addEventListener("pointercancel", endDrag)

          // ResizeObserver.
          if js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined" then
            val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(
              { (_: js.Any) => renderImperative() }: js.Function1[js.Any, Unit]
            )
            obs.observe(svgRoot)

          // Commit year input boxes on change/Enter.
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

          // Follow state changes from other modules (hash restore, etc.).
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

  /** Imperatively update the SVG element's children -- we keep a stable SVG node for pointer events
    * and redraw its content on every state tick. */
  private def renderImperative(): Unit =
    if svgEl == null then ()
    else
      val svg = svgEl
      val w   = svgWidth()
      svg.setAttribute("viewBox", s"0 0 ${w.toInt} $H")
      val ld = loading.now()
      if ld then svg.classList.add("tl-loading")
      else svg.classList.remove("tl-loading")

      // Clear children.
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
            if x >= 0 && x <= w - 4 then
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
      endTxt.setAttribute("x", (w - 2).toString)
      endTxt.setAttribute("y", (H - 2).toString)
      endTxt.textContent = endLabel
      svg.appendChild(endTxt)

      // Drag overlay.
      dragState.now().foreach { d =>
        val dx0 = Math.min(d.x, d.cur)
        val dx1 = Math.max(d.x, d.cur)
        val dragRect = dom.document.createElementNS(ns, "rect")
        dragRect.setAttribute("class", "tl-drag")
        dragRect.setAttribute("x", dx0.toString)
        dragRect.setAttribute("y", "0")
        dragRect.setAttribute("width", Math.max(1.0, dx1 - dx0).toString)
        dragRect.setAttribute("height", BASE.toString)
        svg.appendChild(dragRect)
      }
