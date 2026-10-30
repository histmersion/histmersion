package com.histmersion.server.importers

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.LandRepo
import javax.sql.DataSource

/** Natural Earth land for clipping polities to the coastline (V025) — `histmersion places fetch land`.
  *
  * Source: https://www.naturalearthdata.com (public domain), via the nvkelso/natural-earth-vector GeoJSON mirror. 1:10
  * m land plus the minor-islands layer, which carries the small Aegean and Dalmatian islands the land layer drops. Not
  * gazetteer places: the rows go to `land`, never to `places`, so "land" is neither searchable nor a place a finding
  * can be assigned to.
  */
object NaturalEarthLand:

  val name = "land"
  val description = "Natural Earth 1:10 m land + minor islands (public domain) — clips polities to the coastline"
  val urls = List(
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_land.geojson",
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_minor_islands.geojson"
  )

  /** The polygon geometries of a GeoJSON FeatureCollection, each as its own GeoJSON text. Pure. */
  def geometries(rawJson: String): Either[String, List[String]] =
    io.circe.parser.parse(rawJson).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      doc.hcursor
        .downField("features")
        .focus
        .flatMap(_.asArray)
        .toRight("not a GeoJSON FeatureCollection")
        .map(_.toList.flatMap { f =>
          f.hcursor
            .downField("geometry")
            .focus
            .filter { g =>
              g.hcursor.downField("type").as[String].toOption.exists(t => t == "Polygon" || t == "MultiPolygon")
            }
            .map(_.noSpaces)
        })
    }

  /** Download both layers, replace the land, re-clip every polity. The result's `places` is the number of polities now
    * drawn clipped; `failed` carries the piece count as a note.
    */
  def run(ds: DataSource, dryRun: Boolean): Either[String, PlaceImportResult] =
    for
      raws <- urls.foldLeft[Either[String, List[String]]](Right(Nil))((acc, u) =>
        acc.flatMap(l => ImportRunner.download(u).map(l :+ _))
      )
      geoms <- raws.foldLeft[Either[String, List[String]]](Right(Nil))((acc, r) =>
        acc.flatMap(l => geometries(r).map(l ++ _))
      )
      _ <- if geoms.isEmpty then Left("no land polygons in the download") else Right(())
    yield
      if dryRun then PlaceImportResult(0, 0, 0, List(s"dry run: ${geoms.size} land polygons"))
      else
        scribe.info(s"[import] land: storing ${geoms.size} polygons…")
        val pieces = LandRepo.replace(ds, geoms)
        scribe.info(s"[import] land: $pieces pieces; clipping polities…")
        val clipped = LandRepo.reclip(ds)
        PlaceImportResult(clipped, 0, 0, List(s"$pieces land pieces from ${geoms.size} polygons"))
