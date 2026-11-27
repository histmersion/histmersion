package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.lint.{tolerated, LintRule}

final class MarkerLook(ops: List[MarkerPaint], val w: Double, val h: Double):
  private val pad = 12.0
  def sprite(colours: js.Array[String], dpr: Double): dom.html.Canvas =
    val c = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
    c.width = math.ceil((w + 2 * pad) * dpr).toInt
    c.height = math.ceil((h + 2 * pad) * dpr).toInt
    val g = c.getContext("2d", js.Dynamic.literal(willReadFrequently = true)).asInstanceOf[dom.CanvasRenderingContext2D]
    g.setTransform(dpr, 0, 0, dpr, 0, 0)
    g.translate(w / 2 + pad, h / 2 + pad)
    g.asInstanceOf[js.Dynamic].hdColours = colours
    ops.foreach(_.run(g))
    c

  var atlasId: Int = -1

  def spriteWidth(dpr: Double): Int = math.ceil((w + 2 * pad) * dpr).toInt

  def spriteHeight(dpr: Double): Int = math.ceil((h + 2 * pad) * dpr).toInt

  def render(g: dom.CanvasRenderingContext2D, ox: Double, oy: Double, colours: js.Array[String], dpr: Double): Unit =
    g.save()
    g.setTransform(dpr, 0, 0, dpr, ox, oy)
    g.translate(w / 2 + pad, h / 2 + pad)
    g.asInstanceOf[js.Dynamic].hdColours = colours
    ops.foreach(_.run(g))
    g.restore()

  def blit(
      ctx: dom.CanvasRenderingContext2D,
      atlas: dom.html.Canvas,
      region: js.Array[Double],
      dpr: Double,
      x: Double,
      y: Double,
      alpha: Double
  ): Unit =
    ctx.globalAlpha = alpha
    ctx.drawImage(
      atlas,
      region(1),
      region(2),
      region(3),
      region(4),
      x - w / 2 - pad,
      y - h / 2 - pad,
      region(3) / dpr,
      region(4) / dpr
    )
    ctx.globalAlpha = 1

