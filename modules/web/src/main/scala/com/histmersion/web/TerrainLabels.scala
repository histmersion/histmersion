package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** The terrain map's names and water edges, drawn by symbolizers of our own in protomaps-leaflet's rules
  * (docs/research-terrain-basemap.md, "Names that never overlap").
  *
  * protomaps-leaflet's own text symbolizer spaces the lines of a wrapped name by the measured height of
  * its LONGEST line — "IONIAN" in capitals has no descender, so the next line came closer than the
  * letters are tall and two lines of one name overlapped — and its collision box left out the halo and
  * the lower lines' descenders, so neighbouring names touched. Here a name's box is its whole block —
  * every line at the font's own ascent and descent, the halo and a free margin — and the lines are drawn
  * exactly where the box says. The library's labeler then keeps one name per place across the tiles
  * of a zoom (its index is per zoom, not per tile) and repeats are dropped by name within a distance. */
object TerrainLabels:

  /** A name broken into lines of at most `max` characters, at spaces and hyphens (a word longer than
    * `max` stays whole). Pure. */
  def wrap(text: String, max: Int): List[String] =
    // the words, each with the space or hyphen after it
    val words = text.trim.foldLeft(List("")) { (acc, ch) =>
      val cur = acc.head + ch
      if ch == ' ' || ch == '-' then "" :: cur :: acc.tail else cur :: acc.tail
    }.reverse.filter(_.nonEmpty)
    words.foldLeft(List.empty[String]) { (acc, w) =>
      acc match
        case cur :: rest if (cur + w).trim.length <= max => (cur + w) :: rest
        case _                                              => w :: acc
    }.reverse.map(_.trim).filter(_.nonEmpty)

  /** The box of a name's block, centred on its anchor: `widths` of its lines, the font's `ascent` and
    * `descent`, the distance between baselines `lineGap`, and `margin` (halo + free space) all round.
    * Answers (half width, half height) and each line's baseline below the block's centre. Pure. */
  def block(widths: List[Double], ascent: Double, descent: Double, lineGap: Double, margin: Double): (Double, Double, List[Double]) =
    val n = math.max(1, widths.size)
    val h = ascent + descent + (n - 1) * lineGap
    val baselines = (0 until n).map(i => -h / 2 + ascent + i * lineGap).toList
    ((widths.maxOption.getOrElse(0.0)) / 2 + margin, h / 2 + margin, baselines)

  /** Apply a text transform. Pure. */
  def transform(s: String, t: Option[String]): String = t match
    case Some("uppercase")  => s.toUpperCase
    case Some("lowercase")  => s.toLowerCase
    case Some("capitalize") => s.split(" ").map(w => w.take(1).toUpperCase + w.drop(1)).mkString(" ")
    case _                  => s

  /** What a name symbolizer draws with: properties read for the text (first present wins), the font at a
    * zoom (`"italic 400 12px Noto Serif"`), its pixel size at a zoom, colours, the halo's width at a zoom,
    * the transform, letter spacing, the longest line, the free margin and the line height (× size). */
  final case class Spec(props: js.Array[String], font: Double => String, size: Double => Double, fill: String,
      halo: String, haloWidth: Double => Double, transform: Option[String], letterSpacing: Double, maxLineChars: Int,
      padding: Double, lineHeight: Double)

  private def widthOf(ctx: dom.CanvasRenderingContext2D, s: String, spacing: Double): Double =
    ctx.measureText(s).width + spacing * math.max(0, s.length - 1)

  /** Draw one line from `x` (its left end) on baseline `y`, letter-spaced by hand. */
  private def drawLine(ctx: dom.CanvasRenderingContext2D, s: String, x: Double, y: Double, spacing: Double, stroke: Boolean): Unit =
    if spacing <= 0 then
      if stroke then ctx.strokeText(s, x, y) else ctx.fillText(s, x, y)
    else
      var at = x
      s.foreach { ch =>
        val c = ch.toString
        if stroke then ctx.strokeText(c, at, y) else ctx.fillText(c, at, y)
        at += ctx.measureText(c).width + spacing
      }

  /** A protomaps-leaflet label symbolizer for points: `place(layout, geom, feature)` answers the name's
    * block — anchor, ONE box covering every line, the halo and the margin, and how to draw it. */
  def symbolizer(spec: Spec): js.Object =
    val place: js.Function3[js.Dynamic, js.Array[js.Array[js.Dynamic]], PmFeatureLike, js.Any] = (layout, geom, feature) =>
      val raw = spec.props.iterator.flatMap(p => feature.props.get(p)).map(_.toString).find(_.trim.nonEmpty)
      raw.fold[js.Any](js.undefined) { r =>
        val text  = transform(r, spec.transform)
        val zoom  = layout.zoom.asInstanceOf[Double]
        val ctx   = layout.scratch.asInstanceOf[dom.CanvasRenderingContext2D]
        val font  = spec.font(zoom)
        val size  = spec.size(zoom)
        ctx.font = font
        val lines  = wrap(text, spec.maxLineChars)
        val widths = lines.map(widthOf(ctx, _, spec.letterSpacing))
        val m      = ctx.measureText("Hg").asInstanceOf[js.Dynamic]
        def metric(k: String, fallback: Double) =
          val v = m.selectDynamic(k)
          if js.typeOf(v) == "number" && v.asInstanceOf[Double] > 0 then v.asInstanceOf[Double] else fallback
        val ascent  = metric("fontBoundingBoxAscent", size * 0.9)
        val descent = metric("fontBoundingBoxDescent", size * 0.3)
        val halo    = spec.haloWidth(zoom)
        val (hw, hh, baselines) = block(widths, ascent, descent, size * spec.lineHeight, halo + spec.padding)
        val anchor = geom(0)(0)
        val ax = anchor.x.asInstanceOf[Double]
        val ay = anchor.y.asInstanceOf[Double]
        val box = js.Dynamic.literal(minX = ax - hw, minY = ay - hh, maxX = ax + hw, maxY = ay + hh)
        val draw: js.Function1[dom.CanvasRenderingContext2D, Unit] = c =>
          c.globalAlpha = 1
          c.font = font
          c.textAlign = "left"
          c.textBaseline = "alphabetic"
          if halo > 0 then
            c.lineWidth = halo * 2
            c.lineJoin = "round"
            c.strokeStyle = spec.halo
            lines.lazyZip(widths).lazyZip(baselines).foreach((l, w, y) => drawLine(c, l, -w / 2, y, spec.letterSpacing, stroke = true))
          c.fillStyle = spec.fill
          lines.lazyZip(widths).lazyZip(baselines).foreach((l, w, y) => drawLine(c, l, -w / 2, y, spec.letterSpacing, stroke = false))
        js.Array(js.Dynamic.literal(anchor = anchor, bboxes = js.Array(box), draw = draw,
          deduplicationKey = text, deduplicationDistance = math.max(96.0, hw * 2)))
      }
    js.Dynamic.literal(place = place).asInstanceOf[js.Object]

  /** Whether the segment (x1, y1)–(x2, y2) is a tile's cut through an area rather than its outline: it
    * runs along the area's own bounding box, straight across or down. Pure. */
  def isCut(x1: Double, y1: Double, x2: Double, y2: Double, minX: Double, minY: Double, maxX: Double, maxY: Double): Boolean =
    (x1 == x2 && (x1 == minX || x1 == maxX)) || (y1 == y2 && (y1 == minY || y1 == maxY))

  /** A protomaps-leaflet paint symbolizer for areas: the fill, then — clipped to the area — a soft band
    * of `glow` along its outline (not along the tile's cuts through it), fading inwards over `width(z)`
    * pixels: the sea lighter along the shore. */
  def glowFill(fill: String, opacity: Double => Double, glow: String, width: Double => Double): js.Object =
    val draw: js.Function4[dom.CanvasRenderingContext2D, js.Array[js.Array[js.Dynamic]], Double, js.Any, Unit] = (ctx, geom, z, _) =>
      val path = new dom.Path2D()
      // `var`s: the area's bounding box, gathered while its path is built
      var minX = Double.MaxValue; var minY = Double.MaxValue; var maxX = -Double.MaxValue; var maxY = -Double.MaxValue
      geom.foreach { ring =>
        ring.zipWithIndex.foreach { (p, i) =>
          val x = p.x.asInstanceOf[Double]; val y = p.y.asInstanceOf[Double]
          if i == 0 then path.moveTo(x, y) else path.lineTo(x, y)
          minX = math.min(minX, x); minY = math.min(minY, y); maxX = math.max(maxX, x); maxY = math.max(maxY, y)
        }
        path.closePath()
      }
      ctx.save()
      ctx.globalAlpha = opacity(z)
      ctx.fillStyle = fill
      ctx.fill(path)
      val w = width(z)
      if w > 0 then
        // the outline without the cuts: each ring's segments, the closing one too
        val edge = new dom.Path2D()
        geom.foreach { ring =>
          val n = ring.length
          var i = 0
          while i < n do
            val a = ring(i); val b = ring((i + 1) % n)
            val x1 = a.x.asInstanceOf[Double]; val y1 = a.y.asInstanceOf[Double]
            val x2 = b.x.asInstanceOf[Double]; val y2 = b.y.asInstanceOf[Double]
            if !isCut(x1, y1, x2, y2, minX, minY, maxX, maxY) then { edge.moveTo(x1, y1); edge.lineTo(x2, y2) }
            i += 1
        }
        ctx.clip(path)
        ctx.strokeStyle = glow
        ctx.lineJoin = "round"
        ctx.lineCap = "round"
        // three bands, wide and faint to narrow: a fade towards the open water
        List((2.0, 0.35), (1.2, 0.35), (0.5, 0.45)).foreach { (k, a) =>
          ctx.globalAlpha = a
          ctx.lineWidth = w * k
          ctx.stroke(edge)
        }
      ctx.restore()
    js.Dynamic.literal(draw = draw).asInstanceOf[js.Object]

/** A feature as a symbolizer's `place` sees it: its tags. */
@js.native
trait PmFeatureLike extends js.Object:
  val props: js.Dictionary[js.Any] = js.native
