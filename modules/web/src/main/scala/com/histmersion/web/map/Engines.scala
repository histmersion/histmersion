package com.histmersion.web.map

/** The engine the app runs on: the one place outside the adapter that names it. */
object Engines:
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelRatio = PixelRatio.T
  object PixelRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def default: MapEngine = maplibre.MapLibreEngine

  /** The page's map in `el` — or, when the browser cannot run the engine or the engine fails to start, a
    * [[NoMapSurface]] that says so in the map's place and draws nothing. `pixelRatio` from what the browser can draw.
    */
  def surface(
      el: org.scalajs.dom.HTMLElement,
      maxZoom: com.histmersion.web.map.Engines.MaxZoom,
      pixelRatio: Capabilities => Option[com.histmersion.web.map.Engines.PixelRatio],
      engine: MapEngine = default
  ): MapSurface =
    val caps = engine.probe()
    engine.unsupported(caps) match
      case Some(why) => NoMapSurface(el, why, caps, maxZoom)
      case None      =>
        try engine.create(el, maxZoom, pixelRatio(caps))
        catch
          case e: Throwable =>
            val msg = Option(e.getMessage).map(_.trim).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
            NoMapSurface(el, s"the map engine did not start ($msg)", caps, maxZoom)