object MarkerLook:
  type Op = MarkerPaint

  val Vars: List[String] = List("--c", "--g0", "--g1")
  val Sentinels: List[String] = List("rebeccapurple", "darkolivegreen", "mediumvioletred")
  private val SentinelRgb: List[(Int, Int, Int)] = List((102, 51, 153), (85, 107, 47), (199, 21, 133))
  val Second: List[String] = List("lightseagreen", "saddlebrown", "palevioletred")

  private val Rgb = "^rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)$".r

  def rgbOf(c: String): Option[(Int, Int, Int)] = c.trim match
    case Rgb(r, g, b) => Some((r.toInt, g.toInt, b.toInt))
    case _            => None

  def inlineVar(el: dom.Element, name: String): String =
    Option(el.getAttribute("style"))
      .getOrElse("")
      .split(";")
      .iterator
      .map(_.trim)
      .collectFirst { case d if d.startsWith(name + ":") => d.drop(name.length + 1).trim }
      .getOrElse("")

  private val VarDecl = "--(c|g0|g1):[^;\"]*;?".r
  private val BubbleId = "hdb\\d+".r
  private val Words = "\\s(aria-label|title|role|tabindex|data-id|data-ck|data-key)=\"[^\"]*\"".r

  val Transient: List[String] = List(
    "hd-cluster-move",
    "hd-cluster-arrive",
    "hd-cluster-ghost",
    "hd-cluster-pending",
    "hd-fade-in",
    "hd-fade-out",
    "hd-entering",
    "hd-exiting",
    "is-offscreen",
    "hd-spidered",
    "hd-spider"
  )

  private val TransientWord = Transient.map(t => s"\\b$t\\b").mkString("(", "|", ")").r

  def templateKey(el: dom.Element): String =
    val cls = Option(el.getAttribute("class")).getOrElse("").split(" ").filterNot(Transient.contains).mkString(" ")
    cls + "|" + Words.replaceAllIn(
      TransientWord.replaceAllIn(BubbleId.replaceAllIn(VarDecl.replaceAllIn(el.innerHTML, ""), ""), ""),
      ""
    )

  private val Placement = "\\s*--(tx|ty|z|sx|sy|ax|ay|move-x|move-y):[^;]*;?".r

  def rootKey(cls: String, style: String): String =
    cls.split(" ").filterNot(Transient.contains).mkString(" ") + "|" + Placement.replaceAllIn(style, "").trim

  def rootKey(el: dom.Element): String =
    rootKey(Option(el.getAttribute("class")).getOrElse(""), Option(el.getAttribute("style")).getOrElse(""))

  private val Text = ">[^<]+<".r

  def shapeKey(templateKey: String): String = Text.replaceAllIn(templateKey, "><")

  def colours(el: dom.Element): js.Array[String] =
    val all = el.querySelectorAll("[style]")
    val els = (0 until all.length).map(all(_))
    js.Array(Vars.map(v => els.iterator.map(inlineVar(_, v)).find(_.nonEmpty).getOrElse(""))*)

  def withColours(el: dom.Element, cs: List[String]): dom.Element =
    val clone = el.cloneNode(true).asInstanceOf[dom.Element]
    val every = clone.querySelectorAll("*")
    (clone +: (0 until every.length).map(every(_))).foreach(e => Transient.foreach(t => e.classList.remove(t)))
    val all = clone.querySelectorAll("[style]")
    (0 until all.length).map(all(_).asInstanceOf[dom.HTMLElement]).foreach { e =>
      cs.lift(0).filter(_ => inlineVar(e, "--c").nonEmpty).foreach(c => e.style.setProperty("--c", c))
      cs.lift(1).filter(_ => inlineVar(e, "--g0").nonEmpty).foreach(c => e.style.setProperty("--g0", c))
      cs.lift(2).filter(_ => inlineVar(e, "--g1").nonEmpty).foreach(c => e.style.setProperty("--g1", c))
    }
    clone

  def col(ctx: dom.CanvasRenderingContext2D, c: String): String =
    val i = rgbOf(c).fold(-1)(SentinelRgb.indexOf)
    if i < 0 then c
    else
      val cs = ctx.asInstanceOf[js.Dynamic].hdColours
      if js.isUndefined(cs) then c
      else
        val v = cs.asInstanceOf[js.Array[String]].lift(i).getOrElse("").trim
        if v.isEmpty then c else v

  private val Ws = "\\s+".r
  private val WsComma = "[\\s,]+".r
  private val Fn = "^(rgba?|hsla?|color)\\(".r
  private val Clear = "rgba\\(\\s*\\d+,\\s*\\d+,\\s*\\d+,\\s*0\\s*\\)".r

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
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

  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def polyPath(points: String, closed: Boolean): String =
    val ns = WsComma
      .split(points.trim)
      .toList
      .flatMap(_.toDoubleOption)
      .grouped(2)
      .collect { case List(x, y) =>
        (x, y)
      }
      .toList
    ns match
      case (x0, y0) :: rest =>
        (s"M $x0 $y0" :: rest.map((x, y) => s"L $x $y")).mkString(" ") + (if closed then " Z" else "")
      case Nil => ""

  private def attr(e: dom.Element, n: String): Double =
    Option(e.getAttribute(n)).flatMap(_.trim.toDoubleOption).getOrElse(0.0)

  def pathOf(e: dom.Element): Option[String] = e.tagName.toLowerCase match
    case "path"     => Option(e.getAttribute("d"))
    case "circle"   => Some(circlePath(attr(e, "cx"), attr(e, "cy"), attr(e, "r")))
    case "ellipse"  => Some(ellipsePath(attr(e, "cx"), attr(e, "cy"), attr(e, "rx"), attr(e, "ry")))
    case "line"     => Some(s"M ${attr(e, "x1")} ${attr(e, "y1")} L ${attr(e, "x2")} ${attr(e, "y2")}")
    case "polyline" => Some(polyPath(Option(e.getAttribute("points")).getOrElse(""), closed = false))
    case "polygon"  => Some(polyPath(Option(e.getAttribute("points")).getOrElse(""), closed = true))
    case "rect"     =>
      val rx = Option(e.getAttribute("rx")).flatMap(_.toDoubleOption)
      val ry = Option(e.getAttribute("ry")).flatMap(_.toDoubleOption)
      Some(
        rectPath(
          attr(e, "x"),
          attr(e, "y"),
          attr(e, "width"),
          attr(e, "height"),
          rx.orElse(ry).getOrElse(0.0),
          ry.orElse(rx).getOrElse(0.0)
        )
      )
    case _ => None

  def visible(c: String): Boolean =
    val t = c.trim
    t.nonEmpty && t != "transparent" && t != "none" && Clear.findFirstIn(t).isEmpty

  private def path2d(d: String): dom.Path2D =
    js.Dynamic.newInstance(js.Dynamic.global.Path2D)(d).asInstanceOf[dom.Path2D]

  private def dashOf(style: String, width: Double): js.Array[Double] = style match
    case "dashed" => js.Array(math.max(2.0, width * 2), math.max(2.0, width * 2))
    case "dotted" => js.Array(math.max(1.0, width), math.max(1.0, width))
    case _        => js.Array()

  def gradientFor(ctx: dom.CanvasRenderingContext2D, g: Linear, x: Double, y: Double, w: Double, h: Double) =
    val a = g.angleDeg.toRadians
    val (dx, dy) = (math.sin(a), -math.cos(a))
    val half = (math.abs(w * dx) + math.abs(h * dy)) / 2
    val (cx, cy) = (x + w / 2, y + h / 2)
    val grad = ctx.createLinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    g.stops.foreach(s => grad.addColorStop(math.max(0, math.min(1, s.at)), col(ctx, s.colour)))
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
          s -> path2d(
            rectPath(x - s.spread, y - s.spread, w + 2 * s.spread, h + 2 * s.spread, r + s.spread, r + s.spread)
          )
        else
          s -> path2d(
            rectPath(
              x - s.spread + s.dx,
              y - s.spread + s.dy,
              w + 2 * s.spread,
              h + 2 * s.spread,
              r + s.spread,
              r + s.spread
            ) + " " + rectPath(x, y, w, h, r, r)
          )
      }
      List(
        MarkerPaint.Box(
          (x, y, w, h),
          transform,
          shadowPaths,
          bg,
          image,
          outline,
          inner,
          (bw, bstyle, bcolour),
          dashOf(bstyle, bw),
          opacity
        )
      )

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
        val dashes = if dash.isEmpty || dash == "none" then js.Array[Double]()
        else js.Array(WsComma.split(dash).toSeq.flatMap(s => MarkerCanvas.px(s))*)
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
              (
                attr(g, "x1"),
                attr(g, "y1"),
                Option(g.getAttribute("x2")).flatMap(_.toDoubleOption).getOrElse(1.0),
                attr(g, "y2"),
                ss
              )
            }
          else None
        val bounds = e.asInstanceOf[js.Dynamic].getBBox().asInstanceOf[js.Dynamic]
        val (bx, by, bw2, bh2) =
          (
            bounds.x.asInstanceOf[Double],
            bounds.y.asInstanceOf[Double],
            bounds.width.asInstanceOf[Double],
            bounds.height.asInstanceOf[Double]
          )
        svgShapeOp(
          SvgShape(path2d(d), gradient, fill, stroke, sw, dashes, cap, join, strokeOpacity, (bx, by, bw2, bh2)),
          (ox, oy, sx, sy, vx, vy),
          opacity
        )
      }
    }

  final case class SvgShape(
      p: dom.Path2D,
      gradient: Option[(Double, Double, Double, Double, List[Stop])],
      fill: String,
      stroke: String,
      sw: Double,
      dashes: js.Array[Double],
      cap: String,
      join: String,
      strokeOpacity: Double,
      bounds: (Double, Double, Double, Double)
  )

  private def svgShapeOp(s: SvgShape, at: (Double, Double, Double, Double, Double, Double), opacity: Double): Op =
    MarkerPaint.Svg(s, at, opacity)

  private def textOps(e: dom.Element, cx: Double, cy: Double, opacity: Double): List[Op] =
    val own = (0 until e.childNodes.length)
      .map(e.childNodes(_))
      .collect {
        case t if t.nodeType == dom.Node.TEXT_NODE => t.textContent
      }
      .mkString
      .trim
    if own.isEmpty then Nil
    else
      val cs = dom.window.getComputedStyle(e)
      val font =
        s"${cs.getPropertyValue("font-weight")} ${cs.getPropertyValue("font-size")} ${cs.getPropertyValue("font-family")}"
      List(
        textOp(own, font, cs.getPropertyValue("color"), shadows(cs.getPropertyValue("text-shadow")), (cx, cy), opacity)
      )

  private def textOp(
      own: String,
      font: String,
      colour: String,
      shs: List[Shadow],
      at: (Double, Double),
      opacity: Double
  ): Op = MarkerPaint.Text(own, font, colour, shs, at, opacity)

  private def alphaOp(o: Double): Op = MarkerPaint.Alpha(o)

  private val restoreOp: Op = MarkerPaint.Restore

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
            val own = if e eq el then Nil
            else boxOps(x, y, r.width, r.height, cs, matrix(cs.getPropertyValue("transform")), 1.0)
            val kids = (0 until e.children.length).toList.flatMap(i => walk(e.children(i), o))
            val text = textOps(e, x + r.width / 2, y + r.height / 2, 1.0)
            (alphaOp(o) :: own) ++ beforeOps ++ text ++ (restoreOp :: kids)
      val ops = walk(el, 1.0)
      Some(MarkerLook(ops, box.width, box.height))
