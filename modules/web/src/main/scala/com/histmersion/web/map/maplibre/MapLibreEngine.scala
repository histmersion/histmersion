package com.histmersion.web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** The map engine: MapLibre GL JS (docs/research-map-styles.md, option C). The ONLY code that knows the library; the
  * app speaks [[MapSurface]].
  */
object MapLibreEngine extends MapEngine:

  /** MapLibre's zoom is the raster grid's minus one for the same scale (its tiles are 512 pixels). */

  val ZoomShift = 1.0

  /** Read once per page: a probe makes a WebGL context of its own (the page's map makes another). */
  def probe(): Capabilities = probed

  private lazy val probed: Capabilities =
    scala.util
      .Try {
        val c = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
        val gl2 = Option(c.getContext("webgl2"))
        // a canvas gives one kind of context: WebGL 1 is asked of another canvas
        val gl = gl2
          .orElse(Option(dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement].getContext("webgl")))
          .map(_.asInstanceOf[js.Dynamic])
        gl match
          case None    => Capabilities(webgl = com.histmersion.web.map.Capabilities.Webgl(false), software = com.histmersion.web.map.Capabilities.Software(true), renderer = com.histmersion.web.map.Capabilities.Renderer(""), webgl2 = com.histmersion.web.map.Capabilities.Webgl2(false))
          case Some(g) =>
            // RENDERER names the GPU in Firefox (which warns that the debug extension is going away); Chromium
            // answers a generic name there and the GPU only through the extension
            val plain = Capabilities.Renderer(g.getParameter(g.RENDERER).toString)
            val renderer =
              if !Capabilities.genericRenderer(plain) then plain
              else
                val ext = g.getExtension("WEBGL_debug_renderer_info")
                if ext != null && !js.isUndefined(ext) then Capabilities.Renderer(g.getParameter(ext.UNMASKED_RENDERER_WEBGL).toString)
                else plain
            // the probe's context is left to the garbage collector: losing it on purpose makes Firefox warn
            Capabilities(
              webgl = com.histmersion.web.map.Capabilities.Webgl(true),
              software = Capabilities.Software(Capabilities.isSoftware(renderer)),
              renderer = renderer,
              webgl2 = Capabilities.Webgl2(gl2.isDefined)
            )
      }
      .getOrElse(Capabilities(webgl = com.histmersion.web.map.Capabilities.Webgl(false), software = com.histmersion.web.map.Capabilities.Software(true), renderer = com.histmersion.web.map.Capabilities.Renderer(""), webgl2 = com.histmersion.web.map.Capabilities.Webgl2(false)))

  /** MapLibre GL JS 6 draws every map — vector and raster — with WebGL 2 only. */
  override def unsupported(c: Capabilities): Option[MapEngine.Problem] = Capabilities.withoutWebgl2(c)

  def create(el: dom.HTMLElement, maxZoom: com.histmersion.web.map.MapEngine.MaxZoom, pixelRatio: Option[com.histmersion.web.map.MapEngine.PixelRatio]): MapSurface =
    MapLibreSurface(el, maxZoom, pixelRatio, probe())
