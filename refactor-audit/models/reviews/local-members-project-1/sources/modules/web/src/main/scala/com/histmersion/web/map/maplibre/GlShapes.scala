package com.histmersion.web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** One [[ShapeSet]] as a GeoJSON source and its layers. Its features carry numeric ids (feature state needs them) and
  * their own id as `hid`; colour and visibility live in feature state, so a restyle never sends the geometry again.
  */
final private[maplibre] class GlShapes(gl: GlMap, val id: GlShapes.Id, val look: ShapeStyle) extends ShapeSet:
  // `var`s: the next numeric id, and what is drawn: our id → (numeric id, geometry, colour, alpha, dashed)
  private var next = 1
  final private class Drawn(
      val n: Drawn.N,
      val geometry: js.Any,
      var colour: Drawn.Colour,
      var alpha: Drawn.Alpha,
      val dashed: Drawn.Dashed
  ):
    lazy val bounds: Option[GeoBounds] = GeoBounds.ofGeometry(geometry.asInstanceOf[js.Dynamic])

  object Drawn:
    type N = N.T
    object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Colour = Colour.T
    object Colour extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Alpha = Alpha.T
    object Alpha extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dashed = Dashed.T
    object Dashed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  private val drawn = mutable.LinkedHashMap.empty[String, Drawn]
  private val numeric = mutable.HashMap.empty[String, Int]
  // the shapes whose colour or visibility was set in place (feature state): only these need it again
  // after the geometry is sent anew — the others carry theirs in their properties
  private val stated = mutable.HashSet.empty[String]

  private def colourExpr: js.Any = look.colour match
    case Some(c) => c
    case None    => js.Array("coalesce", js.Array("feature-state", "c"), js.Array("get", "c"))
  private val alphaExpr: js.Array[js.Any] = js.Array("coalesce", js.Array("feature-state", "a"), js.Array("get", "a"))
  private def times(k: com.histmersion.web.map.maplibre.GlShapes.TimesK): js.Any = js.Array("*", k, alphaExpr)
  private val notPoint = js.Array("!=", js.Array("geometry-type"), "Point")

  def layerIds: List[com.histmersion.web.map.maplibre.GlShapes.LayerIdsResult] =
    List(
      Option.when(look.fillOpacity > 0)(s"$id-fill"),
      Some(s"$id-line"),
      Option.when(look.dash.nonEmpty)(s"$id-dashed"),
      Option.when(look.circleRadius > 0)(s"$id-points")
    ).flatten

  private def layers: List[js.Object] =
    val fill = Option.when(look.fillOpacity > 0)(
      js.Dynamic.literal(
        id = s"$id-fill",
        `type` = "fill",
        source = id,
        filter = notPoint,
        paint = js.Dynamic.literal(`fill-color` = colourExpr, `fill-opacity` = times(look.fillOpacity))
      )
    )
    val solid = js.Dynamic.literal(
      id = s"$id-line",
      `type` = "line",
      source = id,
      filter = if look.dash.isEmpty then notPoint else js.Array("all", notPoint, js.Array("!", js.Array("get", "d"))),
      layout = js.Dynamic.literal(`line-join` = "round"),
      paint = js.Dynamic
        .literal(`line-color` = colourExpr, `line-width` = look.lineWidth, `line-opacity` = times(look.lineOpacity))
    )
    val dashed = Option.when(look.dash.nonEmpty)(
      js.Dynamic.literal(
        id = s"$id-dashed",
        `type` = "line",
        source = id,
        filter = js.Array("all", notPoint, js.Array("get", "d")),
        paint = js.Dynamic.literal(
          `line-color` = colourExpr,
          `line-width` = look.lineWidth,
          `line-opacity` = times(look.dashedLineOpacity.getOrElse(look.lineOpacity)),
          `line-dasharray` = look.dash.toJSArray
        )
      )
    )
    val points = Option.when(look.circleRadius > 0)(
      js.Dynamic.literal(
        id = s"$id-points",
        `type` = "circle",
        source = id,
        filter = js.Array("==", js.Array("geometry-type"), "Point"),
        paint = js.Dynamic.literal(
          `circle-radius` = look.circleRadius,
          `circle-color` = colourExpr,
          `circle-opacity` = times(0.9),
          `circle-stroke-width` = 2,
          `circle-stroke-color` = colourExpr
        )
      )
    )
    (fill.toList :+ solid) ++ dashed.toList ++ points.toList map (_.asInstanceOf[js.Object])

  private def collection: js.Object =
    js.Dynamic
      .literal(
        `type` = "FeatureCollection",
        features = drawn.toSeq.map { (k, d) =>
          js.Dynamic.literal(
            `type` = "Feature",
            id = d.n,
            geometry = d.geometry,
            properties = js.Dynamic.literal(hid = k, c = d.colour, a = d.alpha, d = d.dashed)
          )
        }.toJSArray
      )
      .asInstanceOf[js.Object]

  /** The source and layers, when the style lacks them (made, or a new base map loaded). */
  def restore(): Unit =
    if gl.isStyleLoaded() || gl.getStyle() != null then
      scala.util.Try {
        if gl.getSource(id).isEmpty then
          gl.addSource(id, js.Dynamic.literal(`type` = "geojson", data = collection).asInstanceOf[js.Object])
        layers.foreach(l =>
          if gl.getLayer(l.asInstanceOf[js.Dynamic].id.asInstanceOf[String]).isEmpty then gl.addLayer(l)
        )
        stated.foreach(k => drawn.get(k).foreach(state))
      }

  private def state(d: Drawn): Unit =
    scala.util.Try(
      gl.setFeatureState(
        js.Dynamic.literal(source = id, id = d.n).asInstanceOf[js.Object],
        js.Dynamic.literal(c = d.colour, a = d.alpha).asInstanceOf[js.Object]
      )
    )

  restore()

  def ids: Seq[com.histmersion.web.map.maplibre.GlShapes.IdsResult] = drawn.keys.toSeq

  // `var`: how many times the outlines were sent (diagnostics)
  private var sent = 0
  override def uploads: Int = sent
  override def styleOf(k: String): Option[(String, Double)] = drawn.get(k).map(d => (d.colour, d.alpha))

  /** The shapes under map point `at` (screen point `p`), topmost (last drawn) first: an area that holds it, a point
    * within its circle.
    */
  def under(at: LatLon, p: ScreenPt, s: MapSurface): Seq[com.histmersion.web.map.maplibre.GlShapes.UnderResult] =
    // a shape faded out (playback's states before and after their years) answers nothing
    drawn.toSeq.reverseIterator
      .filter((_, d) => d.alpha > 0.05)
      .filter { (_, d) =>
        ShapeHits.point(d.geometry.asInstanceOf[js.Dynamic]) match
          case Some(q) =>
            val sp = s.project(q)
            math.hypot(sp.x - p.x, sp.y - p.y) <= look.circleRadius + 3
          case None =>
            d.bounds.exists(b => at.lat >= b.south && at.lat <= b.north) && {
              // the copy of the world the shape's bounds are in
              val lon = d.bounds.fold(at.lon)(b =>
                if at.lon < b.west then at.lon + 360 else if at.lon > b.east then at.lon - 360 else at.lon
              )
              d.bounds.exists(b => lon >= b.west && lon <= b.east) && ShapeHits.contains(
                d.geometry.asInstanceOf[js.Dynamic],
                at.lat,
                lon
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
      gl.getSource(id).toOption match
        case Some(src) =>
          sent += 1
          src.setData(collection)
          stated.filterInPlace(drawn.contains)
          stated.foreach(k => state(drawn(k)))
        case None => restore()

  def restyle(k: com.histmersion.web.map.maplibre.GlShapes.RestyleK, colour: com.histmersion.web.map.maplibre.GlShapes.RestyleColour, alpha: com.histmersion.web.map.maplibre.GlShapes.RestyleAlpha): Unit =
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
  private def sameGeometry(a: js.Any, b: js.Any): com.histmersion.web.map.maplibre.GlShapes.SameGeometryResult =
    (a eq b) || {
      def sig(g: js.Any): com.histmersion.web.map.maplibre.GlShapes.SameGeometrySigResult =
        val d = g.asInstanceOf[js.Dynamic]
        if js.isUndefined(d) || d == null then ""
        else
          val sb = StringBuilder(d.selectDynamic("type").toString)
          def walk(c: js.Dynamic, depth: com.histmersion.web.map.maplibre.GlShapes.SameGeometrySigWalkDepth): Unit =
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

object GlShapes:
  type SameGeometrySigResult = SameGeometrySigResult.T
  object SameGeometrySigResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameGeometrySigWalkDepth = SameGeometrySigWalkDepth.T
  object SameGeometrySigWalkDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TimesK = TimesK.T
  object TimesK extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerIdsResult = LayerIdsResult.T
  object LayerIdsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsResult = IdsResult.T
  object IdsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnderResult = UnderResult.T
  object UnderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestyleK = RestyleK.T
  object RestyleK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestyleColour = RestyleColour.T
  object RestyleColour extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestyleAlpha = RestyleAlpha.T
  object RestyleAlpha extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameGeometryResult = SameGeometryResult.T
  object SameGeometryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
