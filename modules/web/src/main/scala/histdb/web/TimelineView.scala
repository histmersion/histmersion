package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import histdb.web.ui.{Layout, AggregateBy, AggregateUnit}
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TimelineView:

  private val OH        = Layout.overviewHeight.value
  private val O_BASE    = Layout.overviewBase.value
  private val DH        = Layout.detailHeight.value
  private val D_BASE    = Layout.detailBase.value
  private val MIN_SPAN  = Layout.minSpan
  private val HANDLE_W  = Layout.handleWidth
  private val ZOOM      = 2.0

  // ── state ─────────────────────────────────────────────────────────────────────────────────────
  private var limits: TimeConfig = uninitialized

  private val spanFrom  = Var(0L)
  private val spanTo    = Var(0L)
  private val detailBars = Var(List.empty[TimeBucket])
  private val overviewBars = Var(List.empty[TimeBucket])
  private val loading   = Var(false)
  private val failedAt  = Var(Option.empty[String])
  private val periodPresets = Var(List.empty[HistoricalPeriod])
  private val showPresets = Var(false)

  private val renderTick = Var(0)

  private var reqId: Int           = 0
  private var ovReqId: Int         = 0
  private var timer: Int           = 0
  private var overviewSvg: dom.Element = uninitialized
  private var detailSvg: dom.Element   = uninitialized

  private enum DragKind:
    case LeftHandle, RightHandle, PanRange, ZoomDetail

  private case class DragInfo(
    startX: Double, curX: Double,
    kind: DragKind,
    origFrom: Long, origTo: Long
  )
  private val dragState = Var(Option.empty[DragInfo])

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

  private def setSpan(from: Long, to: Long, push: Boolean = true): Unit =
    val (nf, nt) = clamp(from, to)
    if nf == spanFrom.now() && nt == spanTo.now() then ()
    else
      spanFrom.set(nf)
      spanTo.set(nt)
      bumpRender()
      scheduleDetail()
      if push then UiState.set(("from", nf), ("to", nt))()

  private def bumpRender(): Unit = renderTick.update(_ + 1)

  // ── data loading ──────────────────────────────────────────────────────────────────────────────

  private def scheduleDetail(): Unit =
    dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => loadDetail(), 150)

  private[web] val MAX_BUCKETS = Layout.maxBuckets

  private[web] def bucketSizeFor(spanYears: Long, pixelWidth: Double): Long =
    val byPixels = Math.ceil(spanYears.toDouble / Math.max(pixelWidth, 100)).toLong
    val byLimit  = Math.ceil(spanYears.toDouble / Layout.maxBuckets).toLong
    Math.max(1L, Math.max(byPixels, byLimit))

  private def loadOverview(pixelWidth: Double): Unit =
    ovReqId += 1
    val myId = ovReqId
    val f = UiState.toFilter().copy(period = Some(Period(limits.min, limits.max)))
    val span = limits.max - limits.min
    val sz = bucketSizeFor(span, pixelWidth)
    ApiClient.aggregate(Api.AggregateQuery(Some(AggregateBy.Time.param), AggregateUnit.Sources.param, f, size = Some(sz))).onComplete { result =>
      if myId == ovReqId then
        result.fold(
          e => dom.console.error("overview load failed", e.getMessage),
          h => {
            overviewBars.set(h.buckets)
            bumpRender()
          }
        )
    }

  private def loadDetail(): Unit =
    reqId += 1
    val myId = reqId
    loading.set(true)
    bumpRender()
    val sf = spanFrom.now()
    val st = spanTo.now()
    val f = UiState.toFilter().copy(period = Some(Period(sf, st)))
    val pw = if detailSvg != null then elWidth(detailSvg) else 600.0
    val sz = bucketSizeFor(st - sf, pw)
    ApiClient.aggregate(Api.AggregateQuery(Some(AggregateBy.Time.param), AggregateUnit.Sources.param, f, size = Some(sz))).onComplete { result =>
      if myId == reqId then
        result.fold(
          _ => failedAt.set(Some("counts unavailable")),
          h => {
            detailBars.set(h.buckets)
            failedAt.set(None)
          }
        )
        loading.set(false)
        bumpRender()
    }

  private def loadPresets(): Unit =
    if periodPresets.now().isEmpty then
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

  private def niceInterval(raw: Double): Double =
    val candidates = List(1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 250.0, 500.0,
      1000.0, 2000.0, 2500.0, 5000.0, 10000.0)
    candidates.find(_ >= raw).getOrElse(Math.pow(10, Math.ceil(Math.log10(raw))))

  private def drawAreaCurve(svg: dom.Element, bs: List[TimeBucket], from: Long, to: Long,
      w: Double, h: Double, baseY: Double, gradId: String): Unit =
    val ns = "http://www.w3.org/2000/svg"
    val maxCount = if bs.isEmpty then 0 else bs.map(_.count).max
    if bs.nonEmpty && maxCount > 0 then
      val defs = dom.document.createElementNS(ns, "defs")
      val grad = dom.document.createElementNS(ns, "linearGradient")
      grad.setAttribute("id", gradId)
      grad.setAttribute("x1", "0%"); grad.setAttribute("x2", "100%")
      grad.setAttribute("y1", "0%"); grad.setAttribute("y2", "0%")
      for i <- 0 until 10 do
        val stop = dom.document.createElementNS(ns, "stop")
        stop.setAttribute("offset", s"${(i.toDouble / 9 * 100).round}%")
        stop.setAttribute("stop-color", Fmt.color(i.toDouble / 9))
        grad.appendChild(stop)
      defs.appendChild(grad)
      svg.appendChild(defs)

      val span = (to - from).toDouble.max(1)
      def xOf(yr: Double): Double = (yr - from.toDouble) / span * w

      case class Pt(x: Double, y: Double, b: TimeBucket)
      val pts = bs.map { b =>
        val mid = (b.start.toDouble + b.end.toDouble) / 2
        val x = xOf(mid)
        val barH = (b.count.toDouble / maxCount) * (baseY - 4)
        Pt(x, baseY - barH, b)
      }

      val pathD = new StringBuilder
      pathD.append(s"M ${pts.head.x} $baseY L ${pts.head.x} ${pts.head.y}")
      if pts.length == 1 then
        pathD.append(s" L ${pts.head.x + 1} ${pts.head.y} L ${pts.head.x + 1} $baseY")
      else
        for i <- 1 until pts.length do
          val p0 = pts(i - 1); val p1 = pts(i)
          val cx = (p0.x + p1.x) / 2
          pathD.append(s" C $cx ${p0.y} $cx ${p1.y} ${p1.x} ${p1.y}")
        pathD.append(s" L ${pts.last.x} $baseY")
      pathD.append(" Z")

      val path = dom.document.createElementNS(ns, "path")
      path.setAttribute("class", "tl-area")
      path.setAttribute("d", pathD.toString)
      path.setAttribute("fill", s"url(#$gradId)")
      path.setAttribute("opacity", "0.85")
      svg.appendChild(path)

      val line = dom.document.createElementNS(ns, "path")
      val lineD = new StringBuilder
      lineD.append(s"M ${pts.head.x} ${pts.head.y}")
      for i <- 1 until pts.length do
        val p0 = pts(i - 1); val p1 = pts(i)
        val cx = (p0.x + p1.x) / 2
        lineD.append(s" C $cx ${p0.y} $cx ${p1.y} ${p1.x} ${p1.y}")
      line.setAttribute("class", "tl-area-line")
      line.setAttribute("d", lineD.toString)
      line.setAttribute("fill", "none")
      line.setAttribute("stroke", s"url(#$gradId)")
      line.setAttribute("stroke-width", "1.5")
      svg.appendChild(line)

      for pt <- pts if pt.b.count > 0 do
        val dot = dom.document.createElementNS(ns, "circle")
        dot.setAttribute("class", "tl-area-dot")
        dot.setAttribute("cx", pt.x.toString)
        dot.setAttribute("cy", pt.y.toString)
        dot.setAttribute("r", "2")
        val t = (((pt.b.start + pt.b.end).toDouble / 2) - from.toDouble) / span
        dot.setAttribute("fill", Fmt.color(t.max(0).min(1)))
        val titleEl = dom.document.createElementNS(ns, "title")
        titleEl.textContent = s"${Fmt.yr(pt.b.start)}–${Fmt.yr(pt.b.end)}: ${pt.b.count} source(s)"
        dot.appendChild(titleEl)
        svg.appendChild(dot)

  // ── overview rendering ────────────────────────────────────────────────────────────────────────

  private def renderOverview(): Unit =
    if overviewSvg != null then
      val svg = overviewSvg
      val w = elWidth(svg)
      dom.console.log(s"renderOverview: w=$w, bars=${overviewBars.now().length}")
      svg.setAttribute("viewBox", s"0 0 ${w.toInt} $OH")
      while svg.firstChild != null do svg.removeChild(svg.firstChild)
      val ns = "http://www.w3.org/2000/svg"
      val bs = overviewBars.now()
      val sf = spanFrom.now()
      val st = spanTo.now()
      drawAreaCurve(svg, bs, limits.min, limits.max, w, O_BASE.toDouble, (O_BASE - 2).toDouble, "ov-grad")
      val fullSpan = (limits.max - limits.min).toDouble.max(1)
      val selX0 = Math.max(0.0, (sf - limits.min).toDouble / fullSpan * w)
      val selX1 = Math.min(w, (st - limits.min).toDouble / fullSpan * w)
      val dimLeft = dom.document.createElementNS(ns, "rect")
      dimLeft.setAttribute("class", "tl-ov-dim")
      dimLeft.setAttribute("x", "0"); dimLeft.setAttribute("y", "0")
      dimLeft.setAttribute("width", selX0.toString); dimLeft.setAttribute("height", O_BASE.toString)
      svg.appendChild(dimLeft)
      val dimRight = dom.document.createElementNS(ns, "rect")
      dimRight.setAttribute("class", "tl-ov-dim")
      dimRight.setAttribute("x", selX1.toString); dimRight.setAttribute("y", "0")
      dimRight.setAttribute("width", (w - selX1).toString); dimRight.setAttribute("height", O_BASE.toString)
      svg.appendChild(dimRight)
      for ex <- List(selX0, selX1) do
        val edge = dom.document.createElementNS(ns, "line")
        edge.setAttribute("class", "tl-ov-edge")
        edge.setAttribute("x1", ex.toString); edge.setAttribute("y1", "0")
        edge.setAttribute("x2", ex.toString); edge.setAttribute("y2", O_BASE.toString)
        svg.appendChild(edge)
      val lh = dom.document.createElementNS(ns, "rect")
      lh.setAttribute("class", "tl-ov-handle")
      lh.setAttribute("x", (selX0 - HANDLE_W / 2).toString); lh.setAttribute("y", "0")
      lh.setAttribute("width", HANDLE_W.toString); lh.setAttribute("height", O_BASE.toString)
      lh.setAttribute("rx", "2")
      svg.appendChild(lh)
      val rh = dom.document.createElementNS(ns, "rect")
      rh.setAttribute("class", "tl-ov-handle")
      rh.setAttribute("x", (selX1 - HANDLE_W / 2).toString); rh.setAttribute("y", "0")
      rh.setAttribute("width", HANDLE_W.toString); rh.setAttribute("height", O_BASE.toString)
      rh.setAttribute("rx", "2")
      svg.appendChild(rh)
      val fromLabel = dom.document.createElementNS(ns, "text")
      fromLabel.setAttribute("class", "tl-ov-label")
      fromLabel.setAttribute("x", (selX0 + 3).toString)
      fromLabel.setAttribute("y", (O_BASE + 11).toString)
      fromLabel.textContent = Fmt.yr(sf)
      svg.appendChild(fromLabel)
      val toLabel = dom.document.createElementNS(ns, "text")
      toLabel.setAttribute("class", "tl-ov-label tl-ov-label-end")
      toLabel.setAttribute("x", (selX1 - 3).toString)
      toLabel.setAttribute("y", (O_BASE + 11).toString)
      toLabel.textContent = Fmt.yr(st)
      svg.appendChild(toLabel)

  // ── detail rendering ──────────────────────────────────────────────────────────────────────────

  private def renderDetail(): Unit =
    if detailSvg != null then
      val svg = detailSvg
      val w = elWidth(svg)
      svg.setAttribute("viewBox", s"0 0 ${w.toInt} $DH")
      val ld = loading.now()
      if ld then svg.classList.add("tl-loading")
      else svg.classList.remove("tl-loading")
      while svg.firstChild != null do svg.removeChild(svg.firstChild)
      val sf = spanFrom.now()
      val st = spanTo.now()
      val bs = detailBars.now()
      val ns = "http://www.w3.org/2000/svg"
      drawAreaCurve(svg, bs, sf, st, w, DH.toDouble, D_BASE.toDouble, "dt-grad")
      val axisLine = dom.document.createElementNS(ns, "line")
      axisLine.setAttribute("class", "tl-axis")
      axisLine.setAttribute("x1", "0"); axisLine.setAttribute("y1", (D_BASE + 0.5).toString)
      axisLine.setAttribute("x2", w.toString); axisLine.setAttribute("y2", (D_BASE + 0.5).toString)
      svg.appendChild(axisLine)
      val spanYears = (st - sf).toDouble.max(1)
      val targetTicks = Math.max(3, (w / 120).toInt)
      val rawInterval = spanYears / targetTicks
      val major = niceInterval(rawInterval)
      val minor = major / (if major >= 1000 then 5 else if major >= 100 then 5 else if major >= 10 then 5 else 1)
      val textWidth = (label: String) => label.length * 6.2
      def xOf(yr: Double): Double = (yr - sf.toDouble) / spanYears * w
      val firstMajor = Math.ceil(sf.toDouble / major).toLong * major.toLong
      val firstMinor = Math.ceil(sf.toDouble / minor).toLong * minor.toLong
      var lastLabelEnd = 0.0
      if minor < major then
        var yr = firstMinor
        while yr <= st do
          if yr % major.toLong != 0 then
            val x = xOf(yr.toDouble)
            if x >= 0 && x <= w then
              val tick = dom.document.createElementNS(ns, "line")
              tick.setAttribute("class", "tl-tick tl-tick-minor")
              tick.setAttribute("x1", x.toString); tick.setAttribute("y1", (D_BASE + 1).toString)
              tick.setAttribute("x2", x.toString); tick.setAttribute("y2", (D_BASE + 3).toString)
              svg.appendChild(tick)
          yr += minor.toLong
      var yr = firstMajor
      while yr <= st do
        val x = xOf(yr.toDouble)
        if x >= 0 && x <= w - 4 then
          val tick = dom.document.createElementNS(ns, "line")
          tick.setAttribute("class", "tl-tick")
          tick.setAttribute("x1", x.toString); tick.setAttribute("y1", D_BASE.toString)
          tick.setAttribute("x2", x.toString); tick.setAttribute("y2", (D_BASE + 5).toString)
          svg.appendChild(tick)
          val lbl = Fmt.yr(yr)
          val lblW = textWidth(lbl)
          if x + 3 > lastLabelEnd then
            val txt = dom.document.createElementNS(ns, "text")
            txt.setAttribute("class", "tl-label")
            txt.setAttribute("x", (x + 3).toString)
            txt.setAttribute("y", (DH - 2).toString)
            txt.textContent = lbl
            svg.appendChild(txt)
            lastLabelEnd = x + 3 + lblW + 8
        yr += major.toLong
      dragState.now().foreach { d =>
        if d.kind == DragKind.ZoomDetail then
          val dx0 = Math.min(d.startX, d.curX)
          val dx1 = Math.max(d.startX, d.curX)
          if Math.abs(d.curX - d.startX) >= 3 then
            val dragRect = dom.document.createElementNS(ns, "rect")
            dragRect.setAttribute("class", "tl-drag")
            dragRect.setAttribute("x", dx0.toString); dragRect.setAttribute("y", "0")
            dragRect.setAttribute("width", Math.max(1.0, dx1 - dx0).toString)
            dragRect.setAttribute("height", D_BASE.toString)
            svg.appendChild(dragRect)
      }
      TimelinePlayback.playheadNow.foreach { ph =>
        if ph >= sf && ph <= st then
          val px = xOf(ph.toDouble)
          val line = dom.document.createElementNS(ns, "line")
          line.setAttribute("class", "tl-playhead")
          line.setAttribute("x1", px.toString); line.setAttribute("y1", "0")
          line.setAttribute("x2", px.toString); line.setAttribute("y2", D_BASE.toString)
          svg.appendChild(line)
          val label = dom.document.createElementNS(ns, "text")
          label.setAttribute("class", "tl-playhead-label")
          label.setAttribute("x", (px + 3).toString)
          label.setAttribute("y", "10")
          label.textContent = Fmt.yr(ph)
          svg.appendChild(label)
      }

  // ── public entry point ────────────────────────────────────────────────────────────────────────

  def init(config: UiConfig): HtmlElement =
    limits = config.time
    val sf = UiState.from.now().getOrElse(config.time.defaultFrom)
    val st = UiState.to.now().getOrElse(config.time.defaultTo)
    val (cf, ct) = clamp(sf, st)
    spanFrom.set(cf)
    spanTo.set(ct)
    if cf != sf || ct != st then UiState.set(("from", cf), ("to", ct))()

    val presetsDropdown = div(
      cls := "tl-presets-dropdown",
      hidden <-- showPresets.signal.map(!_),
      children <-- periodPresets.signal.map { ps =>
        if ps.isEmpty then List(div(cls := "tl-preset-loading", "no period presets available"))
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

    val badgeEl = htmlTag("span")(
      cls := "tl-badge",
      hidden <-- failedAt.signal.map(_.isEmpty),
      child.text <-- failedAt.signal.map(_.getOrElse(""))
    )

    div(
      cls := "tl",
      // ── Overview bar (full range, draggable handles) ──────────────────────────────────────
      div(
        cls := "tl-overview-wrap",
        onMountCallback { ctx =>
          val ns = "http://www.w3.org/2000/svg"
          val svg = dom.document.createElementNS(ns, "svg").asInstanceOf[dom.SVGElement]
          svg.setAttribute("class", "tl-overview-svg")
          svg.setAttribute("preserveAspectRatio", "none")
          svg.setAttribute("width", "100%")
          svg.setAttribute("height", OH.toString)
          overviewSvg = svg
          ctx.thisNode.ref.appendChild(svg)

          renderOverview()
          renderTick.signal.foreach(_ => renderOverview())(using ctx.owner)
          loadOverview(elWidth(svg))

          svg.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
            if e.button == 0 then
              val rect = svg.getBoundingClientRect()
              val w = if rect.width > 0 then rect.width else 600.0
              val x = e.clientX - rect.left
              val fullSpan = (limits.max - limits.min).toDouble.max(1)
              val sf = spanFrom.now()
              val st = spanTo.now()
              val selX0 = (sf - limits.min).toDouble / fullSpan * w
              val selX1 = (st - limits.min).toDouble / fullSpan * w

              val kind =
                if Math.abs(x - selX0) <= HANDLE_W then DragKind.LeftHandle
                else if Math.abs(x - selX1) <= HANDLE_W then DragKind.RightHandle
                else if x > selX0 && x < selX1 then DragKind.PanRange
                else
                  val clickYear = limits.min.toDouble + (x / w) * fullSpan
                  val half = (st - sf).toDouble / 2
                  setSpan(Math.round(clickYear - half), Math.round(clickYear + half))
                  DragKind.PanRange

              dragState.set(Some(DragInfo(x, x, kind, sf, st)))
              svg.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
          })

          svg.addEventListener("pointermove", { (e: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              val rect = svg.getBoundingClientRect()
              val w = if rect.width > 0 then rect.width else 600.0
              val cur = e.clientX - rect.left
              val fullSpan = (limits.max - limits.min).toDouble.max(1)
              val pxToYear = fullSpan / w

              d.kind match
                case DragKind.LeftHandle =>
                  val yearDelta = ((cur - d.startX) * pxToYear).toLong
                  val nf = Math.max(limits.min, Math.min(d.origTo - MIN_SPAN, d.origFrom + yearDelta))
                  setSpan(nf, d.origTo, push = false)
                case DragKind.RightHandle =>
                  val yearDelta = ((cur - d.startX) * pxToYear).toLong
                  val nt = Math.min(limits.max, Math.max(d.origFrom + MIN_SPAN, d.origTo + yearDelta))
                  setSpan(d.origFrom, nt, push = false)
                case DragKind.PanRange =>
                  val yearDelta = ((cur - d.startX) * pxToYear).toLong
                  val rangeWidth = d.origTo - d.origFrom
                  var nf = d.origFrom + yearDelta
                  var nt = d.origTo + yearDelta
                  if nf < limits.min then { nf = limits.min; nt = nf + rangeWidth }
                  if nt > limits.max then { nt = limits.max; nf = nt - rangeWidth }
                  setSpan(nf, nt, push = false)
                case _ => ()
            }
          })

          val endDrag: js.Function1[dom.PointerEvent, Unit] = { (_: dom.PointerEvent) =>
            if dragState.now().isDefined then
              dragState.set(None)
              UiState.set(("from", spanFrom.now()), ("to", spanTo.now()))()
          }
          svg.addEventListener("pointerup", endDrag)
          svg.addEventListener("pointercancel", endDrag)

          if js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined" then
            val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(
              { (_: js.Any) => loadOverview(elWidth(svg)); renderOverview() }: js.Function1[js.Any, Unit]
            )
            obs.observe(svg)
        }
      ),
      // ── Detail bar (zoomed into selected range) ───────────────────────────────────────────
      div(
        cls := "tl-detail-wrap",
        onMountCallback { ctx =>
          val ns = "http://www.w3.org/2000/svg"
          val svg = dom.document.createElementNS(ns, "svg").asInstanceOf[dom.SVGElement]
          svg.setAttribute("class", "tl-detail-svg")
          svg.setAttribute("preserveAspectRatio", "none")
          svg.setAttribute("width", "100%")
          svg.setAttribute("height", DH.toString)
          detailSvg = svg
          ctx.thisNode.ref.appendChild(svg)

          renderDetail()
          renderTick.signal.foreach(_ => renderDetail())(using ctx.owner)

          svg.addEventListener("wheel", { (e: dom.WheelEvent) =>
            e.preventDefault()
            if e.deltaY != 0 then
              val rect = svg.getBoundingClientRect()
              val px = e.clientX - rect.left
              val w = if rect.width > 0 then rect.width else 600.0
              val spanYears = (spanTo.now() - spanFrom.now()).toDouble
              val anchor = spanFrom.now().toDouble + (px / w) * spanYears
              val factor = if e.deltaY > 0 then ZOOM else 1.0 / ZOOM
              val nf = Math.round(anchor - (anchor - spanFrom.now().toDouble) * factor)
              val nt = Math.round(anchor + (spanTo.now().toDouble - anchor) * factor)
              setSpan(nf, nt)
          }, js.Dynamic.literal(passive = false).asInstanceOf[dom.EventListenerOptions])

          svg.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
            if e.button == 0 then
              val rect = svg.getBoundingClientRect()
              val x = e.clientX - rect.left
              dragState.set(Some(DragInfo(x, x, DragKind.ZoomDetail, spanFrom.now(), spanTo.now())))
              svg.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
          })

          svg.addEventListener("pointermove", { (e: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              if d.kind == DragKind.ZoomDetail then
                val rect = svg.getBoundingClientRect()
                val cur = e.clientX - rect.left
                dragState.set(Some(d.copy(curX = cur)))
                bumpRender()
            }
          })

          val endDrag: js.Function1[dom.PointerEvent, Unit] = { (_: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              if d.kind == DragKind.ZoomDetail then
                dragState.set(None)
                if Math.abs(d.curX - d.startX) >= 3 then
                  val w = elWidth(detailSvg)
                  val spanYears = (d.origTo - d.origFrom).toDouble
                  val y0 = d.origFrom + (Math.min(d.startX, d.curX) / w * spanYears).toLong
                  val y1 = d.origFrom + (Math.max(d.startX, d.curX) / w * spanYears).toLong
                  setSpan(y0, y1)
                else bumpRender()
            }
          }
          svg.addEventListener("pointerup", endDrag)
          svg.addEventListener("pointercancel", endDrag)

          if js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined" then
            val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(
              { (_: js.Any) => renderDetail() }: js.Function1[js.Any, Unit]
            )
            obs.observe(svg)

          TimelinePlayback.playheadSignal.foreach(_ => renderDetail())(using ctx.owner)

          UiState.signal.foreach { _ =>
            val uf = UiState.from.now().getOrElse(spanFrom.now())
            val ut = UiState.to.now().getOrElse(spanTo.now())
            if uf != spanFrom.now() || ut != spanTo.now() then
              val (cf2, ct2) = clamp(uf, ut)
              spanFrom.set(cf2)
              spanTo.set(ct2)
              bumpRender()
            scheduleDetail()
          }(using ctx.owner)

          loadDetail()
        }
      ),
      // ── Controls ──────────────────────────────────────────────────────────────────────────
      div(cls := "tl-ctl",
        div(cls := "tl-preset-wrap",
          button(cls := "tl-btn tl-btn-presets", title := "period presets",
            aria.label := "period presets", "⏱",
            onClick --> { _ => loadPresets(); showPresets.update(!_) }
          ),
          presetsDropdown
        ),
        button(cls := "tl-btn", title := "zoom in ×2", aria.label := "zoom in", "+",
          onClick --> { _ =>
            val sf = spanFrom.now().toDouble; val st = spanTo.now().toDouble
            val mid = (sf + st) / 2; val half = (st - sf) / 4
            setSpan(Math.round(mid - half), Math.round(mid + half))
          }),
        button(cls := "tl-btn", title := "zoom out ×2", aria.label := "zoom out", "−",
          onClick --> { _ =>
            val sf = spanFrom.now().toDouble; val st = spanTo.now().toDouble
            val mid = (sf + st) / 2; val half = (st - sf)
            setSpan(Math.round(mid - half), Math.round(mid + half))
          }),
        button(cls := "tl-btn tl-reset", title := "reset to default span", aria.label := "reset", "⟲",
          onClick --> { _ => setSpan(config.time.defaultFrom, config.time.defaultTo) }),
        TimelinePlayback.element(config),
        badgeEl
      )
    )
