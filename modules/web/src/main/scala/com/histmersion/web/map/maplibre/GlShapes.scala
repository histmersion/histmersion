package com.histmersion
package web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.web.map.MapSurface
import com.histmersion.web.map.ShapeFeature

/** One [[ShapeSet]] as a GeoJSON source and its layers. Its features carry numeric ids (feature state needs them) and
  * their own id as `hid`; colour and visibility live in feature state, so a restyle never sends the geometry again.
  */
final private[maplibre] class GlShapes(gl: GlMap, val id: NoMap.Shapes.Id, val look: ShapeStyle) extends ShapeSet:
  // `var`s: the next numeric id, and what is drawn: our id → (numeric id, geometry, colour, alpha, dashed)
  private val sourceId: String = id.value
  private var next = 1
  final private class Drawn(
      val n: Int,
      val geometry: js.Any,
      var colour: ShapeFeature.Colour,
      var alpha: ShapeFeature.Alpha,
      val dashed: ShapeFeature.Dashed
  ):
    lazy val bounds: Option[GeoBounds] = GeoBounds.ofGeometry(geometry.asInstanceOf[js.Dynamic])

  private val drawn = mutable.LinkedHashMap.empty[ShapeFeature.Id, Drawn]
  private val numeric = mutable.HashMap.empty[ShapeFeature.Id, Int]
  // the shapes whose colour or visibility was set in place (feature state): only these need it again
  // after the geometry is sent anew — the others carry theirs in their properties
  private val stated = mutable.HashSet.empty[ShapeFeature.Id]

  private def colourExpr: js.Any = look.colour match
    case Some(c) => c.value
    case None    => js.Array("coalesce", js.Array("feature-state", "c"), js.Array("get", "c"))
  private val alphaExpr: js.Array[js.Any] = js.Array("coalesce", js.Array("feature-state", "a"), js.Array("get", "a"))
  private def times(k: Double): js.Any = js.Array("*", k, alphaExpr)
  private val notPoint = js.Array("!=", js.Array("geometry-type"), "Point")

  def layerIds: List[MapSurface.LayerId] =
    List(
      Option.when(look.fillOpacity.value > 0)(MapSurface.LayerId(s"$sourceId-fill")),
      Some(MapSurface.LayerId(s"$sourceId-line")),
      Option.when(look.dash.nonEmpty)(MapSurface.LayerId(s"$sourceId-dashed")),
      Option.when(look.circleRadius.value > 0)(MapSurface.LayerId(s"$sourceId-points"))
    ).flatten

  private def layers: List[js.Object] =
    val fill = Option.when(look.fillOpacity.value > 0)(
      js.Dynamic.literal(
        id = s"$sourceId-fill",
        `type` = "fill",
        source = sourceId,
        filter = notPoint,
        paint = js.Dynamic.literal(`fill-color` = colourExpr, `fill-opacity` = times(look.fillOpacity.value))
      )
    )
    val solid = js.Dynamic.literal(
      id = s"$sourceId-line",
      `type` = "line",
      source = sourceId,
      filter = if look.dash.isEmpty then notPoint else js.Array("all", notPoint, js.Array("!", js.Array("get", "d"))),
      layout = js.Dynamic.literal(`line-join` = "round"),
      paint = js.Dynamic
        .literal(`line-color` = colourExpr, `line-width` = look.lineWidth.value, `line-opacity` = times(look.lineOpacity.value))
    )
    val dashed = Option.when(look.dash.nonEmpty)(
      js.Dynamic.literal(
        id = s"$sourceId-dashed",
        `type` = "line",
        source = sourceId,
        filter = js.Array("all", notPoint, js.Array("get", "d")),
        paint = js.Dynamic.literal(
          `line-color` = colourExpr,
          `line-width` = look.lineWidth.value,
          `line-opacity` = times(look.dashedLineOpacity.map(_.value).getOrElse(look.lineOpacity.value)),
          `line-dasharray` = look.dash.map(_.value).toJSArray
        )
      )
    )
    val points = Option.when(look.circleRadius.value > 0)(
      js.Dynamic.literal(
        id = s"$sourceId-points",
        `type` = "circle",
        source = sourceId,
        filter = js.Array("==", js.Array("geometry-type"), "Point"),
        paint = js.Dynamic.literal(
          `circle-radius` = look.circleRadius.value,
          `circle-color` = colourExpr,
          `circle-opacity` = times(0.9),
          `circle-stroke-width` = 2,
          `circle-stroke-color` = colourExpr
        )
      )
    )
    ((fill.toList :+ solid) ++ dashed.toList ++ points.toList).map(_.asInstanceOf[js.Object])

  private def collection: js.Object =
    js.Dynamic
      .literal(
        `type` = "FeatureCollection",
        features = drawn.toSeq.map { (k, d) =>
          js.Dynamic.literal(
            `type` = "Feature",
            id = d.n,
            geometry = d.geometry,
            properties = js.Dynamic.literal(hid = k.value, c = d.colour.value, a = d.alpha.value, d = d.dashed.value)
          )
        }.toJSArray
      )
      .asInstanceOf[js.Object]

  /** The source and layers, when the style lacks them (made, or a new base map loaded). */
  def restore(): Unit =
    if gl.isStyleLoaded() || gl.getStyle() != null then
      scala.util.Try {
        if gl.getSource(sourceId).isEmpty then
          gl.addSource(sourceId, js.Dynamic.literal(`type` = "geojson", data = collection).asInstanceOf[js.Object])
        layers.foreach(l =>
          if gl.getLayer(l.asInstanceOf[js.Dynamic].id.asInstanceOf[String]).isEmpty then gl.addLayer(l)
        )
        stated.foreach(k => drawn.get(k).foreach(state))
      }

  private def state(d: Drawn): Unit =
    scala.util.Try(
      gl.setFeatureState(
        js.Dynamic.literal(source = sourceId, id = d.n).asInstanceOf[js.Object],
        js.Dynamic.literal(c = d.colour.value, a = d.alpha.value).asInstanceOf[js.Object]
      )
    )

  restore()

  def ids: Seq[ShapeFeature.Id] = drawn.keys.toSeq

  // `var`: how many times the outlines were sent (diagnostics)
  private var sent = 0
  override def uploads: ShapeSet.UploadCount = ShapeSet.UploadCount(sent)
  override def styleOf(k: ShapeFeature.Id): Option[(ShapeStyle.Colour, ShapeFeature.Alpha)] = drawn.get(k).map(d => (ShapeStyle.Colour(d.colour.value), d.alpha))

  /** The shapes under map point `at` (screen point `p`), topmost (last drawn) first: an area that holds it, a point
    * within its circle.
    */
  def under(at: LatLon, p: ScreenPt, s: MapSurface): Seq[ShapeFeature.Id] =
    // a shape faded out (playback's states before and after their years) answers nothing
    drawn.toSeq.reverseIterator
      .filter((_, d) => d.alpha.value > 0.05)
      .filter { (_, d) =>
        ShapeHits.point(d.geometry.asInstanceOf[js.Dynamic]) match
          case Some(q) =>
            val sp = s.project(q)
            math.hypot(sp.x.value - p.x.value, sp.y.value - p.y.value) <= look.circleRadius.value + 3
          case None =>
            d.bounds.exists(b => at.lat.value >= b.south.value && at.lat.value <= b.north.value) && {
              // the copy of the world the shape's bounds are in
              val lon = d.bounds.fold(at.lon.value)(b =>
                if at.lon.value < b.west.value then at.lon.value + 360
                else if at.lon.value > b.east.value then at.lon.value - 360
                else at.lon.value
              )
              d.bounds.exists(b => lon >= b.west.value && lon <= b.east.value) && ShapeHits.contains(
                d.geometry.asInstanceOf[js.Dynamic],
                at.lat,
                LatLon.Lon(lon)
              )
            }
      }
      .map(_._1)
      .toSeq

  def set(shapes: Seq[ShapeFeature]): Unit =
    val want = shapes.map(s => s.id -> s).toMap
    val sameSet = want.size == drawn.size && shapes.forall(s =>
      drawn.get(s.id).exists(d => sameGeometry(d.geometry, s.geometry) && d.dashed == s.dashed)
    )
    if sameSet then shapes.foreach(s => restyle(s.id, s.colour, s.alpha))
    else
      drawn.clear()
      shapes.foreach { s =>
        val n = numeric.getOrElseUpdate(s.id, { next += 1; next })
        drawn(s.id) = Drawn(n, s.geometry, s.colour, s.alpha, s.dashed)
      }
      gl.getSource(sourceId).toOption match
        case Some(src) =>
          sent += 1
          src.setData(collection)
          stated.filterInPlace(drawn.contains)
          stated.foreach(k => state(drawn(k)))
        case None => restore()

  def restyle(k: ShapeFeature.Id, colour: ShapeFeature.Colour, alpha: ShapeFeature.Alpha): Unit =
    drawn.get(k).foreach { d =>
      if d.colour != colour || d.alpha != alpha then
        d.colour = colour
        d.alpha = alpha
        stated += k
        state(d)
    }

  /** The same outline: the same object, or (a new answer for the same shape) the same rings' lengths and first vertex —
    * cheap, and two years of one state with the same outline share it.
    */
  private def sameGeometry(a: js.Any, b: js.Any): Boolean =
    (a eq b) || {
      def sig(g: js.Any): String =
        val d = g.asInstanceOf[js.Dynamic]
        if js.isUndefined(d) || d == null then ""
        else
          val sb = StringBuilder(d.selectDynamic("type").toString)
          def walk(c: js.Dynamic, depth: Int): Unit =
            if js.Array.isArray(c) && depth < 4 then
              val arr = c.asInstanceOf[js.Array[js.Dynamic]]
              sb.append('[').append(arr.length)
              if arr.nonEmpty then
                if js.typeOf(arr(0)) == "number" then sb.append(':').append(arr.mkString(","))
                else { walk(arr(0), depth + 1); if arr.length > 1 then walk(arr(arr.length - 1), depth + 1) }
              sb.append(']')
          walk(d.coordinates, 0)
          sb.toString
      sig(a) == sig(b)
    }
