package com.histmersion
package web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, BBox, Plural, Years}
import com.histmersion.web.ui.{AggregateBy, AggregateUnit, Layout}
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.{HistoricalPeriod, Period, PresetMenu, Year}
import com.histmersion.api.instance.{TimeConfig, UiConfig}
import com.histmersion.api.library.TimeBucket
import com.histmersion.web.ui.Flash
import com.histmersion.api.personal.Bookmark
import com.histmersion.api.UiLabels
import com.histmersion.web.ui.ContextMenu
import com.histmersion.web.marks.PeriodMarks
import com.histmersion.lint.{tolerated, LintRule}

object TimelineView:
  private val OH = Layout.overviewHeight.value
  private val O_BASE = Layout.overviewBase.value
  private val DH = Layout.detailHeight.value
  private val D_BASE = Layout.detailBase.value
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val MIN_SPAN = Layout.minSpan.value
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val HANDLE_W = Layout.handleWidth.value
  private val ZOOM = 2.0

  // ── state ─────────────────────────────────────────────────────────────────────────────────────
  private var limits: TimeConfig = uninitialized

  /** The span shown IS `UiState.from/to` (no copy): a drag writes the fields live without committing, and commits once,
    * on release.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def spanFrom: Years.Astronomical = Years.Astronomical(UiState.from.now().getOrElse(limits.defaultFrom.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def spanTo: Years.Astronomical = Years.Astronomical(UiState.to.now().getOrElse(limits.defaultTo.value))

  /** A span of the timeline as the filter and the labels read it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def period(from: Years.Astronomical, to: Years.Astronomical): Period =
    Period(Year(from.value), Year(to.value))
  private val detailBars = Var(List.empty[TimeBucket])
  private val overviewBars = Var(List.empty[TimeBucket])
  private val loading = Var(false)
  private val failedAt = Var(Option.empty[String])
  private val periodPresets = Var(List.empty[HistoricalPeriod])
  private val showPresets = Var(false)

  /** What the map showed when the preset menu was opened: its region's presets come first. */
  private val presetView = Var(Option.empty[BBox])

  private val renderTick = Var(0)

  // the detail strip's request a late answer must belong to
  private val detailLatest = Latest()

  /** Whether the detail strip is unfolded in the narrow layout (a wide screen always shows it). */
  private val detailOpen = Var(false)
  // the overview's: only the newest answer is drawn
  private val overviewLatest = Latest()
  private var timer: Int = 0
  private var overviewSvg: dom.Element = uninitialized
  private var detailSvg: dom.Element = uninitialized

  // Persistent layers, bottom to top; each render replaces only its own layer's children.
  private var ovCurveLayer: dom.Element = null
  private var ovSelLayer: dom.Element = null
  private var dtUnderLayer: dom.Element = null
  private var dtCurveLayer: dom.Element = null
  private var dtAxisLayer: dom.Element = null
  private var dtOverlayLayer: dom.Element = null
  // the hover markers, above everything of their strip
  private var ovHoverLayer: dom.Element = null
  private var dtHoverLayer: dom.Element = null
  // the points each curve was drawn through (the hover reads them)
  private var ovPts: Vector[TimelineCurve.Pt] = Vector.empty
  private var dtPts: Vector[TimelineCurve.Pt] = Vector.empty
  private var lastOvWidth: Double = 0.0
  private var lastDtWidth: Double = 0.0
  private var lastOvHeight: Double = 0.0
  private var lastDtHeight: Double = 0.0
  private var lastOvBarsLen: Int = -1
  private var lastDtBarsLen: Int = -1
  private var ovResizeRaf: Int = 0
  // `var`s: the strips' size observers, disconnected when their strip leaves the page
  private var ovObserver: Option[js.Dynamic] = None
  private var dtObserver: Option[js.Dynamic] = None
  private var dtResizeRaf: Int = 0

  /** The span the current `detailBars` were fetched for — not the span on screen. */
  private var dtBarsRange: (Years.Astronomical, Years.Astronomical) = (Years.Astronomical(0L), Years.Astronomical(0L))
  private var lastOvFilterKey: String = ""

  /** What the detail curve layer was drawn for: (from, to, width). */
  private var dtDrawn: (Years.Astronomical, Years.Astronomical, Double) =
    (Years.Astronomical(0L), Years.Astronomical(0L), 0.0)

  private enum DragKind derives CanEqual:
    case LeftHandle, RightHandle, PanRange, ZoomDetail

  private case class DragInfo(startX: Double, curX: Double, kind: DragKind, origFrom: Long, origTo: Long)

  private val dragState = Var(Option.empty[DragInfo])

  def currentSpan: (Years.Astronomical, Years.Astronomical) = (spanFrom, spanTo)

  private val foldedKey = "histmersion.timelineFolded"

  /** Whether the timeline is folded to its one line of controls (kept by this browser). */
  val folded: Var[Boolean] = Var(try Option(dom.window.localStorage.getItem(foldedKey)).contains("1")
  catch case _: Exception => false)

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def setFolded(on: Boolean): Unit =
    folded.set(on)
    try dom.window.localStorage.setItem(foldedKey, if on then "1" else "0")
    catch case _: Exception => ()

  // ── span algebra ──────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def clamp(a0: Years.Astronomical, b0: Years.Astronomical): (Years.Astronomical, Years.Astronomical) =
    var a = Math.round(Math.min(a0.value.toDouble, b0.value.toDouble))
    var b = Math.round(Math.max(a0.value.toDouble, b0.value.toDouble))
    if b - a < MIN_SPAN then
      val mid = (a.toDouble + b.toDouble) / 2
      a = Math.round(mid - MIN_SPAN.toDouble / 2)
      b = a + MIN_SPAN
    val width = Math.min(b - a, limits.max.value - limits.min.value)
    if a < limits.min.value then { a = limits.min.value; b = a + width }
    if b > limits.max.value then { b = limits.max.value; a = b - width }
    (Years.Astronomical(a), Years.Astronomical(Math.max(a + MIN_SPAN, b)))

  /** Locked, the timeline keeps its span: no drag, wheel, click or menu of its own changes it. */
  val locked: Var[Boolean] = Var(false)

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def setLocked(on: Boolean): Unit =
    locked.set(on)
    Flash.say(if on then Flash.Text("The timeline is locked: its years stay until you unlock it.")
    else Flash.Text("The timeline moves again."))

  /** The person's own periods ([[marks.PeriodMarks]]) and the naming of a new one, in the presets menu. */
  private val namingPeriod = Var(Option.empty[Bookmark.Label])

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def setSpan(from: Years.Astronomical, to: Years.Astronomical, push: Router.Push = Router.Push(true)): Unit =
    val (nf, nt) = clamp(from, to)
    if locked.now() then bumpRender()
    else if nf == spanFrom && nt == spanTo then ()
    else
      UiState.from.set(Some(nf.value))
      UiState.to.set(Some(nt.value))
      bumpRender()
      scheduleDetail()
      App.quickRenderIfCached(nf, nt)
      if push.value then UiState.set(UiState.from := Some(nf.value), UiState.to := Some(nt.value))()

  private def bumpRender(): Unit = renderTick.update(_ + 1)

  // ── data loading ──────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def scheduleDetail(): Unit =
    dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => loadDetail(), UiState.reloadDelay(UiState.ReloadDelayNormalMs(150)).value)

  /** What the detail strip was last asked for: the filter over the span on screen. */
  private var lastDtKey: String = ""
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  private def detailKey(): String = UiState.toFilter().copy(period = Some(period(spanFrom, spanTo))).toString

  private[web] val MAX_BUCKETS = Layout.maxBuckets

  /** A bucket every [[TimelineCurve.PxPerBucket]] px (one a pixel drew a staircase of 1-px risers), and never more than
    * `Layout.maxBuckets`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def bucketSizeFor(spanYears: Years.Shift, pixelWidth: Double): PlaybackBuckets.BucketYears =
    val byPixels =
      Math.ceil(spanYears.value.toDouble / (Math.max(pixelWidth, 100) / TimelineCurve.PxPerBucket.value)).toLong
    val byLimit = Math.ceil(spanYears.value.toDouble / Layout.maxBuckets.value).toLong
    PlaybackBuckets.BucketYears(Math.max(1L, Math.max(byPixels, byLimit)))

  private def loadOverview(pixelWidth: Double): Unit = overview(pixelWidth)

  /** The overview follows every filter change — and every hovered suggestion — with ONE request in flight and the
    * newest change next (it fired one per change, undebounced: sweeping the pointer down a suggestion list sent one per
    * row). Only the newest answer is drawn.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val overview = LatestOnly[Double] { (pixelWidth, done) =>
    val ticket = overviewLatest.start()
    val f = UiState.toFilter().copy(period = Some(Period(limits.min, limits.max)))
    val span = Years.Shift(limits.max.value - limits.min.value)
    val sz = bucketSizeFor(span, pixelWidth)
    ApiClient
      .aggregate(
        Api.AggregateQuery(
          Some(Api.AggregateQuery.By(AggregateBy.Time.param.value)),
          AggregateUnit.Sources.param.map(u => Api.AggregateQuery.Unit(u.value)),
          f,
          size = Some(Api.AggregateQuery.Size(sz.value))
        )
      )
      .onComplete { result =>
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
  }

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def loadDetail(): Unit =
    // a load now answers whatever was scheduled: the strip's mount scheduled one and loaded at once,
    // asking twice for the same strip on every page load
    dom.window.clearTimeout(timer)
    loading.set(true)
    bumpRender()
    val sf = spanFrom
    val st = spanTo
    val f = UiState.toFilter().copy(period = Some(period(sf, st)))
    lastDtKey = f.toString
    val pw = if detailSvg != null then elWidth(detailSvg) else 600.0
    val sz = bucketSizeFor(Years.Shift(st.value - sf.value), pw)
    val q = Api.AggregateQuery(
      Some(Api.AggregateQuery.By(AggregateBy.Time.param.value)),
      AggregateUnit.Sources.param.map(u => Api.AggregateQuery.Unit(u.value)),
      f,
      size = Some(Api.AggregateQuery.Size(sz.value))
    )
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
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  private def loadPresets(): Unit =
    if periodPresets.now().isEmpty then
      ApiClient.periods().foreach(ps => periodPresets.set(ps.filter(_.preset.isDefined)))

  // ── drawing helpers ───────────────────────────────────────────────────────────────────────────

  /** The element's drawn width, fractions of a pixel included (the drawing is made at exactly this size: a whole-pixel
    * `clientWidth` stretched it by the fraction).
    */
  private def elWidth(el: dom.Element): Double =
    if el == null then 600.0
    else
      val r = el.getBoundingClientRect()
      if r.width > 0 then r.width
      else
        val w = el.asInstanceOf[dom.SVGElement].clientWidth.toDouble
        if w > 0 then w else 600.0

  private def niceInterval(raw: Double): Double =
    val candidates =
      List(1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 250.0, 500.0, 1000.0, 2000.0, 2500.0, 5000.0, 10000.0)
    candidates.find(_ >= raw).getOrElse(Math.pow(10, Math.ceil(Math.log10(raw))))

  /** The element's drawn height (its stylesheet height: fixed on a wide screen, `clamp()` on a phone). */
  private def elHeight(el: dom.Element, fallback: Double): Double =
    if el == null then fallback
    else
      val r = el.getBoundingClientRect()
      if r.height > 0 then r.height
      else
        val h = el.asInstanceOf[dom.SVGElement].clientHeight.toDouble
        if h > 0 then h else fallback

  /** The area and line of the counts `bs` over `[from, to]`, `w` px wide, standing on `baseY`: a smooth monotone curve
    * ([[TimelineCurve]]), no mark per bucket (the hover marker says one bucket's count). Answers the points drawn, for
    * the hover.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def drawAreaCurve(
      svg: dom.Element,
      bs: List[TimeBucket],
      from: Years.Astronomical,
      to: Years.Astronomical,
      w: Double,
      baseY: Double,
      gradId: String
  ): Vector[TimelineCurve.Pt] =
    val ns = "http://www.w3.org/2000/svg"
    val pts = TimelineCurve.points(
      bs,
      from,
      to,
      TimelineCurve.ElementSize(w),
      TimelineCurve.ScreenPixels(baseY),
      top = TimelineCurve.ScreenPixels(4)
    )
    if pts.nonEmpty then
      val defs = dom.document.createElementNS(ns, "defs")
      val grad = dom.document.createElementNS(ns, "linearGradient")
      grad.setAttribute("id", gradId)
      grad.setAttribute("x1", "0%"); grad.setAttribute("x2", "100%")
      grad.setAttribute("y1", "0%"); grad.setAttribute("y2", "0%")
      for i <- 0 until 10 do
        val stop = dom.document.createElementNS(ns, "stop")
        stop.setAttribute("offset", s"${(i.toDouble / 9 * 100).round}%")
        stop.setAttribute("stop-color", Fmt.color(Fmt.ColorT(i.toDouble / 9)).value)
        grad.appendChild(stop)
      defs.appendChild(grad)
      svg.appendChild(defs)

      val path = dom.document.createElementNS(ns, "path")
      path.setAttribute("class", "tl-area")
      path.setAttribute("d", TimelineCurve.areaPath(pts, TimelineCurve.ScreenPixels(baseY)))
      path.setAttribute("fill", s"url(#$gradId)")
      path.setAttribute("opacity", "0.85")
      svg.appendChild(path)

      val line = dom.document.createElementNS(ns, "path")
      line.setAttribute("class", "tl-area-line")
      line.setAttribute("d", TimelineCurve.linePath(pts))
      line.setAttribute("fill", "none")
      line.setAttribute("stroke", s"url(#$gradId)")
      line.setAttribute("stroke-width", "1.5")
      line.setAttribute("vector-effect", "non-scaling-stroke")
      svg.appendChild(line)
    pts

  // ── the hover marker: ONE per strip, the bucket under the pointer and its exact count ─────────

  /** The bucket nearest screen x `px` of a curve drawn as `pts` and moved on screen by (`tx`, `scale`), and where it
    * stands on screen. Pure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def hoverAt(
      pts: IndexedSeq[TimelineCurve.Pt],
      px: Double,
      tx: Double,
      scale: Double
  ): Option[(Double, TimelineCurve.Pt)] =
    if scale <= 0 then None
    else TimelineCurve.nearest(pts, TimelineCurve.ScreenPixels((px - tx) / scale)).map(p => (tx + p.x.value * scale, p))

  /** Pure: what the hover marker says of a bucket. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def hoverText(b: TimeBucket): UiLabels.Text =
    val years = if b.start.value == b.end.value then Fmt.yr(Year(b.start.value)).value
    else shown"${Fmt.yr(Year(b.start.value))}–${Fmt.yr(Year(b.end.value))}"
    UiLabels.Text(shown"$years: ${Plural.count(Plural.CountN(b.count.value.toLong), Plural.CountSingular("source"))}")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def showHover(layer: dom.Element, at: Option[(Double, TimelineCurve.Pt)], w: Double): Unit =
    if layer != null then
      clearLayer(layer)
      at.filter((_, p) => p.bucket.count.value > 0).foreach { (x, p) =>
        val dot = dom.document.createElementNS(svgNs, "circle")
        dot.setAttribute("class", "tl-hover-dot")
        dot.setAttribute("cx", x.toString); dot.setAttribute("cy", p.y.value.toString); dot.setAttribute("r", "3")
        val span = (spanTo.value - spanFrom.value).toDouble.max(1)
        dot.setAttribute(
          "fill",
          Fmt
            .color(
              Fmt.ColorT(
                (((p.bucket.start.value + p.bucket.end.value).toDouble / 2 - spanFrom.value) / span).max(0).min(1)
              )
            )
            .value
        )
        layer.appendChild(dot)
        val label = dom.document.createElementNS(svgNs, "text")
        label.setAttribute("class", "tl-hover-label")
        val text = hoverText(p.bucket)
        // beside the marker, on the side with room for it
        val right = x + 6 + text.value.length * 6.2 <= w
        label.setAttribute("x", (if right then x + 6 else x - 6).toString)
        label.setAttribute("y", "10")
        label.setAttribute("text-anchor", if right then "start" else "end")
        label.textContent = text.value
        layer.appendChild(label)
      }

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def realign(
      drawn: (Years.Astronomical, Years.Astronomical, Double),
      now: (Years.Astronomical, Years.Astronomical, Double)
  ): (Double, Double) =
    val (df, dt, dw) = drawn
    val (nf, nt, nw) = now
    val dSpan = (dt.value - df.value).toDouble.max(1)
    val nSpan = (nt.value - nf.value).toDouble.max(1)
    if dw <= 0 then (0.0, 1.0)
    else
      val scale = (dSpan / nSpan) * (nw / dw)
      val tx = (df.value - nf.value).toDouble / nSpan * nw
      (tx, scale)

  private def applyTransform(g: dom.Element, tx: Double, scale: Double): Boolean =
    val identity = Math.abs(tx) < 0.5 && Math.abs(scale - 1) < 0.002
    if identity then g.removeAttribute("transform")
    else g.setAttribute("transform", s"translate($tx 0) scale($scale 1)")
    !identity

  /** The strips' heights as drawn (the stylesheet's, a `clamp()` on a phone) and where the curve stands in them: the
    * label band under it keeps its height, the curve takes the rest — drawn at the size it is shown, so nothing is
    * stretched.
    */
  private def ovHeight: Double = elHeight(overviewSvg, OH.toDouble)
  private def ovBase(h: Double): Double = h - (OH - O_BASE)
  private def dtHeight: Double = elHeight(detailSvg, DH.toDouble)
  private def dtBase(h: Double): Double = h - (DH - D_BASE)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def renderOverviewCurve(): Unit =
    if overviewSvg != null then
      val w = elWidth(overviewSvg)
      val h = ovHeight
      val bs = overviewBars.now()
      if lastOvBarsLen != bs.length || lastOvWidth <= 0 || h != lastOvHeight then
        clearLayer(ovCurveLayer)
        showHover(ovHoverLayer, None, w)
        ovPts = drawAreaCurve(
          ovCurveLayer,
          bs,
          Years.Astronomical(limits.min.value),
          Years.Astronomical(limits.max.value),
          w,
          ovBase(h) - 2,
          "ov-grad"
        )
        lastOvWidth = w
        lastOvHeight = h
        lastOvBarsLen = bs.length
      alignOverviewCurve(w)

  /** Stretch the drawn overview curve to the current width at once; the exact redraw follows. */
  private def alignOverviewCurve(w: Double): Unit =
    val stale = applyTransform(ovCurveLayer, 0.0, if lastOvWidth > 0 then w / lastOvWidth else 1)
    if stale then ovCurveLayer.classList.add("tl-stale") else ovCurveLayer.classList.remove("tl-stale")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def renderOverviewSelection(): Unit =
    if overviewSvg != null then
      val svg = overviewSvg
      val w = elWidth(svg)
      val oh = ovHeight
      val ovB = ovBase(oh)
      // the drawing's own size: one unit a pixel, both ways
      svg.setAttribute("viewBox", s"0 0 $w $oh")
      alignOverviewCurve(w)
      val ns = svgNs
      clearLayer(ovSelLayer)
      val g = ovSelLayer
      val sf = spanFrom
      val st = spanTo
      val fullSpan = (limits.max.value - limits.min.value).toDouble.max(1)
      val selX0 = Math.max(0.0, (sf.value - limits.min.value).toDouble / fullSpan * w)
      val selX1 = Math.min(w, (st.value - limits.min.value).toDouble / fullSpan * w)
      val dimLeft = dom.document.createElementNS(ns, "rect")
      dimLeft.setAttribute("class", "tl-ov-dim")
      dimLeft.setAttribute("x", "0"); dimLeft.setAttribute("y", "0")
      dimLeft.setAttribute("width", selX0.toString); dimLeft.setAttribute("height", ovB.toString)
      g.appendChild(dimLeft)
      val dimRight = dom.document.createElementNS(ns, "rect")
      dimRight.setAttribute("class", "tl-ov-dim")
      dimRight.setAttribute("x", selX1.toString); dimRight.setAttribute("y", "0")
      dimRight.setAttribute("width", (w - selX1).toString); dimRight.setAttribute("height", ovB.toString)
      g.appendChild(dimRight)
      for ex <- List(selX0, selX1) do
        val edge = dom.document.createElementNS(ns, "line")
        edge.setAttribute("class", "tl-ov-edge")
        edge.setAttribute("x1", ex.toString); edge.setAttribute("y1", "0")
        edge.setAttribute("x2", ex.toString); edge.setAttribute("y2", ovB.toString)
        g.appendChild(edge)
      for hx <- List(selX0, selX1) do
        val h = dom.document.createElementNS(ns, "rect")
        h.setAttribute("class", "tl-ov-handle")
        h.setAttribute("x", (hx - HANDLE_W / 2).toString); h.setAttribute("y", "0")
        h.setAttribute("width", HANDLE_W.toString); h.setAttribute("height", ovB.toString)
        h.setAttribute("rx", "2")
        g.appendChild(h)
      // the two years: inside the selection, outside its handles when they are close, else one label
      val labels = RangeLabels.place(
        UiLabels.Text(Fmt.yr(Year(sf.value)).value),
        UiLabels.Text(Fmt.yr(Year(st.value)).value),
        RangeLabels.ScreenPixels(selX0),
        RangeLabels.ScreenPixels(selX1),
        RangeLabels.ElementSize(w)
      ) match
        case RangeLabels.Placement.Inside(f, t)  => List(f, t)
        case RangeLabels.Placement.Outside(f, t) => List(f, t)
        case RangeLabels.Placement.Joined(l)     => List(l)
      for l <- labels do
        val label = dom.document.createElementNS(ns, "text")
        label.setAttribute("class", "tl-ov-label")
        label.setAttribute("x", l.x.value.toString)
        label.setAttribute("y", (ovB + 11).toString)
        label.textContent = l.text.value
        g.appendChild(label)

  private def renderOverview(): Unit =
    renderOverviewCurve()
    renderOverviewSelection()

  // ── detail rendering ──────────────────────────────────────────────────────────────────────────

  /** Overview buckets overlapping `[from, to]`, one neighbour kept on each side so the curve runs to the edges.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def underlayBuckets(
      bs: List[TimeBucket],
      from: Years.Astronomical,
      to: Years.Astronomical
  ): List[TimeBucket] =
    val idx = bs.indices.filter(i => bs(i).end.value >= from.value && bs(i).start.value <= to.value)
    if idx.isEmpty then Nil
    else bs.slice(Math.max(0, idx.head - 1), Math.min(bs.length, idx.last + 2))

  /** The exact curve, drawn for the span its data was fetched for; `alignDetail` maps it on screen. */
  private def renderDetailCurve(): Unit =
    if detailSvg != null then
      val w = elWidth(detailSvg)
      val h = dtHeight
      val bs = detailBars.now()
      if lastDtBarsLen != bs.length || lastDtWidth <= 0 || h != lastDtHeight then
        clearLayer(dtCurveLayer)
        showHover(dtHoverLayer, None, w)
        val (bf, bt) = dtBarsRange
        dtPts = drawAreaCurve(dtCurveLayer, bs, bf, bt, w, dtBase(h), "dt-grad")
        dtDrawn = (bf, bt, w)
        lastDtWidth = w
        lastDtHeight = h
        lastDtBarsLen = bs.length
      alignDetail(w)

  /** Keep the drawn curve under the span on screen. While it is stale (being refetched, or only partly covering a
    * widened span) the overview curve shows beneath as a low-resolution stand-in.
    */
  private def alignDetail(w: Double): Unit =
    val sf = spanFrom
    val st = spanTo
    val (tx, scale) = realign(dtDrawn, (sf, st, w))
    val moved = applyTransform(dtCurveLayer, tx, scale)
    if moved then dtCurveLayer.classList.add("tl-stale") else dtCurveLayer.classList.remove("tl-stale")
    val stale = moved || loading.now() || detailBars.now().isEmpty
    clearLayer(dtUnderLayer)
    if stale then
      val _ = drawAreaCurve(
        dtUnderLayer,
        underlayBuckets(overviewBars.now(), sf, st),
        sf,
        st,
        w,
        dtBase(dtHeight),
        "dtu-grad"
      )
    renderDetailAxis(w)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def renderDetailAxis(w: Double): Unit =
    val ns = svgNs
    val g = dtAxisLayer
    clearLayer(g)
    val dtH = dtHeight
    val dtB = dtBase(dtH)
    val sf = spanFrom
    val st = spanTo
    val axisLine = dom.document.createElementNS(ns, "line")
    axisLine.setAttribute("class", "tl-axis")
    axisLine.setAttribute("x1", "0"); axisLine.setAttribute("y1", (dtB + 0.5).toString)
    axisLine.setAttribute("x2", w.toString); axisLine.setAttribute("y2", (dtB + 0.5).toString)
    g.appendChild(axisLine)
    val spanYears = (st.value - sf.value).toDouble.max(1)
    val targetTicks = Math.max(3, (w / 120).toInt)
    val major = niceInterval(spanYears / targetTicks)
    val minor = major / (if major >= 10 then 5 else 1)
    val textWidth = (label: String) => label.length * 6.2
    def xOf(yr: Double): Double = (yr - sf.value.toDouble) / spanYears * w
    val firstMajor = Math.ceil(sf.value.toDouble / major).toLong * major.toLong
    val firstMinor = Math.ceil(sf.value.toDouble / minor).toLong * minor.toLong
    var lastLabelEnd = 0.0
    if minor < major then
      var yr = firstMinor
      while yr <= st.value do
        if yr % major.toLong != 0 then
          val x = xOf(yr.toDouble)
          if x >= 0 && x <= w then
            val tick = dom.document.createElementNS(ns, "line")
            tick.setAttribute("class", "tl-tick tl-tick-minor")
            tick.setAttribute("x1", x.toString); tick.setAttribute("y1", (dtB + 1).toString)
            tick.setAttribute("x2", x.toString); tick.setAttribute("y2", (dtB + 3).toString)
            g.appendChild(tick)
        yr += minor.toLong
    var yr = firstMajor
    while yr <= st.value do
      val x = xOf(yr.toDouble)
      if x >= 0 && x <= w - 4 then
        val tick = dom.document.createElementNS(ns, "line")
        tick.setAttribute("class", "tl-tick")
        tick.setAttribute("x1", x.toString); tick.setAttribute("y1", dtB.toString)
        tick.setAttribute("x2", x.toString); tick.setAttribute("y2", (dtB + 5).toString)
        g.appendChild(tick)
        val lbl = Fmt.yr(Year(yr)).value
        if x + 3 > lastLabelEnd then
          val txt = dom.document.createElementNS(ns, "text")
          txt.setAttribute("class", "tl-label")
          txt.setAttribute("x", (x + 3).toString)
          txt.setAttribute("y", (dtH - 2).toString)
          txt.textContent = lbl
          g.appendChild(txt)
          lastLabelEnd = x + 3 + textWidth(lbl) + 8
      yr += major.toLong

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def renderDetailOverlay(): Unit =
    if detailSvg != null then
      val svg = detailSvg
      val w = elWidth(svg)
      val dh = dtHeight
      val dtB = dtBase(dh)
      svg.setAttribute("viewBox", s"0 0 $w $dh")
      if loading.now() then svg.classList.add("tl-loading")
      else svg.classList.remove("tl-loading")
      alignDetail(w)
      val ns = svgNs
      val g = dtOverlayLayer
      clearLayer(g)
      val sf = spanFrom
      val st = spanTo
      val spanYears = (st.value - sf.value).toDouble.max(1)
      def xOf(yr: Double): Double = (yr - sf.value.toDouble) / spanYears * w
      dragState.now().foreach { d =>
        if d.kind == DragKind.ZoomDetail then
          val dx0 = Math.min(d.startX, d.curX)
          val dx1 = Math.max(d.startX, d.curX)
          if Math.abs(d.curX - d.startX) >= 3 then
            val dragRect = dom.document.createElementNS(ns, "rect")
            dragRect.setAttribute("class", "tl-drag")
            dragRect.setAttribute("x", dx0.toString); dragRect.setAttribute("y", "0")
            dragRect.setAttribute("width", Math.max(1.0, dx1 - dx0).toString)
            dragRect.setAttribute("height", dtB.toString)
            g.appendChild(dragRect)
      }
      TimelinePlayback.playheadNow.foreach { ph =>
        if ph >= sf && ph <= st then
          val px = xOf(ph.value.toDouble)
          val line = dom.document.createElementNS(ns, "line")
          line.setAttribute("class", "tl-playhead")
          line.setAttribute("x1", px.toString); line.setAttribute("y1", "0")
          line.setAttribute("x2", px.toString); line.setAttribute("y2", dtB.toString)
          g.appendChild(line)
          val label = dom.document.createElementNS(ns, "text")
          label.setAttribute("class", "tl-playhead-label")
          label.setAttribute("x", (px + 3).toString)
          label.setAttribute("y", "10")
          label.textContent = Fmt.yr(Year(ph.value)).value
          g.appendChild(label)
      }

  private def renderDetail(): Unit =
    renderDetailCurve()
    renderDetailOverlay()

  // ── the preset menu ───────────────────────────────────────────────────────────────────────────

  /** One region heading, folded unless the map is looking at it; each row says the span, WHERE the definition applies
    * (PeriodO's coverage) and WHO defines it, and its title says it in full.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def presetGroup(g: PresetMenu.Group): HtmlElement =
    htmlTag[dom.HTMLElement]("details")(
      cls := "tl-preset-group",
      onMountCallback(ctx => if g.inView then ctx.thisNode.ref.setAttribute("open", "")),
      htmlTag("summary")(
        cls := "tl-preset-region",
        g.region.label.value,
        span(cls := "tl-preset-count", s" ${g.presets.size}")
      ),
      g.presets.map { p =>
        val years = shown"${Fmt.yr(p.period.start)}–${Fmt.yr(p.period.end)}"
        val where = p.region.getOrElse("")
        val who = PresetMenu.source(p).getOrElse("")
        button(
          typ := "button",
          cls := "tl-preset-item",
          title := List(
            shown"${p.label}, $years",
            s"applies to: $where",
            s"defined by: $who",
            PresetMenu.periodoId(p).fold("")(id => s"PeriodO $id")
          ).filter(_.nonEmpty).mkString("\n"),
          span(
            cls := "tl-preset-line",
            span(cls := "tl-preset-label", p.label),
            span(cls := "tl-preset-years", years)
          ),
          span(cls := "tl-preset-meta", s"$where · $who"),
          onClick --> { _ =>
            setSpan(Years.Astronomical(p.period.start.value), Years.Astronomical(p.period.end.value))
            showPresets.set(false)
          }
        )
      }
    )

  /** The timeline's own actions: lock it, start it at the years shown, keep the years as one of my periods. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def menuEntries(config: UiConfig): List[ContextMenu.Entry] =
    val (a, b) = (Year(spanFrom.value), Year(spanTo.value))
    List(
      ContextMenu.Entry(
        if locked.now() then ContextMenu.Entry.Label("Unlock the timeline")
        else ContextMenu.Entry.Label("Lock the timeline"),
        () => setLocked(!locked.now()),
        pressed = Some(ContextMenu.Entry.Pressed(locked.now()))
      ),
      ContextMenu.Entry(
        ContextMenu.Entry.Label(shown"Start the timeline at ${Fmt.periodStr(a, b)}"),
        () => PeriodMarks.startAt(a, b),
        hint = Some(
          ContextMenu.Entry.Hint("The years the timeline opens on, for everyone who opens Histmersion from this server")
        )
      ),
      ContextMenu.Entry(
        ContextMenu.Entry.Label("Add these years to my periods…"),
        () => {
          presetView.set(ViewportToggle.viewBox)
          loadPresets()
          namingPeriod.set(Some(Bookmark.Label(Fmt.periodStr(a, b).value)))
          showPresets.set(true)
        }
      )
    )

  // ── public entry point ────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def init(config: UiConfig): HtmlElement =
    limits = config.time
    val sf = Years.Astronomical(UiState.from.now().getOrElse(config.time.defaultFrom.value))
    val st = Years.Astronomical(UiState.to.now().getOrElse(config.time.defaultTo.value))
    val (cf, ct) = clamp(sf, st)
    if cf != sf || ct != st then UiState.set(UiState.from := Some(cf.value), UiState.to := Some(ct.value))()

    val presetsDropdown = div(
      cls := "tl-presets-dropdown",
      aria.label := "period presets",
      hidden <-- showPresets.signal.map(!_),
      PeriodMarks
        .section(
          namingPeriod,
          () => (Year(spanFrom.value), Year(spanTo.value)),
          (a, b) => { setSpan(Years.Astronomical(a.value), Years.Astronomical(b.value)); showPresets.set(false) }
        ),
      children <-- periodPresets.signal.combineWith(presetView.signal).map { (ps, view) =>
        val groups = PresetMenu.groups(ps, view)
        if groups.isEmpty then List(div(cls := "tl-preset-loading", "no period presets available"))
        else
          div(
            cls := "tl-preset-note muted",
            "Each age as one authority dates it for one region (PeriodO). ",
            "The region the map shows comes first."
          ) ::
            groups.map(presetGroup)
      },
      // the document's events for as long as the menu is on the page — bound and unbound with it
      // (they were added on mount and never removed: a remount added a second pair)
      documentEvents(_.onPointerDown) --> { e =>
        if showPresets.now() then
          val target = e.target.asInstanceOf[dom.Element]
          if target.closest(".tl-preset-wrap") == null then showPresets.set(false)
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
      // folded to its line of controls (a wide screen), as this browser left it
      cls("is-folded") <-- folded.signal,
      cls("is-locked") <-- locked.signal,
      ContextMenu.on(ContextMenu.OnTitle("Timeline actions"), () => menuEntries(config)),
      // ── Overview bar (full range, draggable handles) ──────────────────────────────────────
      div(
        cls := "tl-overview-wrap",
        onMountCallback { ctx =>
          val ns = "http://www.w3.org/2000/svg"
          val svg = dom.document.createElementNS(ns, "svg").asInstanceOf[dom.SVGElement]
          svg.setAttribute("class", "tl-overview-svg")
          svg.setAttribute("width", "100%")
          svg.setAttribute("height", OH.toString)
          overviewSvg = svg
          ctx.thisNode.ref.appendChild(svg)
          ovCurveLayer = newLayer(svg, "tl-ov-curve")
          ovSelLayer = newLayer(svg, "tl-ov-sel")
          ovHoverLayer = newLayer(svg, "tl-hover")

          renderOverview()
          overviewBars.signal.foreach { _ => lastOvBarsLen = -1; renderOverviewCurve() }(using ctx.owner)
          renderTick.signal.foreach(_ => renderOverviewSelection())(using ctx.owner)
          UiState.from.signal.foreach(_ => renderOverviewSelection())(using ctx.owner)
          UiState.to.signal.foreach(_ => renderOverviewSelection())(using ctx.owner)
          lastOvFilterKey = UiState.toFilter().copy(period = None).toString
          loadOverview(elWidth(svg))

          svg.addEventListener(
            "pointerdown",
            (e: dom.PointerEvent) =>
              if e.button == 0 && !locked.now() then
                val rect = svg.getBoundingClientRect()
                val w = if rect.width > 0 then rect.width else 600.0
                val x = e.clientX - rect.left
                val fullSpan = (limits.max.value - limits.min.value).toDouble.max(1)
                val sf = spanFrom
                val st = spanTo
                val selX0 = (sf.value - limits.min.value).toDouble / fullSpan * w
                val selX1 = (st.value - limits.min.value).toDouble / fullSpan * w

                val kind =
                  if Math.abs(x - selX0) <= HANDLE_W then DragKind.LeftHandle
                  else if Math.abs(x - selX1) <= HANDLE_W then DragKind.RightHandle
                  else if x > selX0 && x < selX1 then DragKind.PanRange
                  else
                    val clickYear = limits.min.value.toDouble + (x / w) * fullSpan
                    val half = (st.value - sf.value).toDouble / 2
                    setSpan(
                      Years.Astronomical(Math.round(clickYear - half)),
                      Years.Astronomical(Math.round(clickYear + half))
                    )
                    DragKind.PanRange

                dragState.set(Some(DragInfo(x, x, kind, sf.value, st.value)))
                svg.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
          )

          svg.addEventListener(
            "pointermove",
            (e: dom.PointerEvent) =>
              if dragState.now().isEmpty then
                val rect = svg.getBoundingClientRect()
                val w = if rect.width > 0 then rect.width else 600.0
                showHover(
                  ovHoverLayer,
                  hoverAt(ovPts, e.clientX - rect.left, 0.0, if lastOvWidth > 0 then w / lastOvWidth else 1),
                  w
                )
          )
          svg.addEventListener("pointerleave", (_: dom.PointerEvent) => showHover(ovHoverLayer, None, 0.0))

          svg.addEventListener(
            "pointermove",
            (e: dom.PointerEvent) =>
              dragState.now().foreach { d =>
                showHover(ovHoverLayer, None, 0.0)
                val rect = svg.getBoundingClientRect()
                val w = if rect.width > 0 then rect.width else 600.0
                val cur = e.clientX - rect.left
                val fullSpan = (limits.max.value - limits.min.value).toDouble.max(1)
                val pxToYear = fullSpan / w

                d.kind match
                  case DragKind.LeftHandle =>
                    val yearDelta = ((cur - d.startX) * pxToYear).toLong
                    val nf = Math.max(limits.min.value, Math.min(d.origTo - MIN_SPAN, d.origFrom + yearDelta))
                    setSpan(Years.Astronomical(nf), Years.Astronomical(d.origTo), push = Router.Push(false))
                  case DragKind.RightHandle =>
                    val yearDelta = ((cur - d.startX) * pxToYear).toLong
                    val nt = Math.min(limits.max.value, Math.max(d.origFrom + MIN_SPAN, d.origTo + yearDelta))
                    setSpan(Years.Astronomical(d.origFrom), Years.Astronomical(nt), push = Router.Push(false))
                  case DragKind.PanRange =>
                    val yearDelta = ((cur - d.startX) * pxToYear).toLong
                    val rangeWidth = d.origTo - d.origFrom
                    var nf = d.origFrom + yearDelta
                    var nt = d.origTo + yearDelta
                    if nf < limits.min.value then { nf = limits.min.value; nt = nf + rangeWidth }
                    if nt > limits.max.value then { nt = limits.max.value; nf = nt - rangeWidth }
                    setSpan(Years.Astronomical(nf), Years.Astronomical(nt), push = Router.Push(false))
                  case _ => ()
              }
          )

          val endDrag: js.Function1[dom.PointerEvent, Unit] = { (_: dom.PointerEvent) =>
            if dragState.now().isDefined then
              dragState.set(None)
              UiState.set(UiState.from := Some(spanFrom.value), UiState.to := Some(spanTo.value))()
          }
          svg.addEventListener("pointerup", endDrag)
          svg.addEventListener("pointercancel", endDrag)

          if js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined" then
            val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(
              { (_: js.Any) =>
                // stretch now, redraw exactly once the width settles
                renderOverviewSelection()
                dom.window.clearTimeout(ovResizeRaf)
                ovResizeRaf = dom.window.setTimeout(
                  () => {
                    if elWidth(svg) != lastOvWidth || ovHeight != lastOvHeight then
                      lastOvBarsLen = -1
                      renderOverview()
                  },
                  Layout.resizeSettleMs.value.toDouble
                )
              }: js.Function1[js.Any, Unit]
            )
            obs.observe(svg)
            ovObserver = Some(obs)
        },
        onUnmountCallback { _ =>
          ovObserver.foreach { o =>
            val _ = o.disconnect()
          };
          ovObserver = None
        }
      ),
      // ── Detail bar (zoomed into selected range) ───────────────────────────────────────────
      div(
        cls := "tl-detail-wrap",
        onMountCallback { ctx =>
          val ns = "http://www.w3.org/2000/svg"
          val svg = dom.document.createElementNS(ns, "svg").asInstanceOf[dom.SVGElement]
          svg.setAttribute("class", "tl-detail-svg")
          svg.setAttribute("width", "100%")
          svg.setAttribute("height", DH.toString)
          detailSvg = svg
          ctx.thisNode.ref.appendChild(svg)
          dtUnderLayer = newLayer(svg, "tl-dt-under")
          dtCurveLayer = newLayer(svg, "tl-dt-curve")
          dtAxisLayer = newLayer(svg, "tl-dt-axis")
          dtOverlayLayer = newLayer(svg, "tl-dt-overlay")
          dtHoverLayer = newLayer(svg, "tl-hover")

          renderDetail()
          detailBars.signal.foreach { _ => lastDtBarsLen = -1; renderDetailCurve() }(using ctx.owner)
          overviewBars.signal.foreach(_ => renderDetailOverlay())(using ctx.owner)
          renderTick.signal.foreach(_ => renderDetailOverlay())(using ctx.owner)

          svg.addEventListener(
            "wheel",
            { (e: dom.WheelEvent) =>
              e.preventDefault()
              if e.deltaY != 0 then
                val rect = svg.getBoundingClientRect()
                val px = e.clientX - rect.left
                val w = if rect.width > 0 then rect.width else 600.0
                val spanYears = (spanTo.value - spanFrom.value).toDouble
                val anchor = spanFrom.value.toDouble + (px / w) * spanYears
                val factor = if e.deltaY > 0 then ZOOM else 1.0 / ZOOM
                val nf = Math.round(anchor - (anchor - spanFrom.value.toDouble) * factor)
                val nt = Math.round(anchor + (spanTo.value.toDouble - anchor) * factor)
                setSpan(Years.Astronomical(nf), Years.Astronomical(nt))
            },
            js.Dynamic.literal(passive = false).asInstanceOf[dom.EventListenerOptions]
          )

          svg.addEventListener(
            "pointerdown",
            (e: dom.PointerEvent) =>
              if e.button == 0 && !locked.now() then
                val rect = svg.getBoundingClientRect()
                val x = e.clientX - rect.left
                dragState.set(Some(DragInfo(x, x, DragKind.ZoomDetail, spanFrom.value, spanTo.value)))
                svg.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
          )

          svg.addEventListener(
            "pointermove",
            (e: dom.PointerEvent) =>
              if dragState.now().isEmpty then
                val rect = svg.getBoundingClientRect()
                val w = if rect.width > 0 then rect.width else 600.0
                val (tx, scale) = realign(dtDrawn, (spanFrom, spanTo, w))
                showHover(dtHoverLayer, hoverAt(dtPts, e.clientX - rect.left, tx, scale), w)
          )
          svg.addEventListener("pointerleave", (_: dom.PointerEvent) => showHover(dtHoverLayer, None, 0.0))

          svg.addEventListener(
            "pointermove",
            (e: dom.PointerEvent) =>
              dragState.now().foreach { d =>
                showHover(dtHoverLayer, None, 0.0)
                if d.kind == DragKind.ZoomDetail then
                  val rect = svg.getBoundingClientRect()
                  val cur = e.clientX - rect.left
                  dragState.set(Some(d.modify(_.curX).setTo(cur)))
                  bumpRender()
                  if Math.abs(cur - d.startX) >= 3 then
                    val w = elWidth(detailSvg)
                    val spanYears = (d.origTo - d.origFrom).toDouble
                    val y0 = d.origFrom + (Math.min(d.startX, cur) / w * spanYears).toLong
                    val y1 = d.origFrom + (Math.max(d.startX, cur) / w * spanYears).toLong
                    App.quickRenderIfCached(Years.Astronomical(y0), Years.Astronomical(y1))
              }
          )

          val endDrag: js.Function1[dom.PointerEvent, Unit] = { (_: dom.PointerEvent) =>
            dragState.now().foreach { d =>
              if d.kind == DragKind.ZoomDetail then
                dragState.set(None)
                if Math.abs(d.curX - d.startX) >= 3 then
                  val w = elWidth(detailSvg)
                  val spanYears = (d.origTo - d.origFrom).toDouble
                  val y0 = d.origFrom + (Math.min(d.startX, d.curX) / w * spanYears).toLong
                  val y1 = d.origFrom + (Math.max(d.startX, d.curX) / w * spanYears).toLong
                  setSpan(Years.Astronomical(y0), Years.Astronomical(y1))
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
                dtResizeRaf = dom.window.setTimeout(
                  () => {
                    if elWidth(svg) != lastDtWidth || dtHeight != lastDtHeight then
                      lastDtBarsLen = -1
                      renderDetail()
                  },
                  Layout.resizeSettleMs.value.toDouble
                )
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
              UiState.from.set(Some(cf2.value))
              UiState.to.set(Some(ct2.value))
            bumpRender()
            // only when what the strip shows changed: a layer toggled, a window opened or a tab
            // switched commits UiState too, and each refetched the strip for the same answer
            if detailKey() != lastDtKey then scheduleDetail()
          }(using ctx.owner)

          loadDetail()
        },
        onUnmountCallback { _ =>
          dtObserver.foreach { o =>
            val _ = o.disconnect()
          };
          dtObserver = None
          dom.window.clearTimeout(timer)
          // an answer still on its way would draw into the strip that left the page
          detailLatest.cancel()
        }
      ),
      div(
        cls := "tl-lock-note",
        cls("is-hidden") <-- locked.signal.map(!_),
        role := "status",
        Icon(Icon.Lock),
        span("The timeline is locked"),
        button(typ := "button", cls := "btn btn--sm tl-unlock", "Unlock", onClick --> (_ => setLocked(false)))
      ),
      // ── Controls: playback · the span shown · zoom to a period, show all time, fold
      div(
        cls := "tl-ctl",
        TimelinePlayback.element(config),
        div(
          cls := "tl-ctl-span",
          span(
            cls := "tl-span-label",
            aria.live := "polite",
            child.text <-- renderTick.signal.map(_ => Fmt.periodStr(Year(spanFrom.value), Year(spanTo.value)).value)
          )
        ),
        div(
          cls := "btn-group tl-ctl-end",
          role := "group",
          aria.label := "time span",
          div(
            cls := "tl-preset-wrap",
            button(
              typ := "button",
              cls := "btn btn--ghost tl-btn-presets",
              idAttr := "tl-zoom-period",
              title := "Choose a named period (as an authority dates it for a region) and show its years",
              aria.expanded <-- showPresets.signal,
              "Zoom to period ▾",
              onClick --> { _ =>
                presetView.set(ViewportToggle.viewBox)
                loadPresets()
                showPresets.update(!_)
              }
            ),
            presetsDropdown
          ),
          ContextMenu.button(
            ContextMenu.ButtonTitle("Timeline actions"),
            () => menuEntries(config),
            ContextMenu.ButtonExtraCls("tl-menu-btn")
          ),
          button(
            typ := "button",
            cls := "btn btn--ghost tl-all-time",
            idAttr := "tl-all-time",
            title := "Show every year the timeline holds",
            "Show all time",
            onClick --> { _ => setSpan(Years.Astronomical(limits.min.value), Years.Astronomical(limits.max.value)) }
          ),
          // shown in the narrow layout only, where the detail strip starts folded
          button(
            cls := "btn tl-detail-btn",
            typ := "button",
            aria.expanded <-- detailOpen.signal,
            title <-- detailOpen.signal.map(o => if o then "hide the detail strip" else "show the detail strip"),
            child.text <-- detailOpen.signal.map(o => if o then "detail ▴" else "detail ▾"),
            onClick --> (_ => detailOpen.update(!_))
          ),
          button(
            typ := "button",
            cls := "btn btn--ghost tl-fold",
            idAttr := "tl-fold",
            aria.expanded <-- folded.signal.map(!_),
            child.text <-- folded.signal.map(f => if f then "Show timeline ▴" else "Fold timeline ▾"),
            onClick --> (_ => setFolded(!folded.now()))
          ),
          badgeEl
        )
      )
    )
