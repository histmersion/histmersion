package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.{Palette, Wording}
import com.histmersion.web.ui.{GraphLayout as GL, ThemeTokens}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.{GraphNode, Label, SourceGraph}
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.Source

/** The citation graph of one source, in a window: a force layout on a canvas, a legend and the hovered node as a
  * [[SourceLink]].
  *
  * A Laminar component with a real lifecycle: the canvas follows a SIZE SIGNAL fed by one ResizeObserver, created on
  * mount and disconnected on unmount (closing the window), together with the animation frame — nothing outlives the
  * window, and nothing is added again per render.
  */
object GraphPanel:

  def open(citekey: String): Unit =
    WindowManager.open(WindowKey.Graph(citekey))

  private val Depth = 2

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The legend: the statuses and edge kinds this graph actually uses, in their enums' order — typed, so a new kind is
    * a compile-time decision, not a stray string.
    */
  def legend(g: SourceGraph): (List[Source.Status], List[Edge.Kind]) =
    val statuses = g.nodes.map(_.status.getOrElse(Source.Status.Unassessed)).toSet
    val kinds = g.links.flatMap(l => Edge.Kind.fromWire(l.kind)).toSet
    (Source.Status.values.toList.filter(statuses), Edge.Kind.values.toList.filter(kinds))

  /** Links as node-index pairs. Every link of a V026 graph joins two of its nodes, so none is lost; a link to a node
    * the graph does not carry is dropped rather than drawn to nowhere.
    */
  def indexLinks(g: SourceGraph): List[(Int, Int, String)] =
    val idx = g.nodes.zipWithIndex.map((n, i) => n.id -> i).toMap
    g.links.flatMap(l => for s <- idx.get(l.source); t <- idx.get(l.target) yield (s, t, l.kind))

  // ── the component ──────────────────────────────────────────────────────────────────────────────

  private enum Load:
    case Loading
    case Failed(message: String)
    case Loaded(g: SourceGraph)

  def element(citekey: String): HtmlElement =
    val state = Var[Load](Load.Loading)
    val hovered = Var(Option.empty[GraphNode])
    ApiClient.graph(citekey, Some(Depth)).onComplete {
      case scala.util.Success(g) =>
        state.set(Load.Loaded(g))
        g.nodes
          .find(_.id == g.root)
          .flatMap(_.title)
          .filter(_.trim.nonEmpty)
          .foreach(t => WindowManager.retitle(WindowKey.Graph(citekey), s"Citation graph · $t"))
      case scala.util.Failure(e) => state.set(Load.Failed(e.getMessage))
    }
    div(
      cls := "graph-panel",
      child <-- state.signal.map {
        case Load.Loading                      => div(cls := "graph-loading", "loading graph…")
        case Load.Failed(m)                    => div(cls := "graph-empty", s"could not load the graph: $m")
        case Load.Loaded(g) if g.links.isEmpty =>
          div(cls := "graph-empty", "no citation edges between stored sources for this source")
        case Load.Loaded(g) =>
          div(cls := "graph-body", legendView(g), stage(g, hovered), info(hovered.signal))
      }
    )

  private def legendView(g: SourceGraph): HtmlElement =
    val (statuses, kinds) = legend(g)
    div(
      cls := "graph-legend",
      statuses.map(s => span(cls := "graph-legend-item", StatusBadge(s))),
      kinds.map(k =>
        span(
          cls := "graph-legend-item",
          span(cls := "graph-legend-color", styleAttr := s"--c:var(${Palette.edge(Edge.Kind.wireOf(k))})"),
          Wording.capitalize(Label.display(Edge.Kind.wireOf(k)))
        )
      )
    )

  private def info(node: Signal[Option[GraphNode]]): HtmlElement =
    div(
      cls := "graph-info",
      child <-- node.map {
        case None    => span(cls := "muted", "hover a node · double-click opens it · drag to move · wheel to zoom")
        case Some(n) =>
          span(
            SourceLink(n.id),
            " ",
            n.title.getOrElse(""),
            n.year.fold("")(y => s" ($y)"),
            " ",
            n.status.fold(emptyNode)(s => StatusBadge(s))
          )
      }
    )

  final private case class SimNode(
      id: String,
      title: String,
      year: Option[Int],
      status: Option[Source.Status],
      depth: Int,
      var x: Double,
      var y: Double,
      var vx: Double,
      var vy: Double,
      var pinned: Boolean
  )

  /** The canvas and its simulation. Everything mutable lives inside this one mount. */
  private def stage(g: SourceGraph, hovered: Var[Option[GraphNode]]): HtmlElement =
    // the drawing area in CSS pixels — THE size: every frame reads it, so a resize re-centres
    val size = Var((0.0, 0.0))
    div(
      cls := "graph-stage",
      onMountUnmountCallbackWithState(
        mount = ctx =>
          val host = ctx.thisNode.ref
          val canvas = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
          canvas.className = "graph-canvas"
          host.appendChild(canvas)
          val sim = Simulation(g, canvas, () => size.now(), hovered)
          val measure: js.Function1[js.Any, Unit] =
            _ => size.set((host.clientWidth.toDouble, host.clientHeight.toDouble))
          val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(measure)
          obs.observe(host)
          size.signal.foreach(_ => sim.resized())(using ctx.owner)
          // a canvas cannot read CSS: it draws again with the new theme's colours
          Theme.changes.foreach(_ => sim.resized())(using ctx.owner)
          measure(())
          sim.start()
          (obs, sim)
        ,
        unmount = (_, st) =>
          st.foreach { (obs, sim) =>
            val _ = obs.disconnect()
            sim.stop()
          }
      )
    )

  /** The force layout and its drawing — the size is read from the signal on every frame instead of captured once at
    * start, and world (0,0) is kept at the canvas centre.
    */
  final private class Simulation(
      g: SourceGraph,
      canvas: dom.html.Canvas,
      size: () => (Double, Double),
      hovered: Var[Option[GraphNode]]
  ):
    private val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    private val dpr = dom.window.devicePixelRatio.max(1.0)
    private val byId = g.nodes.map(n => n.id -> n).toMap
    private val links = indexLinks(g).toArray
    private val nodes: Array[SimNode] = g.nodes.zipWithIndex.map { (n, i) =>
      val angle = 2 * Math.PI * i / g.nodes.length.max(1)
      val r = if n.id == g.root then 0.0 else 100.0 + n.depth * 60.0
      SimNode(
        n.id,
        n.title.getOrElse(n.id),
        n.year,
        n.status,
        n.depth,
        r * Math.cos(angle),
        r * Math.sin(angle),
        0,
        0,
        n.id == g.root
      )
    }.toArray
    private val rootIdx = nodes.indexWhere(_.id == g.root)

    // `var`s: the animation and the view are mutable by nature; they live and die with this mount
    private var frameId = 0
    private var ticks = 0
    private var running = false
    private var drag = Option.empty[Int]
    private var hover = Option.empty[Int]
    private var panX = 0.0
    private var panY = 0.0
    private var zoom = 1.0

    private def w = size()._1.max(1.0)
    private def h = size()._2.max(1.0)

    def resized(): Unit =
      canvas.style.width = s"${w}px"
      canvas.style.height = s"${h}px"
      canvas.width = (w * dpr).toInt
      canvas.height = (h * dpr).toInt
      draw()

    def start(): Unit =
      wire()
      running = true
      tick()

    def stop(): Unit =
      running = false
      dom.window.cancelAnimationFrame(frameId)

    private def toWorld(sx: Double, sy: Double): (Double, Double) =
      ((sx - w / 2 - panX) / zoom, (sy - h / 2 - panY) / zoom)

    private def nodeAt(sx: Double, sy: Double): Option[Int] =
      val (wx, wy) = toWorld(sx, sy)
      nodes.indices
        .map(i => i -> Math.hypot(nodes(i).x - wx, nodes(i).y - wy))
        .filter(_._2 < 15.0 / zoom)
        .minByOption(_._2)
        .map(_._1)

    private def local(e: dom.MouseEvent): (Double, Double) =
      val r = canvas.getBoundingClientRect()
      (e.clientX - r.left, e.clientY - r.top)

    private def wire(): Unit =
      canvas.addEventListener(
        "pointerdown",
        { (e: dom.PointerEvent) =>
          val (sx, sy) = local(e)
          nodeAt(sx, sy).foreach { i =>
            drag = Some(i)
            nodes(i).pinned = true
            canvas.setPointerCapture(e.pointerId)
          }
        }
      )
      canvas.addEventListener(
        "pointermove",
        { (e: dom.PointerEvent) =>
          val (sx, sy) = local(e)
          drag match
            case Some(i) =>
              val (wx, wy) = toWorld(sx, sy)
              nodes(i).x = wx; nodes(i).y = wy
              restart()
            case None =>
              val prev = hover
              hover = nodeAt(sx, sy)
              if prev != hover then
                hovered.set(hover.flatMap(i => byId.get(nodes(i).id)))
                draw()
              canvas.classList.toggle("hovering", hover.isDefined)
        }
      )
      canvas.addEventListener(
        "pointerup",
        { (_: dom.PointerEvent) =>
          drag.foreach(i => nodes(i).pinned = i == rootIdx)
          drag = None
        }
      )
      canvas.addEventListener(
        "dblclick",
        { (e: dom.MouseEvent) =>
          val (sx, sy) = local(e)
          nodeAt(sx, sy).foreach(i => ArticleModal.open(nodes(i).id))
        }
      )
      canvas.addEventListener(
        "wheel",
        { (e: dom.WheelEvent) =>
          e.preventDefault()
          val (sx, sy) = local(e)
          val factor = if e.deltaY > 0 then 0.9 else 1.1
          val newZoom = (zoom * factor).max(0.2).min(5.0)
          // keep the point under the cursor fixed
          val (cx, cy) = (sx - w / 2, sy - h / 2)
          panX = cx - (cx - panX) * (newZoom / zoom)
          panY = cy - (cy - panY) * (newZoom / zoom)
          zoom = newZoom
          draw()
        },
        js.Dynamic.literal(passive = false).asInstanceOf[dom.EventListenerOptions]
      )

    private def restart(): Unit =
      ticks = 0
      if !running then { running = true; tick() }

    private def step(): Unit =
      for i <- nodes.indices; j <- (i + 1) until nodes.length do
        val (a, b) = (nodes(i), nodes(j))
        val dx = a.x - b.x; val dy = a.y - b.y
        val dist = Math.hypot(dx, dy).max(1.0)
        val force = GL.repulsion / (dist * dist)
        val fx = dx / dist * force; val fy = dy / dist * force
        if !a.pinned then { a.vx += fx; a.vy += fy }
        if !b.pinned then { b.vx -= fx; b.vy -= fy }
      for (s, t, _) <- links do
        val (a, b) = (nodes(s), nodes(t))
        val dx = b.x - a.x; val dy = b.y - a.y
        val dist = Math.hypot(dx, dy).max(1.0)
        val force = GL.springK * (dist - GL.restLen)
        val fx = dx / dist * force; val fy = dy / dist * force
        if !a.pinned then { a.vx += fx; a.vy += fy }
        if !b.pinned then { b.vx -= fx; b.vy -= fy }
      // centring pulls towards the world origin, which the view keeps at the canvas centre
      val cx = nodes.map(_.x).sum / nodes.length
      val cy = nodes.map(_.y).sum / nodes.length
      var maxV = 0.0
      for n <- nodes if !n.pinned do
        n.vx = (n.vx - cx * GL.centering) * GL.damping
        n.vy = (n.vy - cy * GL.centering) * GL.damping
        n.x += n.vx; n.y += n.vy
        maxV = maxV.max(Math.abs(n.vx)).max(Math.abs(n.vy))
      ticks += 1
      if ticks > GL.maxTicks || maxV < 0.1 then running = false

    private def tick(): Unit =
      if running then
        step()
        draw()
        frameId = dom.window.requestAnimationFrame((_: Double) => tick())
      else draw()

    private def draw(): Unit =
      ctx.save()
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, w, h)
      ctx.translate(w / 2 + panX, h / 2 + panY)
      ctx.scale(zoom, zoom)
      for (s, t, kind) <- links do
        val (a, b) = (nodes(s), nodes(t))
        val lit = hover.exists(i => i == s || i == t)
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.strokeStyle = ThemeTokens(Palette.edge(kind))
        ctx.lineWidth = if lit then 2.0 else 1.0
        ctx.globalAlpha = if lit then 1.0 else 0.55
        ctx.stroke()
        ctx.globalAlpha = 1.0
        // the arrowhead, stopped short of the target's disc
        val dist = Math.hypot(b.x - a.x, b.y - a.y).max(1)
        val (ux, uy) = ((b.x - a.x) / dist, (b.y - a.y) / dist)
        val tip = radius(t) + 1
        val (px, py) = (b.x - ux * tip, b.y - uy * tip)
        val (ax, ay) = (px - ux * 8, py - uy * 8)
        ctx.beginPath()
        ctx.moveTo(px, py)
        ctx.lineTo(ax - uy * 3, ay + ux * 3)
        ctx.lineTo(ax + uy * 3, ay - ux * 3)
        ctx.closePath()
        ctx.fillStyle = ThemeTokens(Palette.edge(kind))
        ctx.fill()
      for i <- nodes.indices do
        val n = nodes(i)
        val on = hover.contains(i)
        ctx.beginPath()
        ctx.arc(n.x, n.y, radius(i), 0, 2 * Math.PI)
        ctx.fillStyle = ThemeTokens(Palette.status(n.status.getOrElse(Source.Status.Unassessed).wire))
        ctx.fill()
        if on then
          ctx.strokeStyle = ThemeTokens("--graph-hover")
          ctx.lineWidth = 2
          ctx.stroke()
        val label = if n.title.length > GL.labelMaxLen then n.title.take(GL.labelTruncLen) + "…" else n.title
        ctx.font = if on then "bold 11px system-ui" else "10px system-ui"
        ctx.fillStyle = ThemeTokens(if on then "--graph-label-hover" else "--graph-label")
        ctx.textAlign = "center"
        ctx.fillText(label + n.year.fold("")(y => s" ($y)"), n.x, n.y + radius(i) + 12)
      ctx.restore()

    private def radius(i: Int): Double =
      if i == rootIdx then GL.rootRadius else if nodes(i).depth <= 1 then GL.depth1Radius else GL.leafRadius
