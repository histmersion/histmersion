package com.histmersion
package domain.catalogue.places
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem}

/** Natural Earth land for clipping polities to the coastline (V025) — `histmersion places fetch land`.
  *
  * Source: https://www.naturalearthdata.com (public domain), via the nvkelso/natural-earth-vector GeoJSON mirror. 1:10
  * m land plus the minor-islands layer, which carries the small Aegean and Dalmatian islands the land layer drops. Not
  * gazetteer places: the rows go to `land`, never to `places`, so "land" is neither searchable nor a place a finding
  * can be assigned to.
  */
import com.histmersion.api.catalogue.Place
import com.histmersion.server.HttpDownload
object NaturalEarthLand:
  val name = "land"
  val description = "Natural Earth 1:10 m land + minor islands (public domain) — clips polities to the coastline"
  val urls = List(
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_land.geojson",
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_minor_islands.geojson"
  )

  /** The polygon geometries of a GeoJSON FeatureCollection, each as its own GeoJSON text. Pure. */
  def geometries(rawJson: String): Either[Problem, List[PlaceDetailRow.Geometry]] =
    RawJson.parse(rawJson)
      .left
      .map(e => Problem.Unclassified(Legacy.Text(s"invalid JSON: $e")))
      .flatMap { doc =>
        doc.cursor
          .downField("features")
          .focus
          .flatMap(_.asArray)
          .toRight(Problem.Unclassified(Legacy.Text("not a GeoJSON FeatureCollection")))
          .map(_.toList.flatMap { f =>
            f.cursor
              .downField("geometry")
              .focus
              .filter { g =>
                g.cursor.downField("type").as[String].toOption.exists(t => t == "Polygon" || t == "MultiPolygon")
              }
              .map(g => PlaceDetailRow.Geometry(g.noSpaces))
          })
      }

  /** Download both layers (`download`: a url's text), replace the land, re-clip every polity. The result's `places` is
    * the number of polities now drawn clipped; `failed` carries the piece count as a note.
    */
  def run(
      download: HttpDownload.Url => Either[Problem, HttpDownload.Text],
      geometry: PlaceGeometry,
      dryRun: GazetteerControllers.DryRun
  ): Either[Problem, Place.ImportResult] =
    for
      raws <- urls.foldLeft[Either[Problem, List[String]]](Right(Nil))((acc, u) =>
        acc.flatMap(l => download(HttpDownload.Url(u)).map(l :+ _.value))
      )
      geoms <- raws.foldLeft[Either[Problem, List[PlaceDetailRow.Geometry]]](Right(Nil))((acc, r) =>
        acc.flatMap(l => geometries(r).map(l ++ _))
      )
      _ <-
        if geoms.isEmpty then Left(Problem.Unclassified(Legacy.Text("no land polygons in the download"))) else Right(())
    yield
      if dryRun.value then
        Place.ImportResult(
          Place.ImportResult.Places(0),
          Place.ImportResult.Names(0),
          Place.ImportResult.Relations(0),
          List(Place.ImportResult.Failed(s"dry run: ${geoms.size} land polygons"))
        )
      else
        scribe.info(s"[import] land: storing ${geoms.size} polygons…")
        val pieces = geometry.replaceLand(geoms)
        scribe.info(s"[import] land: $pieces pieces; clipping polities…")
        val clipped = geometry.reclip()
        Place.ImportResult(
          Place.ImportResult.Places(clipped.value),
          Place.ImportResult.Names(0),
          Place.ImportResult.Relations(0),
          List(Place.ImportResult.Failed(s"$pieces land pieces from ${geoms.size} polygons"))
        )
