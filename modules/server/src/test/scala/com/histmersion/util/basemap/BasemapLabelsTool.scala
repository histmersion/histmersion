package com.histmersion
package util.basemap

import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}
import com.histmersion.util.storage.Store

/** Builds a physical-names archive from Natural Earth GeoJSON files already on disk, with the fetch's own code — how
  * the browser fixture (modules/web/test/fixtures/basemap/labels.pmtiles) was made and how the sizes in
  * docs/research-terrain-basemap.md were measured:
  * `sbt "server/Test/runMain com.histmersion.util.basemap.basemapLabels <dir> <bbox> <maxZoom> <out>"`.
  */
@main def basemapLabels(args: String*): Unit =
  val Seq(dir, bbox, maxZoom, out) = args: @unchecked
  val cfg = AppConfig.load().basemap.labels
  val b = BBox.parse(BBox.Text(bbox)).getOrElse(sys.error(s"bad bbox $bbox"))
  val all = cfg.files.flatMap { f =>
    val text = Files.readString(Path.of(dir, s"$f.geojson"))
    io.circe.parser
      .parse(text)
      .left
      .map(e => BasemapLabels.Problem(e.getMessage))
      .flatMap(BasemapLabels.parse(BasemapLabels.FileName(f.value), _))
      .fold(e => sys.error(e.value), identity)
  }
  val kept = all.filter(BasemapLabels.within(_, b))
  val to = Path.of(out).toAbsolutePath
  val (tiles, bytes) = BasemapLabels.write(
    BasemapFixtures.store(to.getParent),
    Store.Key.applyUnsafe(to.getFileName.toString),
    kept,
    b,
    PmTiles.Zoom(maxZoom.toInt),
    BasemapService.labelsMetadata(
      cfg,
      b,
      BasemapService.NameCount(kept.size),
      BasemapFetch.FetchedAt(java.time.Instant.now().toString)
    )
  )
  val byKind = kept.groupBy(_.kind).view.mapValues(_.size).toList.sortBy(-_._2).map((k, n) => s"$k $n").mkString(", ")
  scribe.info(s"$out: ${kept.size} of ${all.size} names ($byKind) in $tiles tiles, $bytes bytes")
