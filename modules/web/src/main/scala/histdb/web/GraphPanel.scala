package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object GraphPanel:

  private case class SimNode(
      var id: String, var title: String, var year: Option[Int],
      var status: Option[String], var depth: Int,
      var x: Double, var y: Double, var vx: Double, var vy: Double,
      var pinned: Boolean
  )

  private case class SimLink(source: Int, target: Int, kind: String)

  private val EdgeColors: Map[String, String] = Map(
    "references"      -> "#6ea8fe",
    "challenges"      -> "#f0b7b2",
    "refutes"         -> "#e06660",
    "updates"         -> "#7dc97d",
    "superseded_by"   -> "#c4574f",
    "supersedes"      -> "#c4574f",
    "confirms"        -> "#5cb85c",
    "cites"           -> "#9aa3b2",
    "foundational_for" -> "#f0c040",
    "compilation_of"  -> "#b060d0",
    "review_of"       -> "#60b0d0",
    "verifies"        -> "#5cb85c"
  )

  private val StatusColors: Map[String, String] = Map(
    "foundational" -> "#f0c040",
    "current"      -> "#6ea8fe",
    "contested"    -> "#f0b7b2",
    "superseded"   -> "#9aa3b2",
    "debunked"     -> "#e06660",
    "retracted"    -> "#c4574f",
    "unassessed"   -> "#666"
  )

  def open(citekey: String): Unit =
    WindowManager.open(s"graph:$citekey", "graph", citekey)

  def render(citekey: String, container: dom.Element): Unit =
    container.innerHTML = "<div class='graph-loading'>loading graph…</div>"

    ApiClient.graph(citekey, Some(2)).foreach { g =>
      container.innerHTML = ""

      if g.nodes.length <= 1 then
        container.innerHTML = "<div class='graph-empty'>no citation edges for this source</div>"
      else
        val canvas = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
        canvas.className = "graph-canvas"
        container.appendChild(canvas)

        val legend = dom.document.createElement("div").asInstanceOf[dom.html.Div]
        legend.className = "graph-legend"
        val kinds = g.links.map(_.kind).distinct.sorted
        legend.innerHTML = kinds.map { k =>
          val color = EdgeColors.getOrElse(k, "#9aa3b2")
          s"""<span class="graph-legend-item"><span class="graph-legend-color" style="background:$color"></span>$k</span>"""
        }.mkString(" ")
        container.appendChild(legend)

        runSimulation(canvas, g)
    }

  private def runSimulation(canvas: dom.html.Canvas, g: SourceGraph): Unit =
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    val dpr = dom.window.devicePixelRatio.max(1.0)

    def resize(): Unit =
      val parent = canvas.parentElement
      if parent != null then
        val w = parent.clientWidth.max(200)
        val h = (parent.clientHeight - 30).max(200)
        canvas.style.width = s"${w}px"
        canvas.style.height = s"${h}px"
        canvas.width = (w * dpr).toInt
        canvas.height = (h * dpr).toInt
    resize()

    val idxMap = g.nodes.zipWithIndex.map((n, i) => n.id -> i).toMap
    val cw = canvas.width.toDouble / dpr
    val ch = canvas.height.toDouble / dpr

    val nodes: Array[SimNode] = g.nodes.zipWithIndex.map { (n, i) =>
      val angle = 2 * Math.PI * i / g.nodes.length
      val r = if n.id == g.root then 0.0 else 100.0 + n.depth * 60.0
      SimNode(n.id, n.title.getOrElse(n.id), n.year, n.status, n.depth,
        cw / 2 + r * Math.cos(angle), ch / 2 + r * Math.sin(angle),
        0, 0, n.id == g.root)
    }.toArray

    val links: Array[SimLink] = g.links.flatMap { l =>
      for s <- idxMap.get(l.source); t <- idxMap.get(l.target) yield SimLink(s, t, l.kind)
    }.toArray

    val REPULSION = 3000.0
    val SPRING_K  = 0.008
    val REST_LEN  = 120.0
    val DAMPING   = 0.92
    val DT        = 1.0
    val CENTERING = 0.01

    var frameId = 0
    var running = true
    var ticks   = 0
    val maxTicks = 300

    var dragNode: Option[Int] = None
    var hoverNode: Option[Int] = None
    var panX = 0.0
    var panY = 0.0
    var zoom = 1.0

    def screenToWorld(sx: Double, sy: Double): (Double, Double) =
      ((sx - panX) / zoom, (sy - panY) / zoom)

    def nodeAt(sx: Double, sy: Double): Option[Int] =
      val (wx, wy) = screenToWorld(sx, sy)
      var best = Option.empty[Int]
      var bestDist = 15.0 / zoom
      var i = 0
      while i < nodes.length do
        val n = nodes(i)
        val d = Math.sqrt((n.x - wx) * (n.x - wx) + (n.y - wy) * (n.y - wy))
        if d < bestDist then { best = Some(i); bestDist = d }
        i += 1
      best

    canvas.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
      val rect = canvas.getBoundingClientRect()
      val sx = (e.clientX - rect.left)
      val sy = (e.clientY - rect.top)
      nodeAt(sx, sy) match
        case Some(i) =>
          dragNode = Some(i)
          nodes(i).pinned = true
          canvas.asInstanceOf[js.Dynamic].setPointerCapture(e.pointerId)
        case None =>
    })

    canvas.addEventListener("pointermove", { (e: dom.PointerEvent) =>
      val rect = canvas.getBoundingClientRect()
      val sx = e.clientX - rect.left
      val sy = e.clientY - rect.top
      dragNode match
        case Some(i) =>
          val (wx, wy) = screenToWorld(sx, sy)
          nodes(i).x = wx
          nodes(i).y = wy
          ticks = 0; running = true
        case None =>
          val prev = hoverNode
          hoverNode = nodeAt(sx, sy)
          if prev != hoverNode then draw()
          canvas.style.cursor = if hoverNode.isDefined then "pointer" else "default"
    })

    canvas.addEventListener("pointerup", { (_: dom.PointerEvent) =>
      dragNode.foreach { i => nodes(i).pinned = i == idxMap.getOrElse(g.root, -1) }
      dragNode = None
    })

    canvas.addEventListener("dblclick", { (e: dom.MouseEvent) =>
      val rect = canvas.getBoundingClientRect()
      val sx = e.clientX - rect.left
      val sy = e.clientY - rect.top
      nodeAt(sx, sy).foreach { i =>
        val n = nodes(i)
        WindowManager.open(s"source:${n.id}", "source", n.id)
      }
    })

    canvas.addEventListener("wheel", { (e: dom.WheelEvent) =>
      e.preventDefault()
      val rect = canvas.getBoundingClientRect()
      val sx = e.clientX - rect.left
      val sy = e.clientY - rect.top
      val factor = if e.deltaY > 0 then 0.9 else 1.1
      val newZoom = (zoom * factor).max(0.2).min(5.0)
      panX = sx - (sx - panX) * (newZoom / zoom)
      panY = sy - (sy - panY) * (newZoom / zoom)
      zoom = newZoom
      draw()
    }, js.Dynamic.literal(passive = false).asInstanceOf[dom.EventListenerOptions])

    def step(): Unit =
      // Repulsion (all pairs)
      var i = 0
      while i < nodes.length do
        val ni = nodes(i)
        if !ni.pinned then
          var j = i + 1
          while j < nodes.length do
            val nj = nodes(j)
            val dx = ni.x - nj.x
            val dy = ni.y - nj.y
            val dist = Math.sqrt(dx * dx + dy * dy).max(1.0)
            val force = REPULSION / (dist * dist)
            val fx = (dx / dist) * force
            val fy = (dy / dist) * force
            ni.vx += fx; ni.vy += fy
            if !nj.pinned then { nj.vx -= fx; nj.vy -= fy }
            j += 1
        i += 1

      // Spring (links)
      for l <- links do
        val ns = nodes(l.source)
        val nt = nodes(l.target)
        val dx = nt.x - ns.x
        val dy = nt.y - ns.y
        val dist = Math.sqrt(dx * dx + dy * dy).max(1.0)
        val force = SPRING_K * (dist - REST_LEN)
        val fx = (dx / dist) * force
        val fy = (dy / dist) * force
        if !ns.pinned then { ns.vx += fx; ns.vy += fy }
        if !nt.pinned then { nt.vx -= fx; nt.vy -= fy }

      // Centering
      val cx = nodes.map(_.x).sum / nodes.length
      val cy = nodes.map(_.y).sum / nodes.length
      for n <- nodes if !n.pinned do
        n.vx += (cw / 2 - cx) * CENTERING
        n.vy += (ch / 2 - cy) * CENTERING

      // Integrate
      var maxV = 0.0
      for n <- nodes if !n.pinned do
        n.vx *= DAMPING
        n.vy *= DAMPING
        n.x += n.vx * DT
        n.y += n.vy * DT
        maxV = maxV.max(Math.abs(n.vx)).max(Math.abs(n.vy))

      ticks += 1
      if ticks > maxTicks || maxV < 0.1 then running = false

    def draw(): Unit =
      ctx.save()
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, cw, ch)
      ctx.translate(panX, panY)
      ctx.scale(zoom, zoom)

      // Links
      for l <- links do
        val ns = nodes(l.source)
        val nt = nodes(l.target)
        ctx.beginPath()
        ctx.moveTo(ns.x, ns.y)
        ctx.lineTo(nt.x, nt.y)
        ctx.strokeStyle = EdgeColors.getOrElse(l.kind, "#9aa3b2")
        ctx.lineWidth = if hoverNode.exists(h => h == l.source || h == l.target) then 2.0 else 1.0
        ctx.globalAlpha = if hoverNode.exists(h => h == l.source || h == l.target) then 1.0 else 0.5
        ctx.stroke()
        ctx.globalAlpha = 1.0

        // Arrow
        val dx = nt.x - ns.x; val dy = nt.y - ns.y
        val dist = Math.sqrt(dx * dx + dy * dy).max(1)
        val ux = dx / dist; val uy = dy / dist
        val ax = nt.x - ux * 8; val ay = nt.y - uy * 8
        ctx.beginPath()
        ctx.moveTo(nt.x, nt.y)
        ctx.lineTo(ax - uy * 3, ay + ux * 3)
        ctx.lineTo(ax + uy * 3, ay - ux * 3)
        ctx.closePath()
        ctx.fillStyle = EdgeColors.getOrElse(l.kind, "#9aa3b2")
        ctx.fill()

      // Nodes
      var i = 0
      while i < nodes.length do
        val n = nodes(i)
        val r = if n.id == nodes(idxMap.getOrElse(g.root, 0)).id then 8.0
                else if n.depth <= 1 then 6.0 else 4.5
        val isHovered = hoverNode.contains(i)

        ctx.beginPath()
        ctx.arc(n.x, n.y, r, 0, 2 * Math.PI)
        ctx.fillStyle = StatusColors.getOrElse(n.status.getOrElse("unassessed"), "#666")
        ctx.fill()
        if isHovered then
          ctx.strokeStyle = "#fff"
          ctx.lineWidth = 2
          ctx.stroke()

        // Label
        val label = if n.title.length > 30 then n.title.take(28) + "…" else n.title
        val yearStr = n.year.map(y => s" ($y)").getOrElse("")
        ctx.font = if isHovered then "bold 11px system-ui" else "10px system-ui"
        ctx.fillStyle = if isHovered then "#fff" else "#bbb"
        ctx.textAlign = "center"
        ctx.fillText(label + yearStr, n.x, n.y + r + 12)

        i += 1

      ctx.restore()

    def tick(): Unit =
      if running then
        step()
        draw()
        frameId = dom.window.requestAnimationFrame((_: Double) => tick())
      else draw()

    tick()

    if js.typeOf(js.Dynamic.global.ResizeObserver) != "undefined" then
      val obs = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(
        { (_: js.Any) => resize(); draw() }: js.Function1[js.Any, Unit]
      )
      obs.observe(canvas.parentElement)
