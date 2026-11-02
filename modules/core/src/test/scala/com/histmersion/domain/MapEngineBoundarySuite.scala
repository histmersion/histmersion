package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The web map's abstraction holds (docs/user-guide/contributing/architecture.md, "The map"): only the adapter package
  * `web.map.maplibre` names the map library — its facade types, its global, its stylesheet classes, its style-document
  * vocabulary. Everything else speaks `web.map.MapSurface`, so a change of map library is a new adapter, not a rewrite
  * of the app. `Engines.scala` is the one place that picks the adapter.
  */
class MapEngineBoundarySuite extends munit.FunSuite:

  private val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
  private val web = root.resolve("modules/web/src/main/scala/com/histmersion/web")
  private val adapter = web.resolve("map/maplibre")

  private def sources(d: Path): List[Path] =
    if !Files.isDirectory(d) then Nil
    else Files.walk(d).iterator.asScala.filter(_.toString.endsWith(".scala")).toList

  /** What only the adapter may say: its package, the library's global and names, Leaflet's. */
  private val forbidden = List(
    "web.map.maplibre",
    "maplibregl",
    "maplibre-gl",
    "GlMap",
    "GlPopup",
    "GlEvent",
    "RenderedFeature",
    "queryRenderedFeatures",
    "setFeatureState",
    "addLayer(",
    "addSource(",
    "\"raster-dem\"",
    "leaflet",
    "Leaflet",
    "protomaps",
    "markerClusterGroup",
    "mlcontour"
  )

  test("no map library type or name outside the adapter package"):
    assert(Files.isDirectory(adapter), s"the adapter is at $adapter")
    val outside = sources(web).filterNot(_.startsWith(adapter)).filterNot(_.getFileName.toString == "Engines.scala")
    assert(outside.nonEmpty)
    val hits = outside.flatMap { p =>
      Files.readAllLines(p).asScala.zipWithIndex.flatMap { (line, i) =>
        forbidden.find(line.contains).map(f => s"${web.relativize(p)}:${i + 1}: `$f` — $line")
      }
    }
    assert(hits.isEmpty, hits.mkString("\n"))

  test("the one place that picks the engine names only the adapter's engine"):
    val engines = web.resolve("map/Engines.scala")
    val text = Files.readString(engines)
    assert(text.contains("maplibre.MapLibreEngine"), text)
    assert(!forbidden.filterNot(_ == "web.map.maplibre").filterNot(f => f == "maplibregl").exists(text.contains), text)

  test("the adapter is the only code importing the library's facade"):
    val facade = sources(adapter).filter(p => Files.readString(p).contains("@JSGlobal(\"maplibregl"))
    assertEquals(
      facade.map(_.getFileName.toString).sorted,
      List("GlMap.scala", "GlPopup.scala", "MapLibre.scala", "NavigationControl.scala")
    )
