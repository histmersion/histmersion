package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js

final class MarkerLook(ops: List[js.Function1[dom.CanvasRenderingContext2D, Unit]], val w: Double, val h: Double):
  private val pad = 12.0
  private var sprite: dom.html.Canvas = null
  private var spriteDpr = 0.0

  private def spriteFor(dpr: Double): dom.html.Canvas =
    if sprite == null || spriteDpr != dpr then
      val c = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
      c.width = math.ceil((w + 2 * pad) * dpr).toInt
      c.height = math.ceil((h + 2 * pad) * dpr).toInt
      val g = c.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
      g.setTransform(dpr, 0, 0, dpr, 0, 0)
      g.translate(w / 2 + pad, h / 2 + pad)
      ops.foreach(_(g))
      sprite = c
      spriteDpr = dpr
    sprite

  def draw(ctx: dom.CanvasRenderingContext2D, x: Double, y: Double, alpha: Double): Unit =
    val s = spriteFor(dom.window.devicePixelRatio)
    ctx.globalAlpha = alpha
    ctx.drawImage(s, x - w / 2 - pad, y - h / 2 - pad, w + 2 * pad, h + 2 * pad)
    ctx.globalAlpha = 1

object MarkerLook:
  type Op = js.Function1[dom.CanvasRenderingContext2D, Unit]

  private val Ws = "\\s+".r
  private val WsComma = "[\\s,]+".r
  private val Fn = "^(rgba?|hsla?|color)\\(".r
  private val Clear = "rgba\\(\\s*\\d+,\\s*\\d+,\\s*\\d+,\\s*0\\s*\\)".r

  final case class Shadow(colour: String, dx: Double, dy: Double, blur: Double, spread: Double, inset: Boolean)

  final case class Stop(colour: String, at: Double)

  final case class Linear(angleDeg: Double, stops: List[Stop])

  def key(el: dom.Element): String =
    Option(el.getAttribute("class")).getOrElse("") + "|" + Option(el.getAttribute("style")).getOrElse("") + "|" +
      el.innerHTML

  def splitTop(s: String): List[String] =
    val out = List.newBuilder[String]
    val cur = new StringBuilder
    var depth = 0
    s.foreach { c =>
      if c == '(' then depth += 1
      if c == ')' then depth -= 1
      if c == ',' && depth == 0 then
        out += cur.toString.trim
        cur.clear()
      else cur += c
    }
    if cur.toString.trim.nonEmpty then out += cur.toString.trim
    out.result()

  private def colourAndRest(part: String): (String, String) =
    val p = part.trim
    if Fn.findFirstIn(p).isDefined then
      val close = p.indexOf(')')
      (p.substring(0, close + 1), p.substring(close + 1).trim)
    else
      val words = Ws.split(p).toList
      words.partition(w => w.headOption.exists(c => c.isLetter || c == '#')) match
        case (c :: _, rest) => (c, rest.mkString(" "))
        case (Nil, rest)    => ("", rest.mkString(" "))

  def shadows(s: String): List[Shadow] =
    if s.trim.isEmpty || s.trim == "none" then Nil
    else
      splitTop(s).flatMap { part =>
        val inset = part.contains("inset")
        val (colour, rest) = colourAndRest(part.replace("inset", ""))
        val ns = Ws.split(rest).toList.flatMap(MarkerCanvas.px)
        ns match
          case dx :: dy :: tail =>
            Some(Shadow(colour, dx, dy, tail.headOption.getOrElse(0.0), tail.drop(1).headOption.getOrElse(0.0), inset))
          case _ => None
      }

  def linear(s: String): Option[Linear] =
    val t = s.trim
    if !t.startsWith("linear-gradient(") then None
    else
      val body = t.stripPrefix("linear-gradient(").stripSuffix(")")
      val parts = splitTop(body)
      val (angle, stopParts) = parts.headOption match
        case Some(a) if a.endsWith("deg")   => (a.stripSuffix("deg").trim.toDoubleOption.getOrElse(180.0), parts.tail)
        case Some(a) if a.startsWith("to ") => (toAngle(a), parts.tail)
        case _                              => (180.0, parts)
      val raw = stopParts.map { p =>
        val (c, rest) = colourAndRest(p)
        (c, Ws.split(rest).toList.headOption.flatMap(r => r.stripSuffix("%").toDoubleOption).map(_ / 100))
      }
      val n = raw.size
      val stops = raw.zipWithIndex.map { case ((c, at), i) =>
        Stop(c, at.getOrElse(if n <= 1 then 0.0 else i.toDouble / (n - 1)))
      }
      Option.when(stops.nonEmpty)(Linear(angle, stops))

  private def toAngle(dir: String): Double = dir.trim match
    case "to top"          => 0.0
    case "to right"        => 90.0
    case "to bottom"       => 180.0
    case "to left"         => 270.0
    case "to top right"    => 45.0
    case "to right top"    => 45.0
    case "to bottom right" => 135.0
    case "to right bottom" => 135.0
    case "to bottom left"  => 225.0
    case "to left bottom"  => 225.0
    case "to top left"     => 315.0
    case "to left top"     => 315.0
    case _                 => 180.0

  def matrix(s: String): Option[(Double, Double, Double, Double, Double, Double)] =
    val t = s.trim
    if !t.startsWith("matrix(") then None
    else
      t.stripPrefix("matrix(").stripSuffix(")").split(",").toList.flatMap(_.trim.toDoubleOption) match
        case List(a, b, c, d, e, f) => Some((a, b, c, d, e, f))
        case _                      => None

  def radius(s: String, w: Double, h: Double): Double =
    val t = Ws.split(s.trim).headOption.getOrElse("0")
    val r =
      if t.endsWith("%") then t.stripSuffix("%").toDoubleOption.getOrElse(0.0) / 100 * math.min(w, h)
      else MarkerCanvas.px(t).getOrElse(0.0)
    math.min(r, math.min(w, h) / 2)

  def num(s: String): Double = s.trim.stripSuffix("px").toDoubleOption.getOrElse(0.0)

  def circlePath(cx: Double, cy: Double, r: Double): String =
    s"M ${cx - r} $cy A $r $r 0 1 0 ${cx + r} $cy A $r $r 0 1 0 ${cx - r} $cy Z"

  def ellipsePath(cx: Double, cy: Double, rx: Double, ry: Double): String =
    s"M ${cx - rx} $cy A $rx $ry 0 1 0 ${cx + rx} $cy A $rx $ry 0 1 0 ${cx - rx} $cy Z"

  def rectPath(x: Double, y: Double, w: Double, h: Double, rx0: Double, ry0: Double): String =
    val rx = math.min(rx0, w / 2)
    val ry = math.min(ry0, h / 2)
    if rx <= 0 || ry <= 0 then s"M $x $y H ${x + w} V ${y + h} H $x Z"
    else
      s"M ${x + rx} $y H ${x + w - rx} A $rx $ry 0 0 1 ${x + w} ${y + ry} V ${y + h - ry} " +
        s"A $rx $ry 0 0 1 ${x + w - rx} ${y + h} H ${x + rx} A $rx $ry 0 0 1 $x ${y + h - ry} V ${y + ry} " +
        s"A $rx $ry 0 0 1 ${x + rx} $y Z"

  def polyPath(points: String, closed: Boolean): String =
    val ns = WsComma.split(points.trim).toList.flatMap(_.toDoubleOption).grouped(2).collect { case List(x, y) =>
      (x, y)
    }.toList
    ns match
      case (x0, y0) :: rest => (s"M $x0 $y0" :: rest.map((x, y) => s"L $x $y")).mkString(" ") + (if closed then " Z" else "")
      case Nil              => ""

  private def attr(e: dom.Element, n: String): Double = Option(e.getAttribute(n)).flatMap(_.trim.toDoubleOption).getOrElse(0.0)

  def pathOf(e: dom.Element): Option[String] = e.tagName.toLowerCase match
    case "path"     => Option(e.getAttribute("d"))
    case "circle"   => Some(circlePath(attr(e, "cx"), attr(e, "cy"), attr(e, "r")))
    case "ellipse"  => Some(ellipsePath(attr(e, "cx"), attr(e, "cy"), attr(e, "rx"), attr(e, "ry")))
    case "line"     => Some(s"M ${attr(e, "x1")} ${attr(e, "y1")} L ${attr(e, "x2")} ${attr(e, "y2")}")
    case "polyline" => Some(polyPath(Option(e.getAttribute("points")).getOrElse(""), closed = false))
    case "polygon"  => Some(polyPath(Option(e.getAttribute("points")).getOrElse(""), closed = true))
    case "rect" =>
      val rx = Option(e.getAttribute("rx")).flatMap(_.toDoubleOption)
      val ry = Option(e.getAttribute("ry")).flatMap(_.toDoubleOption)
      Some(
        rectPath(attr(e, "x"), attr(e, "y"), attr(e, "width"), attr(e, "height"), rx.orElse(ry).getOrElse(0.0),
          ry.orElse(rx).getOrElse(0.0))
      )
    case _ => None

  private def visible(c: String): Boolean =
    val t = c.trim
    t.nonEmpty && t != "transparent" && t != "none" && Clear.findFirstIn(t).isEmpty

  private def path2d(d: String): dom.Path2D = js.Dynamic.newInstance(js.Dynamic.global.Path2D)(d).asInstanceOf[dom.Path2D]

  private def dashOf(style: String, width: Double): js.Array[Double] = style match
    case "dashed" => js.Array(math.max(2.0, width * 2), math.max(2.0, width * 2))
    case "dotted" => js.Array(math.max(1.0, width), math.max(1.0, width))
    case _        => js.Array()

  private def gradientFor(ctx: dom.CanvasRenderingContext2D, g: Linear, x: Double, y: Double, w: Double, h: Double) =
    val a = g.angleDeg.toRadians
    val (dx, dy) = (math.sin(a), -math.cos(a))
    val half = (math.abs(w * dx) + math.abs(h * dy)) / 2
    val (cx, cy) = (x + w / 2, y + h / 2)
    val grad = ctx.createLinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    g.stops.foreach(s => grad.addColorStop(math.max(0, math.min(1, s.at)), s.colour))
    grad

  private def boxOps(
      x: Double,
      y: Double,
      w: Double,
      h: Double,
      cs: dom.CSSStyleDeclaration,
      transform: Option[(Double, Double, Double, Double, Double, Double)],
      opacity: Double
  ): List[Op] =
    val r = radius(cs.getPropertyValue("border-top-left-radius"), w, h)
    val bw = num(cs.getPropertyValue("border-top-width"))
    val bstyle = cs.getPropertyValue("border-top-style").trim
    val bcolour = cs.getPropertyValue("border-top-color")
    val bg = cs.getPropertyValue("background-color")
    val image = linear(cs.getPropertyValue("background-image"))
    val shs = shadows(cs.getPropertyValue("box-shadow")).filterNot(_.inset)
    if !visible(bg) && image.isEmpty && (bw <= 0 || bstyle == "none") && shs.isEmpty then Nil
    else
      val outline = path2d(rectPath(x, y, w, h, r, r))
      val inner =
        path2d(rectPath(x + bw / 2, y + bw / 2, w - bw, h - bw, math.max(0, r - bw / 2), math.max(0, r - bw / 2)))
      val shadowPaths = shs.reverse.map { s =>
        if s.blur > 0 then
          s -> path2d(rectPath(x - s.spread, y - s.spread, w + 2 * s.spread, h + 2 * s.spread, r + s.spread, r + s.spread))
        else
          s -> path2d(
            rectPath(x - s.spread + s.dx, y - s.spread + s.dy, w + 2 * s.spread, h + 2 * s.spread, r + s.spread,
              r + s.spread) + " " + rectPath(x, y, w, h, r, r)
          )
      }
      val dash = dashOf(bstyle, bw)
      val op: Op = (ctx: dom.CanvasRenderingContext2D) =>
        ctx.save()
        ctx.globalAlpha = ctx.globalAlpha * opacity
        transform.foreach { (a, b, c, d, e, f) =>
          val (cx, cy) = (x + w / 2, y + h / 2)
          ctx.translate(cx, cy)
          ctx.transform(a, b, c, d, e, f)
          ctx.translate(-cx, -cy)
        }
        shadowPaths.foreach { (s, p) =>
          if s.blur > 0 then
            ctx.save()
            ctx.shadowColor = s.colour
            ctx.shadowBlur = s.blur
            ctx.shadowOffsetX = s.dx
            ctx.shadowOffsetY = s.dy
            ctx.fillStyle = s.colour
            ctx.fill(p)
            ctx.restore()
          else if s.spread > 0 then
            ctx.fillStyle = s.colour
            ctx.fill(p, dom.CanvasFillRule.evenodd)
        }
        image match
          case Some(g) =>
            ctx.fillStyle = gradientFor(ctx, g, x, y, w, h)
            ctx.fill(outline)
          case None =>
            if visible(bg) then
              ctx.fillStyle = bg
              ctx.fill(outline)
        if bw > 0 && bstyle != "none" && visible(bcolour) then
          ctx.strokeStyle = bcolour
          ctx.lineWidth = bw
          ctx.setLineDash(dash)
          ctx.stroke(inner)
        ctx.restore()
      List(op)

  private def svgOps(svg: dom.Element, ox: Double, oy: Double, opacity: Double): List[Op] =
    val box = svg.getBoundingClientRect()
    val vb = Option(svg.getAttribute("viewBox")).map(v => WsComma.split(v.trim).toList.flatMap(_.toDoubleOption))
    val (vx, vy, vw, vh) = vb match
      case Some(List(a, b, c, d)) => (a, b, c, d)
      case _                      => (0.0, 0.0, box.width, box.height)
    val sx = if vw > 0 then box.width / vw else 1.0
    val sy = if vh > 0 then box.height / vh else 1.0
    val shapes = svg.querySelectorAll("path, circle, rect, line, polyline, polygon, ellipse")
    (0 until shapes.length).toList.map(shapes(_)).filter(e => e.closest("defs") == null).flatMap { e =>
      pathOf(e).filter(_.nonEmpty).map { d =>
        val cs = dom.window.getComputedStyle(e)
        val fill = cs.getPropertyValue("fill").trim
        val stroke = cs.getPropertyValue("stroke").trim
        val sw = num(cs.getPropertyValue("stroke-width"))
        val dash = cs.getPropertyValue("stroke-dasharray").trim
        val dashes = if dash.isEmpty || dash == "none" then js.Array[Double]() else js.Array(WsComma.split(dash).toSeq.flatMap(s => MarkerCanvas.px(s))*)
        val cap = cs.getPropertyValue("stroke-linecap").trim
        val join = cs.getPropertyValue("stroke-linejoin").trim
        val strokeOpacity = cs.getPropertyValue("stroke-opacity").trim.toDoubleOption.getOrElse(1.0)
        val gradient =
          if fill.startsWith("url(") then
            val id = fill.stripPrefix("url(").stripSuffix(")").replace("\"", "").replace("'", "").stripPrefix("#")
            Option(svg.querySelector(s"[id='$id']")).map { g =>
              val stops = g.querySelectorAll("stop")
              val ss = (0 until stops.length).toList.map { i =>
                val st = stops(i)
                Stop(dom.window.getComputedStyle(st).getPropertyValue("stop-color"), attr(st, "offset"))
              }
              (attr(g, "x1"), attr(g, "y1"), Option(g.getAttribute("x2")).flatMap(_.toDoubleOption).getOrElse(1.0),
                attr(g, "y2"), ss)
            }
          else None
        val bounds = e.asInstanceOf[js.Dynamic].getBBox().asInstanceOf[js.Dynamic]
        val (bx, by, bw2, bh2) =
          (bounds.x.asInstanceOf[Double], bounds.y.asInstanceOf[Double], bounds.width.asInstanceOf[Double],
            bounds.height.asInstanceOf[Double])
        val p = path2d(d)
        val op: Op = (ctx: dom.CanvasRenderingContext2D) =>
          ctx.save()
          ctx.globalAlpha = ctx.globalAlpha * opacity
          ctx.translate(ox, oy)
          ctx.scale(sx, sy)
          ctx.translate(-vx, -vy)
          gradient match
            case Some((x1, y1, x2, y2, ss)) =>
              val grad = ctx.createLinearGradient(bx + x1 * bw2, by + y1 * bh2, bx + x2 * bw2, by + y2 * bh2)
              ss.foreach(s => grad.addColorStop(math.max(0, math.min(1, s.at)), s.colour))
              ctx.fillStyle = grad
              ctx.fill(p)
            case None =>
              if visible(fill) then
                ctx.fillStyle = fill
                ctx.fill(p)
          if visible(stroke) && sw > 0 then
            ctx.globalAlpha = ctx.globalAlpha * strokeOpacity
            ctx.strokeStyle = stroke
            ctx.lineWidth = sw
            ctx.lineCap = if cap.isEmpty then "butt" else cap
            ctx.lineJoin = if join.isEmpty then "miter" else join
            ctx.setLineDash(dashes)
            ctx.stroke(p)
          ctx.restore()
        op
      }
    }

  private def textOps(e: dom.Element, cx: Double, cy: Double, opacity: Double): List[Op] =
    val own = (0 until e.childNodes.length).map(e.childNodes(_)).collect {
      case t if t.nodeType == dom.Node.TEXT_NODE => t.textContent
    }.mkString.trim
    if own.isEmpty then Nil
    else
      val cs = dom.window.getComputedStyle(e)
      val font = s"${cs.getPropertyValue("font-weight")} ${cs.getPropertyValue("font-size")} ${cs.getPropertyValue("font-family")}"
      val colour = cs.getPropertyValue("color")
      val shs = shadows(cs.getPropertyValue("text-shadow"))
      val op: Op = (ctx: dom.CanvasRenderingContext2D) =>
        ctx.save()
        ctx.globalAlpha = ctx.globalAlpha * opacity
        ctx.font = font
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        shs.foreach { s =>
          ctx.save()
          ctx.shadowColor = s.colour
          ctx.shadowBlur = s.blur
          ctx.shadowOffsetX = s.dx
          ctx.shadowOffsetY = s.dy
          ctx.fillStyle = colour
          ctx.fillText(own, cx, cy)
          ctx.restore()
        }
        ctx.fillStyle = colour
        ctx.fillText(own, cx, cy)
        ctx.restore()
      List(op)

  def of(el: dom.Element): Option[MarkerLook] =
    val box = el.getBoundingClientRect()
    if box.width == 0 && box.height == 0 && el.querySelector("svg") == null then None
    else
      val (cx, cy) = (box.left + box.width / 2, box.top + box.height / 2)
      def walk(e: dom.Element, opacity: Double): List[Op] =
        val cs = dom.window.getComputedStyle(e)
        if cs.getPropertyValue("display").trim == "none" || cs.getPropertyValue("visibility").trim == "hidden" then Nil
        else
          val o = opacity * cs.getPropertyValue("opacity").trim.toDoubleOption.getOrElse(1.0)
          if e.tagName.toLowerCase == "svg" then
            val r = e.getBoundingClientRect()
            svgOps(e, r.left - cx, r.top - cy, o)
          else
            val r = e.getBoundingClientRect()
            val (x, y) = (r.left - cx, r.top - cy)
            val before = dom.window.getComputedStyle(e, "::before")
            val beforeOps =
              val content = before.getPropertyValue("content").trim
              if content.isEmpty || content == "none" || content == "normal" then Nil
              else
                val bw = num(before.getPropertyValue("width"))
                val bh = num(before.getPropertyValue("height"))
                val bl = num(before.getPropertyValue("left"))
                val bt = num(before.getPropertyValue("top"))
                boxOps(x + bl, y + bt, bw, bh, before, matrix(before.getPropertyValue("transform")), 1.0)
            val own = if e eq el then Nil else boxOps(x, y, r.width, r.height, cs, matrix(cs.getPropertyValue("transform")), 1.0)
            val kids = (0 until e.children.length).toList.flatMap(i => walk(e.children(i), o))
            val text = textOps(e, x + r.width / 2, y + r.height / 2, 1.0)
            List[Op]((ctx: dom.CanvasRenderingContext2D) => { ctx.save(); ctx.globalAlpha = ctx.globalAlpha * o }) ++
              own ++ beforeOps ++ text ++ List[Op]((ctx: dom.CanvasRenderingContext2D) => ctx.restore()) ++ kids
      val ops = walk(el, 1.0)
      Some(MarkerLook(ops, box.width, box.height))
