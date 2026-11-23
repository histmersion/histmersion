package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js

enum MarkerPaint:
  case Alpha(o: Double)
  case Restore
  case Box(
      at: (Double, Double, Double, Double),
      transform: Option[(Double, Double, Double, Double, Double, Double)],
      shadows: List[(MarkerLook.Shadow, dom.Path2D)],
      bg: String,
      image: Option[MarkerLook.Linear],
      outline: dom.Path2D,
      inner: dom.Path2D,
      edge: (Double, String, String),
      dash: js.Array[Double],
      opacity: Double
  )
  case Svg(shape: MarkerLook.SvgShape, at: (Double, Double, Double, Double, Double, Double), opacity: Double)
  case Text(
      own: String,
      font: String,
      colour: String,
      shadows: List[MarkerLook.Shadow],
      at: (Double, Double),
      opacity: Double
  )

  def run(ctx: dom.CanvasRenderingContext2D): Unit = this match
    case Alpha(o) =>
      ctx.save()
      ctx.globalAlpha = ctx.globalAlpha * o
    case Restore                                                                     => ctx.restore()
    case Box(at, transform, shadows, bg, image, outline, inner, edge, dash, opacity) =>
      val (x, y, w, h) = at
      val (bw, bstyle, bcolour) = edge
      ctx.save()
      ctx.globalAlpha = ctx.globalAlpha * opacity
      transform.foreach { (a, b, c, d, e, f) =>
        val (cx, cy) = (x + w / 2, y + h / 2)
        ctx.translate(cx, cy)
        ctx.transform(a, b, c, d, e, f)
        ctx.translate(-cx, -cy)
      }
      shadows.foreach { (s, p) =>
        if s.blur > 0 then
          ctx.save()
          ctx.shadowColor = MarkerLook.col(ctx, s.colour)
          ctx.shadowBlur = s.blur
          ctx.shadowOffsetX = s.dx
          ctx.shadowOffsetY = s.dy
          ctx.fillStyle = MarkerLook.col(ctx, s.colour)
          ctx.fill(p)
          ctx.restore()
        else if s.spread > 0 then
          ctx.fillStyle = MarkerLook.col(ctx, s.colour)
          ctx.fill(p, dom.CanvasFillRule.evenodd)
      }
      val fill = MarkerLook.col(ctx, bg)
      image.orElse(MarkerLook.linear(fill)) match
        case Some(g) =>
          ctx.fillStyle = MarkerLook.gradientFor(ctx, g, x, y, w, h)
          ctx.fill(outline)
        case None =>
          if MarkerLook.visible(fill) then
            ctx.fillStyle = fill
            ctx.fill(outline)
      if bw > 0 && bstyle != "none" && MarkerLook.visible(bcolour) then
        ctx.strokeStyle = MarkerLook.col(ctx, bcolour)
        ctx.lineWidth = bw
        ctx.setLineDash(dash)
        ctx.stroke(inner)
      ctx.restore()
    case Svg(s, at, opacity) =>
      val (ox, oy, sx, sy, vx, vy) = at
      val (bx, by, bw2, bh2) = s.bounds
      ctx.save()
      ctx.globalAlpha = ctx.globalAlpha * opacity
      ctx.translate(ox, oy)
      ctx.scale(sx, sy)
      ctx.translate(-vx, -vy)
      s.gradient match
        case Some((x1, y1, x2, y2, ss)) =>
          val grad = ctx.createLinearGradient(bx + x1 * bw2, by + y1 * bh2, bx + x2 * bw2, by + y2 * bh2)
          ss.foreach(st => grad.addColorStop(math.max(0, math.min(1, st.at)), MarkerLook.col(ctx, st.colour)))
          ctx.fillStyle = grad
          ctx.fill(s.p)
        case None =>
          if MarkerLook.visible(s.fill) then
            ctx.fillStyle = MarkerLook.col(ctx, s.fill)
            ctx.fill(s.p)
      if MarkerLook.visible(s.stroke) && s.sw > 0 then
        ctx.globalAlpha = ctx.globalAlpha * s.strokeOpacity
        ctx.strokeStyle = MarkerLook.col(ctx, s.stroke)
        ctx.lineWidth = s.sw
        ctx.lineCap = if s.cap.isEmpty then "butt" else s.cap
        ctx.lineJoin = if s.join.isEmpty then "miter" else s.join
        ctx.setLineDash(s.dashes)
        ctx.stroke(s.p)
      ctx.restore()
    case Text(own, font, colour, shadows, at, opacity) =>
      val (cx, cy) = at
      ctx.save()
      ctx.globalAlpha = ctx.globalAlpha * opacity
      ctx.font = font
      ctx.textAlign = "center"
      ctx.textBaseline = "middle"
      shadows.foreach { s =>
        ctx.save()
        ctx.shadowColor = MarkerLook.col(ctx, s.colour)
        ctx.shadowBlur = s.blur
        ctx.shadowOffsetX = s.dx
        ctx.shadowOffsetY = s.dy
        ctx.fillStyle = MarkerLook.col(ctx, colour)
        ctx.fillText(own, cx, cy)
        ctx.restore()
      }
      ctx.fillStyle = MarkerLook.col(ctx, colour)
      ctx.fillText(own, cx, cy)
      ctx.restore()
