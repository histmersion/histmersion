package com.histmersion.server.basemap

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}

/** Builds a physical-names archive from Natural Earth GeoJSON files already on disk, with the fetch's
  * own code — how the browser fixture (modules/web/test/fixtures/basemap/labels.pmtiles) was made and
  * how the sizes in docs/research-terrain-basemap.md were measured:
  * `sbt "server/Test/runMain com.histmersion.server.basemap.BasemapLabelsTool <dir> <bbox> <maxZoom> <out>"`. */
object BasemapLabelsTool:
  def main(args: Array[String]): Unit =
    val Array(dir, bbox, maxZoom, out) = args: @unchecked
    val cfg   = AppConfig.load().basemap.labels
    val b     = BBox.parse(bbox).getOrElse(sys.error(s"bad bbox $bbox"))
    val all   = cfg.files.flatMap { f =>
      val text = Files.readString(Path.of(dir, s"$f.geojson"))
      io.circe.parser.parse(text).left.map(_.getMessage).flatMap(BasemapLabels.parse(f, _)).fold(e => sys.error(e), identity)
    }
    val kept  = all.filter(BasemapLabels.within(_, b))
    val (tiles, bytes) = BasemapLabels.write(Path.of(out), kept, b, maxZoom.toInt,
      BasemapService.labelsMetadata(cfg, b, kept.size, java.time.Instant.now().toString))
    val byKind = kept.groupBy(_.kind).view.mapValues(_.size).toList.sortBy(-_._2).map((k, n) => s"$k $n").mkString(", ")
    scribe.info(s"$out: ${kept.size} of ${all.size} names ($byKind) in $tiles tiles, $bytes bytes")
