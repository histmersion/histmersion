package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import com.histmersion.web.ui.{Layout, AggregateBy, AggregateUnit}
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

  /** The span shown IS `UiState.from/to` (no copy): a drag writes the fields live without
    * committing, and commits once, on release. */
  private def spanFrom: Long = UiState.from.now().getOrElse(limits.defaultFrom)
  private def spanTo: Long   = UiState.to.now().getOrElse(limits.defaultTo)
  private val detailBars = Var(List.empty[TimeBucket])
  private val overviewBars = Var(List.empty[TimeBucket])
  private val loading   = Var(false)
  private val failedAt  = Var(Option.empty[String])
  private val periodPresets = Var(List.empty[HistoricalPeriod])
  private val showPresets = Var(false)
  /** What the map showed when the preset menu was opened: its region's presets come first. */
  private val presetView = Var(Option.empty[BBox])

  private val renderTick = Var(0)

  // the detail strip's request a late answer must belong to
  private val detailLatest         = Latest()
  /** Whether the detail strip is unfolded in the narrow layout (a wide screen always shows it). */
  private val detailOpen           = Var(false)
  // the overview's: only the newest answer is drawn
  private val overviewLatest       = Latest()
  private var timer: Int           = 0
  private var overviewSvg: dom.Element = uninitialized
  private var detailSvg: dom.Element   = uninitialized

  // Persistent layers, bottom to top; each render replaces only its own layer's children.
  private var ovCurveLayer: dom.Element   = null
  private var ovSelLayer: dom.Element     = null
  private var dtUnderLayer: dom.Element   = null
  private var dtCurveLayer: dom.Element   = null
  private var dtAxisLayer: dom.Element    = null
  private var dtOverlayLayer: dom.Element = null
  private var lastOvWidth: Double         = 0
  private var lastDtWidth: Double         = 0
  private var lastOvBarsLen: Int          = -1
  private var lastDtBarsLen: Int          = -1
  private var ovResizeRaf: Int            = 0
  // `var`s: the strips' size observers, disconnected when their strip leaves the page
  private var ovObserver: Option[js.Dynamic] = None
  private var dtObserver: Option[js.Dynamic] = None
  private var dtResizeRaf: Int            = 0
  /** The span the current `detailBars` were fetched for — not the span on screen. */
  private var dtBarsRange: (Long, Long)   = (0L, 0L)
  private var lastOvFilterKey: String     = ""
  /** What the detail curve layer was drawn for: (from, to, width). */
  private var dtDrawn: (Long, Long, Double) = (0L, 0L, 0.0)

  private enum DragKind:
    case LeftHandle, RightHandle, PanRange, ZoomDetail

  private case class DragInfo(
    startX: Double, curX: Double,
    kind: DragKind,
    origFrom: Long, origTo: Long
  )
  private val dragState = Var(Option.empty[DragInfo])

  def currentSpan: (Long, Long) = (spanFrom, spanTo)

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
    if nf == spanFrom && nt == spanTo then ()
    else
      UiState.from.set(Some(nf))
      UiState.to.set(Some(nt))
      bumpRender()
      scheduleDetail()
      App.quickRenderIfCached(nf, nt)
      if push then UiState.set(UiState.from := Some(nf), UiState.to := Some(nt))()

  private def bumpRender(): Unit = renderTick.update(_ + 1)

  // ── data loading ──────────────────────────────────────────────────────────────────────────────

  private def scheduleDetail(): Unit =
    dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => loadDetail(), UiState.reloadDelay(150))

  /** What the detail strip was last asked for: the filter over the span on screen. */
  private var lastDtKey: String = ""
  private def detailKey(): String = UiState.toFilter().copy(period = Some(Period(spanFrom, spanTo))).toString

  private[web] val MAX_BUCKETS = Layout.maxBuckets

  private[web] def bucketSizeFor(spanYears: Long, pixelWidth: Double): Long =
    val byPixels = Math.ceil(spanYears.toDouble / Math.max(pixelWidth, 100)).toLong
    val byLimit  = Math.ceil(spanYears.toDouble / Layout.maxBuckets).toLong
    Math.max(1L, Math.max(byPixels, byLimit))

  private def loadOverview(pixelWidth: Double): Unit = overview(pixelWidth)

  /** The overview follows every filter change — and every hovered suggestion — with ONE request in
    * flight and the newest change next (it fired one per change, undebounced: sweeping the pointer
    * down a suggestion list sent one per row). Only the newest answer is drawn. */
  private val overview = LatestOnly[Double]((pixelWidth, done) => {
    val ticket = overviewLatest.start()
    val f = UiState.toFilter().copy(period = Some(Period(limits.min, limits.max)))
    val span = limits.max - limits.min
    val sz = bucketSizeFor(span, pixelWidth)
    ApiClient.aggregate(Api.AggregateQuery(Some(AggregateBy.Time.param), AggregateUnit.Sources.param, f, size = Some(sz))).onComplete { result =>
      if overviewLatest.isCurrent(ticket) then
        result.fold(
          e => dom.console.error("overview load failed", e.getMessage),
          h => {
            overviewBars.set(h.buckets)
            bumpRender()
          }
        )
      done()
    }
  })

  private def loadDetail(): Unit =
    // a load now answers whatever was scheduled: the strip's mount scheduled one and loaded at once,
    // asking twice for the same strip on every page load
    dom.window.clearTimeout(timer)
    loading.set(true)
    bumpRender()
    val sf = spanFrom
    val st = spanTo
    val f = UiState.toFilter().copy(period = Some(Period(sf, st)))
    lastDtKey = f.toString
    val pw = if detailSvg != null then elWidth(detailSvg) else 600.0
    val sz = bucketSizeFor(st - sf, pw)
    val q = Api.AggregateQuery(Some(AggregateBy.Time.param), AggregateUnit.Sources.param, f, size = Some(sz))
    detailLatest.onCurrent(ApiClient.aggregate(q)) { result =>
      result.fold(
        _ => failedAt.set(Some("counts unavailable")),
        h => {
          dtBarsRange = (sf, st)
          lastDtBarsLen = -1
          detailBars.set(h.buckets)
          failedAt.set(None)
        }
      )
      loading.set(false)
      bumpRender()
    }

  /** The presets are the periods the seed files under a region heading ([[PresetMenu]]). */
  private def loadPresets(): Unit =
    if periodPresets.now().isEmpty then
      ApiClient.periods().foreach(ps => periodPresets.set(ps.filter(_.preset.isDefined)))

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
      w: Double, h: Double, baseY: Double, gradId: String, dots: Boolean = true): Unit =
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
      line.setAttribute("vector-effect", "non-scaling-stroke")
      svg.appendChild(line)

      for pt <- pts if dots && pt.b.count > 0 do
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

  private val svgNs = "http://www.w3.org/2000/svg"

  private def clearLayer(g: dom.Element): Unit =
    while g.firstChild != null do g.removeChild(g.firstChild)

  private def newLayer(svg: dom.Element, cls: String): dom.Element =
    val g = dom.document.createElementNS(svgNs, "g")
    g.setAttribute("class", cls)
    svg.appendChild(g)
    g

  /** Horizontal transform mapping a curve drawn for `drawn` onto `now`; identity when they match. */
  private[web] def realign(drawn: (Long, Long, Double), now: (Long, Long, Double)): (Double, Double) =
    val (df, dt, dw) = drawn
    val (nf, nt, nw) = now
    val dSpan = (dt - df).toDouble.max(1)
    val nSpan = (nt - nf).toDouble.max(1)
    if dw <= 0 then (0.0, 1.0)
    else
      val scale = (dSpan / nSpan) * (nw / dw)
      val tx = (df - nf).toDouble / nSpan * nw
      (tx, scale)

  private def applyTransform(g: dom.Element, tx: Double, scale: Double): Boolean =
    val identity = Math.abs(tx) < 0.5 && Math.abs(scale - 1) < 0.002
    if identity then g.removeAttribute("transform")
    else g.setAttribute("transform", s"translate($tx 0) scale($scale 1)")
    !identity

  private def renderOverviewCurve(): Unit =
    if overviewSvg != null then
      val w = elWidth(overviewSvg)
      val bs = overviewBars.now()
      if lastOvBarsLen != bs.length || lastOvWidth <= 0 then
        clearLayer(ovCurveLayer)
        drawAreaCurve(ovCurveLayer, bs, limits.min, limits.max, w, O_BASE.toDouble, (O_BASE - 2).toDouble, "ov-grad")
        lastOvWidth = w
        lastOvBarsLen = bs.length
      alignOverviewCurve(w)

  /** Stretch the drawn overview curve to the current width at once; the exact redraw follows. */
  private def alignOverviewCurve(w: Double): Unit =
    val stale = applyTransform(ovCurveLayer, 0, if lastOvWidth > 0 then w / lastOvWidth else 1)
    if stale then ovCurveLayer.classList.add("tl-stale") else ovCurveLayer.classList.remove("tl-stale")

  private def renderOverviewSelection(): Unit =
    if overviewSvg != null then
      val svg = overviewSvg
      val w = elWidth(svg)
      svg.setAttribute("viewBox", s"0 0 ${w.toInt} $OH")
      alignOverviewCurve(w)
      val ns = svgNs
      clearLayer(ovSelLayer)
      val g = ovSelLayer
      val sf = spanFrom
      val st = spanTo
      val fullSpan = (limits.max - limits.min).toDouble.max(1)
      val selX0 = Math.max(0.0, (sf - limits.min).toDouble / fullSpan * w)
      val selX1 = Math.min(w, (st - limits.min).toDouble / fullSpan * w)
      val dimLeft = dom.document.createElementNS(ns, "rect")
      dimLeft.setAttribute("class", "tl-ov-dim")
      dimLeft.setAttribute("x", "0"); dimLeft.setAttribute("y", "0")
      dimLeft.setAttribute("width", selX0.toString); dimLeft.setAttribute("height", O_BASE.toString)
      g.appendChild(dimLeft)
      val dimRight = dom.document.createElementNS(ns, "rect")
      dimRight.setAttribute("class", "tl-ov-dim")
      dimRight.setAttribute("x", selX1.toString); dimRight.setAttribute("y", "0")
      dimRight.setAttribute("width", (w - selX1).toString); dimRight.setAttribute("height", O_BASE.toString)
      g.appendChild(dimRight)
      for ex <- List(selX0, selX1) do
        val edge = dom.document.createElementNS(ns, "line")
        edge.setAttribute("class", "tl-ov-edge")
        edge.setAttribute("x1", ex.toString); edge.setAttribute("y1", "0")
        edge.setAttribute("x2", ex.toString); edge.setAttribute("y2", O_BASE.toString)
        g.appendChild(edge)
      for hx <- List(selX0, selX1) do
        val h = dom.document.createElementNS(ns, "rect")
        h.setAttribute("class", "tl-ov-handle")
        h.setAttribute("x", (hx - HANDLE_W / 2).toString); h.setAttribute("y", "0")
        h.setAttribute("width", HANDLE_W.toString); h.setAttribute("height", O_BASE.toString)
        h.setAttribute("rx", "2")
        g.appendChild(h)
      val fromLabel = dom.document.createElementNS(ns, "text")
      fromLabel.setAttribute("class", "tl-ov-label")
      fromLabel.setAttribute("x", (selX0 + 3).toString)
      fromLabel.setAttribute("y", (O_BASE + 11).toString)
      fromLabel.textContent = Fmt.yr(sf)
      g.appendChild(fromLabel)
      val toLabel = dom.document.createElementNS(ns, "text")
      toLabel.setAttribute("class", "tl-ov-label tl-ov-label-end")
      toLabel.setAttribute("x", (selX1 - 3).toString)
      toLabel.setAttribute("y", (O_BASE + 11).toString)
      toLabel.textContent = Fmt.yr(st)
      g.appendChild(toLabel)

  private def renderOverview(): Unit =
    renderOverviewCurve()
    renderOverviewSelection()

  // ── detail rendering ──────────────────────────────────────────────────────────────────────────

  /** Overview buckets overlapping `[from, to]`, one neighbour kept on each side so the curve runs
    * to the edges. */
  private[web] def underlayBuckets(bs: List[TimeBucket], from: Long, to: Long): List[TimeBucket] =
    val idx = bs.indices.filter(i => bs(i).end >= from && bs(i).start <= to)
    if idx.isEmpty then Nil
    else bs.slice(Math.max(0, idx.head - 1), Math.min(bs.length, idx.last + 2))

  /** The exact curve, drawn for the span its data was fetched for; `alignDetail` maps it on screen. */
  private def renderDetailCurve(): Unit =
    if detailSvg != null then
      val w = elWidth(detailSvg)
      val bs = detailBars.now()
      if lastDtBarsLen != bs.length || lastDtWidth <= 0 then
        clearLayer(dtCurveLayer)
        val (bf, bt) = dtBarsRange
        drawAreaCurve(dtCurveLayer, bs, bf, bt, w, DH.toDouble, D_BASE.toDouble, "dt-grad")
        dtDrawn = (bf, bt, w)
        lastDtWidth = w
        lastDtBarsLen = bs.length
      alignDetail(w)

  /** Keep the drawn curve under the span on screen. While it is stale (being refetched, or only
    * partly covering a widened span) the overview curve shows beneath as a low-resolution stand-in. */
  private def alignDetail(w: Double): Unit =
    val sf = spanFrom
    val st = spanTo
    val (tx, scale) = realign(dtDrawn, (sf, st, w))
    val moved = applyTransform(dtCurveLayer, tx, scale)
    if moved then dtCurveLayer.classList.add("tl-stale") else dtCurveLayer.classList.remove("tl-stale")
    val stale = moved || loading.now() || detailBars.now().isEmpty
    clearLayer(dtUnderLayer)
    if stale then
      drawAreaCurve(dtUnderLayer, underlayBuckets(overviewBars.now(), sf, st), sf, st, w,
        DH.toDouble, D_BASE.toDouble, "dtu-grad", dots = false)
    renderDetailAxis(w)

  private def renderDetailAxis(w: Double): Unit =
    val ns = svgNs
    val g = dtAxisLayer
    clearLayer(g)
    val sf = spanFrom
    val st = spanTo
    val axisLine = dom.document.createElementNS(ns, "line")
    axisLine.setAttribute("class", "tl-axis")
    axisLine.setAttribute("x1", "0"); axisLine.setAttribute("y1", (D_BASE + 0.5).toString)
    axisLine.setAttribute("x2", w.toString); axisLine.setAttribute("y2", (D_BASE + 0.5).toString)
    g.appendChild(axisLine)
    val spanYears = (st - sf).toDouble.max(1)
    val targetTicks = Math.max(3, (w / 120).toInt)
    val major = niceInterval(spanYears / targetTicks)
    val minor = major / (if major >= 10 then 5 else 1)
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
            g.appendChild(tick)
        yr += minor.toLong
    var yr = firstMajor
    while yr <= st do
      val x = xOf(yr.toDouble)
      if x >= 0 && x <= w - 4 then
        val tick = dom.document.createElementNS(ns, "line")
        tick.setAttribute("class", "tl-tick")
        tick.setAttribute("x1", x.toString); tick.setAttribute("y1", D_BASE.toString)
        tick.setAttribute("x2", x.toString); tick.setAttribute("y2", (D_BASE + 5).toString)
        g.appendChild(tick)
        val lbl = Fmt.yr(yr)
        if x + 3 > lastLabelEnd then
          val txt = dom.document.createElementNS(ns, "text")
          txt.setAttribute("class", "tl-label")
          txt.setAttribute("x", (x + 3).toString)
          txt.setAttribute("y", (DH - 2).toString)
          txt.textContent = lbl
          g.appendChild(txt)
          lastLabelEnd = x + 3 + textWidth(lbl) + 8
      yr += major.toLong

  private def renderDetailOverlay(): Unit =
    if detailSvg != null then
      val svg = detailSvg
      val w = elWidth(svg)
      svg.setAttribute("viewBox", s"0 0 ${w.toInt} $DH")
      if loading.now() then svg.classList.add("tl-loading")
      else svg.classList.remove("tl-loading")
      alignDetail(w)
      val ns = svgNs
      val g = dtOverlayLayer
      clearLayer(g)
      val sf = spanFrom
      val st = spanTo
      val spanYears = (st - sf).toDouble.max(1)
      def xOf(yr: Double): Double = (yr - sf.toDouble) / spanYears * w
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
            g.appendChild(dragRect)
      }
      TimelinePlayback.playheadNow.foreach { ph =>
        if ph >= sf && ph <= st then
          val px = xOf(ph.toDouble)
          val line = dom.document.createElementNS(ns, "line")
          line.setAttribute("class", "tl-playhead")
          line.setAttribute("x1", px.toString); line.setAttribute("y1", "0")
          line.setAttribute("x2", px.toString); line.setAttribute("y2", D_BASE.toString)
          g.appendChild(line)
          val label = dom.document.createElementNS(ns, "text")
          label.setAttribute("class", "tl-playhead-label")
          label.setAttribute("x", (px + 3).toString)
          label.setAttribute("y", "10")
          label.textContent = Fmt.yr(ph)
          g.appendChild(label)
      }

  private def renderDetail(): Unit =
    renderDetailCurve()
    renderDetailOverlay()

  // ── the preset menu ───────────────────────────────────────────────────────────────────────────

  /** One region heading, folded unless the map is looking at it; each row says the span, WHERE the
    * definition applies (PeriodO's coverage) and WHO defines it, and its title says it in full. */
  private def presetGroup(g: PresetMenu.Group): HtmlElement =
    htmlTag[dom.HTMLElement]("details")(
      cls := "tl-preset-group",
      onMountCallback(ctx => if g.inView then ctx.thisNode.ref.setAttribute("open", "")),
      htmlTag("summary")(cls := "tl-preset-region", g.region.label,
        span(cls := "tl-preset-count", s" ${g.presets.size}")),
      g.presets.map { p =>
        val years = s"${Fmt.yr(p.period.start)}–${Fmt.yr(p.period.end)}"
        val where = p.region.getOrElse("")
        val who   = PresetMenu.source(p).getOrElse("")
        button(
          typ := "button",
          cls := "tl-preset-item",
          title := List(s"${p.label}, $years", s"applies to: $where", s"defined by: $who",
            PresetMenu.periodoId(p).fold("")(id => s"PeriodO $id")).filter(_.nonEmpty).mkString("\n"),
          span(cls := "tl-preset-line", span(cls := "tl-preset-label", p.label), span(cls := "tl-preset-years", years)),
          span(cls := "tl-preset-meta", s"$where · $who"),
          onClick --> { _ =>
            setSpan(p.period.start, p.period.end)
            showPresets.set(false)
          }
        )
      }
    )

  // ── public entry point ────────────────────────────────────────────────────────────────────────

  def init(config: UiConfig): HtmlElement =
    limits = config.time
    val sf = UiState.from.now().getOrElse(config.time.defaultFrom)
    val st = UiState.to.now().getOrElse(config.time.defaultTo)
    val (cf, ct) = clamp(sf, st)
    if cf != sf || ct != st then UiState.set(UiState.from := Some(cf), UiState.to := Some(ct))()

    val presetsDropdown = div(
      cls := "tl-presets-dropdown",
      aria.label := "period presets",
      hidden <-- showPresets.signal.map(!_),
      children <-- periodPresets.signal.combineWith(presetView.signal).map { (ps, view) =>
        val groups = PresetMenu.groups(ps, view)
        if groups.isEmpty then List(div(cls := "tl-preset-loading", "no period presets available"))
        else
          div(cls := "tl-preset-note muted",
            "Each age as one authority dates it for one region (PeriodO). ",
            "The region the map shows comes first.") ::
          groups.map(presetGroup)
      },
      // the document's events for as long as the menu is on the page — bound and unbound with it
      // (they were added on mount and never removed: a remount added a second pair)
      documentEvents(_.onPointerDown) --> { e =>
        if showPresets.now() then
          val target = e.target.asInstanceOf[dom.Element]
          if target.closest(".tl-preset-wrap") == null then
            showPresets.set(false)
      },
      // an open preset list takes Escape first (the window manager's Escape runs after, on `window`)
      documentEvents(_.onKeyDown) --> { e =>
        if e.key == "Escape" && !e.defaultPrevented && showPresets.now() then
          e.preventDefault()
          showPresets.set(false)
      }
    )

    val badgeEl = htmlTag("span")(
      cls := "tl-badge",
      hidden <-- failedAt.signal.map(_.isEmpty),
      child.text <-- failedAt.signal.map(_.getOrElse(""))
    )

    div(
      cls := "tl",
      // the detail strip is folded away in the narrow layout until asked for
      cls("tl-detail-open") <-- detailOpen.signal,
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
          ovCurveLayer = newLayer(svg, "tl-ov-curve")
          ovSelLayer = newLayer(svg, "tl-ov-sel")

          renderOverview()
          overviewBars.signal.foreach(_ => { lastOvBarsLen = -1; renderOverviewCurve() })(using ctx.owner)
          renderTick.signal.foreach(_ => renderOverviewSelection())(using ctx.owner)
          UiState.from.signal.foreach(_ => renderOverviewSelection())(using ctx.owner)
          UiState.to.signal.foreach(_ => renderOverviewSelection())(using ctx.owner)
          lastOvFilterKey = UiState.toFilter().copy(period = None).toString
          loadOverview(elWidth(svg))

          svg.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
            if e.button == 0 then
              val rect = svg.getBoundingClientRect()
              val w = if rect.width > 0 then rect.width else 600.0
              val x = e.clientX - rect.left
              val fullSpan = (limits.max - limits.min).toDouble.max(1)
              val sf = spanFrom
              val st = spanTo
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
              UiState.set(UiState.from := Some(spanFrom), UiState.to := Some(spanTo))()
          }
          svg.addEventListener("pointerup", endDrag)
          svg.addEventListener("pointercancel", endDrag)

          if js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined" then
            val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(
              { (_: js.Any) =>
                // stretch now, redraw exactly once the width settles
                renderOverviewSelection()
                dom.window.clearTimeout(ovResizeRaf)
                ovResizeRaf = dom.window.setTimeout(() => {
                  if elWidth(svg) != lastOvWidth then
                    lastOvBarsLen = -1
                    renderOverview()
                }, Layout.resizeSettleMs.toDouble)
              }: js.Function1[js.Any, Unit]
            )
            obs.observe(svg)
            ovObserver = Some(obs)
        },
        onUnmountCallback { _ => ovObserver.foreach(o => { val _ = o.disconnect() }); ovObserver = None }
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
          dtUnderLayer = newLayer(svg, "tl-dt-under")
          dtCurveLayer = newLayer(svg, "tl-dt-curve")
          dtAxisLayer = newLayer(svg, "tl-dt-axis")
          dtOverlayLayer = newLayer(svg, "tl-dt-overlay")

          renderDetail()
          detailBars.signal.foreach(_ => { lastDtBarsLen = -1; renderDetailCurve() })(using ctx.owner)
          overviewBars.signal.foreach(_ => renderDetailOverlay())(using ctx.owner)
          renderTick.signal.foreach(_ => renderDetailOverlay())(using ctx.owner)

          svg.addEventListener("wheel", { (e: dom.WheelEvent) =>
            e.preventDefault()
            if e.deltaY != 0 then
              val rect = svg.getBoundingClientRect()
              val px = e.clientX - rect.left
              val w = if rect.width > 0 then rect.width else 600.0
              val spanYears = (spanTo - spanFrom).toDouble
              val anchor = spanFrom.toDouble + (px / w) * spanYears
              val factor = if e.deltaY > 0 then ZOOM else 1.0 / ZOOM
              val nf = Math.round(anchor - (anchor - spanFrom.toDouble) * factor)
              val nt = Math.round(anchor + (spanTo.toDouble - anchor) * factor)
              setSpan(nf, nt)
          }, js.Dynamic.literal(passive = false).asInstanceOf[dom.EventListenerOptions])

          svg.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
            if e.button == 0 then
              val rect = svg.getBoundingClientRect()
              val x = e.clientX - rect.left
              dragState.set(Some(DragInfo(x, x, DragKind.ZoomDetail, spanFrom, spanTo)))
              svg.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
          })

          svg.addEventListener("pointermove", { (e: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              if d.kind == DragKind.ZoomDetail then
                val rect = svg.getBoundingClientRect()
                val cur = e.clientX - rect.left
                dragState.set(Some(d.copy(curX = cur)))
                bumpRender()
                if Math.abs(cur - d.startX) >= 3 then
                  val w = elWidth(detailSvg)
                  val spanYears = (d.origTo - d.origFrom).toDouble
                  val y0 = d.origFrom + (Math.min(d.startX, cur) / w * spanYears).toLong
                  val y1 = d.origFrom + (Math.max(d.startX, cur) / w * spanYears).toLong
                  App.quickRenderIfCached(y0, y1)
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
              { (_: js.Any) =>
                renderDetailOverlay()
                dom.window.clearTimeout(dtResizeRaf)
                dtResizeRaf = dom.window.setTimeout(() => {
                  if elWidth(svg) != lastDtWidth then
                    lastDtBarsLen = -1
                    renderDetail()
                }, Layout.resizeSettleMs.toDouble)
              }: js.Function1[js.Any, Unit]
            )
            obs.observe(svg)
            dtObserver = Some(obs)

          TimelinePlayback.playheadSignal.foreach(_ => renderDetailOverlay())(using ctx.owner)

          UiState.signal.foreach { _ =>
            // the overview spans all time but follows every other filter (and a hint preview)
            val ovKey = UiState.toFilter().copy(period = None).toString
            if ovKey != lastOvFilterKey then
              lastOvFilterKey = ovKey
              loadOverview(elWidth(overviewSvg))
            // a span from the URL or back/forward may be out of bounds: clamp it in place
            val (cf2, ct2) = clamp(spanFrom, spanTo)
            if cf2 != spanFrom || ct2 != spanTo then
              UiState.from.set(Some(cf2))
              UiState.to.set(Some(ct2))
            bumpRender()
            // only when what the strip shows changed: a layer toggled, a window opened or a tab
            // switched commits UiState too, and each refetched the strip for the same answer
            if detailKey() != lastDtKey then scheduleDetail()
          }(using ctx.owner)

          loadDetail()
        },
        onUnmountCallback { _ =>
          dtObserver.foreach(o => { val _ = o.disconnect() }); dtObserver = None
          dom.window.clearTimeout(timer)
          // an answer still on its way would draw into the strip that left the page
          detailLatest.cancel()
        }
      ),
      // ── Controls: the span (presets, zoom, reset) · playback, centred · the narrow layout's extras
      div(cls := "tl-ctl",
        div(cls := "btn-group tl-ctl-start", role := "group", aria.label := "time span",
          div(cls := "tl-preset-wrap",
            button(typ := "button", cls := "btn btn--icon tl-btn-presets", title := "period presets",
              aria.label := "period presets", aria.expanded <-- showPresets.signal, "⏱",
              onClick --> { _ =>
                presetView.set(ViewportToggle.viewBox)
                loadPresets()
                showPresets.update(!_)
              }
            ),
            presetsDropdown
          ),
          button(typ := "button", cls := "btn btn--icon", title := "zoom in ×2", aria.label := "zoom in", "+",
            onClick --> { _ =>
              val sf = spanFrom.toDouble; val st = spanTo.toDouble
              val mid = (sf + st) / 2; val half = (st - sf) / 4
              setSpan(Math.round(mid - half), Math.round(mid + half))
            }),
          button(typ := "button", cls := "btn btn--icon", title := "zoom out ×2", aria.label := "zoom out", "−",
            onClick --> { _ =>
              val sf = spanFrom.toDouble; val st = spanTo.toDouble
              val mid = (sf + st) / 2; val half = (st - sf)
              setSpan(Math.round(mid - half), Math.round(mid + half))
            }),
          button(typ := "button", cls := "btn btn--icon", title := "reset to default span", aria.label := "reset", "⟲",
            onClick --> { _ => setSpan(config.time.defaultFrom, config.time.defaultTo) })
        ),
        TimelinePlayback.element(config),
        div(cls := "btn-group tl-ctl-end",
          // shown in the narrow layout only, where the detail strip starts folded
          button(cls := "btn tl-detail-btn", typ := "button",
            aria.expanded <-- detailOpen.signal,
            title <-- detailOpen.signal.map(o => if o then "hide the detail strip" else "show the detail strip"),
            child.text <-- detailOpen.signal.map(o => if o then "detail ▴" else "detail ▾"),
            onClick --> (_ => detailOpen.update(!_))),
          badgeEl)
      )
    )
