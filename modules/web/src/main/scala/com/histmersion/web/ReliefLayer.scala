package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.typedarray.{Float32Array, Int16Array, Uint8ClampedArray}

/** The terrain map's relief layers (docs/research-terrain-basemap.md, "The Natural style"): the
  * elevation tiles the server serves — the extract, a focus region or the on-demand relief, to zoom 12 —
  * drawn as two Leaflet grid layers:
  *
  *  - the TINT, under the vector layer: the ground coloured by its elevation ([[Relief.tint]]), which the
  *    land cover's translucent fills and the opaque water lie over (a style without `tint` has none);
  *  - the SHADING, over it: the slopes' shadows and highlights ([[Relief.shade]]) and, from the style's
  *    zoom on, the contour lines and their elevations ([[Contours]]).
  *
  * Both are drawn at the screen's own pixel density from ONE computation per tile: its elevation window
  * — with its neighbours' edge pixels, so no seam shows at a tile's edge — resampled bicubic to the
  * pixels it covers, so a zoom deeper than the data is smooth, never blocky. The decoded elevation tiles
  * are kept in a bounded cache ([[stats]]); a tile is computed once, when it arrives — never during a
  * gesture (Leaflet asks for tiles when a pan or zoom ends). */
object ReliefLayer:

  /** Everything a tile needs, resolved once per drawing. */
  final case class Setup(url: String, dataMax: Int, relief: ReliefStyle, lights: Relief.Lights,
      shadow: (Int, Int, Int), shadowAlpha: Int, highlight: (Int, Int, Int), highlightAlpha: Int,
      lut: Option[Relief.Lut], contours: Option[ContourDraw], dpr: Double,
      seaLut: Option[Relief.Lut] = None, bathymetry: Option[(String, Int)] = None, extractMax: Int = 8):
    /** Whether there is a tint layer: the land by its height, or the sea by its depth. */
    def tinted: Boolean = lut.isDefined || seaLut.isDefined

  /** The contours as drawn: the style's, its colours resolved. */
  final case class ContourDraw(spec: ContourStyle, color: String, indexColor: String, labelColor: String, halo: String,
      depthColor: String)

  // the decoded elevation tiles: 512 KB each (whole metres as 16-bit integers); the sea floor's, 128 KB
  private val dems = BoundedCache[String, Future[Option[Int16Array]]](16)
  private val seas = BoundedCache[String, Future[Option[Int16Array]]](16)

  /** The cache's figures, for the page's diagnostics. */
  def stats: BoundedCache.Stats = BoundedCache.stats(dems)
  def seaStats: BoundedCache.Stats = BoundedCache.stats(seas)

  // which drawing the jobs belong to; a new drawing (another style, another map) starts afresh. `var`s:
  // the page's one thread
  private var generation = 0
  private val jobs = mutable.HashMap.empty[String, Job]

  /** One tile's computation and the canvases waiting for it (the tint's and the shading's). */
  private final class Job:
    val tint  = mutable.ListBuffer.empty[(dom.HTMLCanvasElement, js.Function2[js.Any, dom.HTMLCanvasElement, Unit])]
    val shade = mutable.ListBuffer.empty[(dom.HTMLCanvasElement, js.Function2[js.Any, dom.HTMLCanvasElement, Unit])]

  /** Forget the jobs of the drawing that ends (their canvases are gone with it). */
  def reset(): Unit =
    generation += 1
    jobs.clear()

  private lazy val scratch: (dom.HTMLCanvasElement, dom.CanvasRenderingContext2D) =
    val c = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
    c.width = Relief.Tile
    c.height = Relief.Tile
    (c, c.getContext("2d", js.Dynamic.literal(willReadFrequently = true)).asInstanceOf[dom.CanvasRenderingContext2D])

  /** A colour of the style as (r, g, b, a): the canvas reads any CSS colour. */
  def rgba(c: String): Option[(Int, Int, Int, Double)] =
    val ctx = scratch._2
    ctx.fillStyle = "transparent"
    ctx.fillStyle = c
    Relief.parseColour(ctx.fillStyle.asInstanceOf[String])

  /** One elevation tile, fetched and decoded once (`None`: the server has none there). */
  private def dem(url: String, z: Int, x: Long, y: Long, cache: BoundedCache[String, Future[Option[Int16Array]]] = dems,
      side: Int = Relief.Tile): Future[Option[Int16Array]] =
    val n = 1L << z
    if z < 0 || y < 0 || y >= n then Future.successful(None)
    else
      val wx  = ((x % n) + n) % n
      val key = s"$url|$z/$wx/$y"
      cache.get(key).getOrElse {
        val f = fetchDem(url.replace("{z}", z.toString).replace("{x}", wx.toString).replace("{y}", y.toString), side)
        cache.put(key, f)
        f.failed.foreach(_ => cache.remove(key))
        f
      }

  private def fetchDem(tileUrl: String, side: Int): Future[Option[Int16Array]] =
    dom.fetch(tileUrl).toFuture.flatMap { r =>
      if r.status == 204 || !r.ok then Future.successful(None)
      else
        r.blob().toFuture.flatMap { blob =>
          js.Dynamic.global.createImageBitmap(blob).asInstanceOf[js.Promise[js.Dynamic]].toFuture.map { bmp =>
            val w = bmp.width.asInstanceOf[Int]
            val h = bmp.height.asInstanceOf[Int]
            if w != side || h != side then { bmp.close(); None }
            else
              val (_, ctx) = scratch
              ctx.clearRect(0, 0, w, h)
              ctx.drawImage(bmp.asInstanceOf[dom.HTMLImageElement], 0, 0)
              bmp.close()
              val px  = ctx.getImageData(0, 0, w, h)
              val out = new Int16Array(w * h)
              Relief.decode(px.data.asInstanceOf[Uint8ClampedArray], w * h, out)
              Some(out)
          }
        }
    }

  // scratch buffers, grown as needed and reused: every computation is synchronous on the page's thread
  private var bufWindow = new Float32Array(0)
  private var bufTmp    = new Float32Array(0)
  private var bufGrid   = new Float32Array(0)
  private var bufSeaWin = new Float32Array(0)
  private var bufSea    = new Float32Array(0)
  private def grow(b: Float32Array, n: Int): Float32Array = if b.length >= n then b else new Float32Array(n)

  /** A tile computed: its side `r` (in the elevation's pixels as drawn), the shading and tint images,
    * the elevation grid ((r + 2)²) the contours are traced on. */
  private final case class Computed(r: Int, shade: dom.ImageData, tint: Option[dom.ImageData], contours: Option[Contours.Traced],
      index: List[(Double, js.Array[Double])], isobaths: Option[Contours.Traced])

  /** The sea-floor tiles (256 pixels, zoom `bz`) under the part of the elevation tile `src` a map tile
    * covers (its window's few pixels around it take the nearest of them): their columns and rows, and the
    * scale from elevation pixels to theirs. Pure. */
  def seaTiles(src: Relief.Source, bathyMax: Int): (Int, Double, List[(Long, Long)]) =
    val bz = math.max(0, math.min(bathyMax, src.z + 1))
    val f  = math.pow(2, bz - src.z - 1).toDouble
    val gx0 = (src.x * Relief.Tile + src.ox) * f; val gy0 = (src.y * Relief.Tile + src.oy) * f
    val n = 1L << bz
    def range(a: Double) = (math.floor(a / 256).toLong to math.floor((a + src.span * f - 1e-9) / 256).toLong).toList
    (bz, f, for y <- range(gy0).filter(y => y >= 0 && y < n); x <- range(gx0) yield (x, y))

  /** A tile computed from the deepest data there is: a zoom deeper than the extract that has no tile there
    * (outside a focus region, the on-demand relief off or offline) falls back a zoom at a time — a coarser
    * relief rather than none. */
  private def compute(s: Setup, z: Int, x: Long, y: Long, dataMax: Int = -1): Future[Option[Computed]] =
    val top = if dataMax < 0 then s.dataMax else dataMax
    computeAt(s, z, x, y, top).flatMap {
      case None if math.min(z - 1, top) > s.extractMax => compute(s, z, x, y, math.min(z - 1, top) - 1)
      case other => Future.successful(other)
    }

  private def computeAt(s: Setup, z: Int, x: Long, y: Long, dataMax: Int): Future[Option[Computed]] =
    val src = Relief.source(z, x, y, dataMax)
    // the drawn side: the screen's pixels (at most 1024); at the data's own zoom and density the
    // window is the grid as it is (one pixel around), otherwise it is resampled (two pixels around)
    val r = math.min(1024, math.round(Relief.Tile * s.dpr).toInt)
    val pad = if src.span >= Relief.Tile && r == Relief.Tile then 1 else 2
    val (x0, y0, side) = src.window(pad)
    val need = Relief.neighboursNeeded(x0, y0, side)
    val centre = dem(s.url, src.z, src.x, src.y)
    val around = Future.sequence(need.map((dx, dy) => dem(s.url, src.z, src.x + dx, src.y + dy).map(d => (dx, dy) -> d)))
    // the sea floor under the window (its own tiles, coarser), when the server has it
    val (bz, bf, bts) = s.bathymetry.fold((0, 1.0, List.empty[(Long, Long)]))((_, m) => seaTiles(src, m))
    val floors = s.bathymetry.fold(Future.successful(List.empty[((Long, Long), Option[Int16Array])])) { (u, _) =>
      Future.sequence(bts.map((bx, by) => dem(u, bz, bx, by, seas, 256).map(d => (bx, by) -> d)))
    }
    centre.zip(around).zip(floors).map { case ((c, ns), fl) =>
      c.map { tile =>
        val nbs = new js.Array[Int16Array](9)
        ns.foreach { case ((dx, dy), d) => nbs((dy + 1) * 3 + dx + 1) = d.orNull }
        val nb = Relief.Neighbourhood(tile, nbs)
        val gw = r + 2
        bufWindow = grow(bufWindow, side * side)
        Relief.window(nb, x0, y0, side, bufWindow)
        // the sea: where the land's elevation is flat at sea level — its depth from the sea floor's tiles
        // (bilinear), at least a metre deep; a mask of it for the tint and the shading
        bufSeaWin = grow(bufSeaWin, side * side)
        val floor = fl.collect { case (k, Some(t)) => k -> t }.toMap
        val (fx0, fx1) = (bts.map(_._1).minOption.getOrElse(0L), bts.map(_._1).maxOption.getOrElse(0L))
        val (fy0, fy1) = (bts.map(_._2).minOption.getOrElse(0L), bts.map(_._2).maxOption.getOrElse(0L))
        val gx0 = (src.x * Relief.Tile + x0).toDouble; val gy0 = (src.y * Relief.Tile + y0).toDouble
        var i = 0
        while i < side * side do
          val e = bufWindow(i)
          if Relief.seaLevel(e) then
            bufSeaWin(i) = 1
            if floor.nonEmpty then
              val px = (gx0 + i % side + 0.5) * bf - 0.5; val py = (gy0 + i / side + 0.5) * bf - 0.5
              // the tile under it — for the few pixels around the window, the nearest one fetched
              val tx = math.max(fx0, math.min(fx1, math.floor((px + 0.5) / 256).toLong))
              val ty = math.max(fy0, math.min(fy1, math.floor((py + 0.5) / 256).toLong))
              floor.get((tx, ty)).foreach { t =>
                bufWindow(i) = math.min(-1.0, Relief.bilinear(t, 256, px - tx * 256, py - ty * 256)).toFloat
              }
          else bufSeaWin(i) = 0
          i += 1
        bufGrid = grow(bufGrid, gw * gw)
        bufSea = grow(bufSea, gw * gw)
        if pad == 1 && r == Relief.Tile then
          // the data's own zoom at its own density: the window IS the grid
          var i = 0
          while i < gw do
            var j = 0
            while j < gw do
              bufGrid(i * gw + j) = bufWindow(i * side + j)
              bufSea(i * gw + j) = bufSeaWin(i * side + j)
              j += 1
            i += 1
        else
          bufTmp = grow(bufTmp, side * gw)
          Relief.resample(bufWindow, side, src.ox - x0, src.oy - y0, src.span, r, bufGrid, bufTmp)
          Relief.resample(bufSeaWin, side, src.ox - x0, src.oy - y0, src.span, r, bufSea, bufTmp)
        val mpp = Hillshade.metresPerPixel(src.z, src.y.toDouble, Relief.Tile) * src.span / r
        val ctx = scratch._2
        val shade = ctx.createImageData(r, r)
        Relief.shade(bufGrid, r, mpp, s.relief.exaggeration.at(z), s.lights, s.shadow, s.shadowAlpha, s.highlight,
          s.highlightAlpha, shade.data.asInstanceOf[Uint8ClampedArray], bufSea, s.relief.seaShading)
        val tint = Option.when(s.tinted) {
          val t = ctx.createImageData(r, r)
          Relief.tint(bufGrid, r, s.lut, t.data.asInstanceOf[Uint8ClampedArray], bufSea, s.seaLut)
          t
        }
        val traced = s.contours.filter(c => z >= c.spec.minZoom).map { c =>
          Contours.trace(bufGrid, gw, gw, c.spec.interval.step(z), c.spec.indexEvery)
        }
        val index = traced.filter(_ => s.contours.exists(_.spec.labels)).fold(Nil)(Contours.polylines)
        // the isobaths: below sea level, every depth interval (none at 0)
        val isobaths = s.contours.filter(c => z >= c.spec.minZoom && c.spec.depthInterval.step(z) > 0).map { c =>
          Contours.trace(bufGrid, gw, gw, c.spec.depthInterval.step(z), 0, min = -12000, max = 0)
        }
        Computed(r, shade, tint, traced, index, isobaths)
      }
    }

  // an image the size of a computed tile, to scale it onto a canvas of the screen's density
  private var stage: Option[dom.HTMLCanvasElement] = None
  private def staged(img: dom.ImageData): dom.HTMLCanvasElement =
    val c = stage.filter(_.width == img.width).getOrElse {
      val n = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
      n.width = img.width; n.height = img.height
      stage = Some(n)
      n
    }
    c.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D].putImageData(img, 0, 0)
    c

  private def paint(canvas: dom.HTMLCanvasElement, img: dom.ImageData): Unit =
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    if canvas.width == img.width then ctx.putImageData(img, 0, 0)
    else
      ctx.imageSmoothingEnabled = true
      ctx.asInstanceOf[js.Dynamic].imageSmoothingQuality = "high"
      ctx.drawImage(staged(img), 0, 0, canvas.width, canvas.height)

  /** The contours of a computed tile onto its shading canvas: lines, index lines, their elevations. */
  private def drawContours(canvas: dom.HTMLCanvasElement, c: Computed, d: ContourDraw, z: Int, dpr: Double): Unit =
    c.contours.foreach { t =>
      val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
      val k = canvas.width.toDouble / c.r
      // grid sample (j, i) is the centre of drawn pixel (j − 1, i − 1)
      def stroke(segs: js.Array[Double], colour: String, width: Double): Unit =
        if segs.length > 0 then
          ctx.beginPath()
          var q = 0
          while q < segs.length do
            ctx.moveTo((segs(q) - 0.5) * k, (segs(q + 1) - 0.5) * k)
            ctx.lineTo((segs(q + 2) - 0.5) * k, (segs(q + 3) - 0.5) * k)
            q += 4
          ctx.strokeStyle = colour
          ctx.lineWidth = width * dpr
          ctx.lineCap = "round"
          ctx.stroke()
      c.isobaths.foreach(b => stroke(b.lines, d.depthColor, d.spec.width))
      stroke(t.lines, d.color, d.spec.width)
      stroke(t.index, d.indexColor, d.spec.indexWidth)
      if d.spec.labels then
        val size = d.spec.font.size.at(z) * dpr
        ctx.font = s"${d.spec.font.style} ${d.spec.font.weight} ${size}px ${d.spec.font.family}"
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        ctx.lineJoin = "round"
        val placed = mutable.ListBuffer.empty[(Double, Double, Double)]
        c.index.foreach { (level, p) =>
          val pts = js.Array[Double]()
          var q = 0
          while q < p.length do { pts.push((p(q) - 0.5) * k, (p(q + 1) - 0.5) * k); q += 2 }
          val text = math.round(level).toString
          val w = ctx.measureText(text).width
          Contours.labelAt(pts, w, 24 * dpr, canvas.width, 18 * dpr).foreach { (lx, ly, a) =>
            if !placed.exists((px, py, pw) => math.hypot(px - lx, py - ly) < (pw + w) / 2 + 16 * dpr) then
              placed += ((lx, ly, w))
              ctx.save()
              ctx.translate(lx, ly)
              ctx.rotate(a)
              ctx.lineWidth = 3 * dpr
              ctx.strokeStyle = d.halo
              ctx.strokeText(text, 0, 0)
              ctx.fillStyle = d.labelColor
              ctx.fillText(text, 0, 0)
              ctx.restore()
          }
        }
    }

  private def run(s: Setup, key: String, z: Int, x: Long, y: Long, mine: Int): Unit =
    compute(s, z, x, y).onComplete { r =>
      val job = jobs.remove(key)
      if mine == generation then
        val err: js.Any = r.failed.toOption.map(e => js.Error(e.getMessage): js.Any).getOrElse(null)
        job.foreach { j =>
          r.toOption.flatten.foreach { c =>
            j.tint.foreach((canvas, _) => c.tint.foreach(paint(canvas, _)))
            j.shade.foreach { (canvas, _) =>
              paint(canvas, c.shade)
              s.contours.foreach(drawContours(canvas, c, _, z, s.dpr))
            }
          }
          (j.tint ++ j.shade).foreach((canvas, done) => done(err, canvas))
        }
    }

  /** The grid layers of one drawing: the tint (when the style has one) and the shading, with the zIndex
    * each goes at (the vector layer sits between them). */
  def layers(s: Setup, bounds: Option[js.Array[js.Array[Double]]], tintZ: Int, shadeZ: Int): List[Layer] =
    val mine = generation
    val px = math.round(Relief.Tile * s.dpr).toInt
    def make(kind: String, z: Int, cls: String): Layer =
      val create: js.Function2[js.Dynamic, js.Function2[js.Any, dom.HTMLCanvasElement, Unit], dom.HTMLCanvasElement] =
        (coords, done) =>
          val canvas = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
          canvas.width = px
          canvas.height = px
          val tz = coords.z.asInstanceOf[Int]
          val tx = coords.x.asInstanceOf[Double].toLong
          val ty = coords.y.asInstanceOf[Double].toLong
          val key = s"$mine/$tz/$tx/$ty"
          val fresh = !jobs.contains(key)
          val job = jobs.getOrElseUpdate(key, Job())
          (if kind == "tint" then job.tint else job.shade) += ((canvas, done))
          if fresh then run(s, key, tz, tx, ty, mine)
          canvas
      val cls0 = GridLayer.extend(js.Dynamic.literal(createTile = create))
      val opts = js.Dynamic.literal(tileSize = Relief.Tile, minNativeZoom = 1, maxNativeZoom = 24, zIndex = z,
        className = cls, updateWhenZooming = false)
      bounds.foreach(b => opts.bounds = b)
      js.Dynamic.newInstance(cls0)(opts).asInstanceOf[Layer]
    Option.when(s.tinted)(make("tint", tintZ, "terrain-tint")).toList :+ make("shade", shadeZ, "terrain-relief")

  /** Resolve a style's relief for drawing. `colour` resolves a style colour (a token, a name …). */
  def setup(url: String, dataMax: Int, r: ReliefStyle, colour: String => String, dpr: Double,
      bathymetry: Option[(String, Int)]): Setup =
    def rgb(c: String) = rgba(colour(c)).map(v => (v._1, v._2, v._3)).getOrElse((0, 0, 0))
    val lut = Option.when(r.heights && r.tint.nonEmpty)(Relief.lut(Relief.stops(r.tint, c => rgba(colour(c))), r.tintOpacity))
    val seaLut = Option.when(r.seaFloor && r.seaTint.nonEmpty)(Relief.lut(Relief.stops(r.seaTint, c => rgba(colour(c))), r.tintOpacity, step = 10))
    val contours = Option.when(r.contours.enabled)(ContourDraw(r.contours, colour(r.contours.color),
      colour(r.contours.indexColor), colour(r.contours.labelColor), colour(r.contours.halo), colour(r.contours.depthColor)))
    Setup(url, dataMax, r, Relief.lights(r.azimuth, r.altitude, r.multidirectional), rgb(r.shadow),
      math.round(r.shadowOpacity * 255).toInt, rgb(r.highlight), math.round(r.highlightOpacity * 255).toInt, lut, contours,
      math.max(1.0, math.min(2.0, dpr)), seaLut, bathymetry.filter(_ => seaLut.isDefined))
