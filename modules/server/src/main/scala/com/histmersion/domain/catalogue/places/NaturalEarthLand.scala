package com.histmersion
package domain.catalogue.places
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{External, Problem}

/** Natural Earth land for clipping polities to the coastline (V025) — `histmersion places fetch land`.
  *
  * Source: https://www.naturalearthdata.com (public domain), via the nvkelso/natural-earth-vector GeoJSON mirror. 1:10
  * m land plus the minor-islands layer, which carries the small Aegean and Dalmatian islands the land layer drops. Not
  * gazetteer places: the rows go to `land`, never to `places`, so "land" is neither searchable nor a place a finding
  * can be assigned to.
  */
import com.histmersion.api.catalogue.Place
import com.histmersion.server.HttpDownload
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object NaturalEarthLand:
  val name = "land"
  val description = "Natural Earth 1:10 m land + minor islands (public domain) — clips polities to the coastline"
  val urls = List(
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_land.geojson",
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_minor_islands.geojson"
  )

  /** The polygon geometries of a GeoJSON FeatureCollection, each as its own GeoJSON text. Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def geometries(rawJson: String): Either[Problem, List[PlaceDetailRow.Geometry]] =
    RawJson
      .parse(rawJson)
      .left
      .map(e =>
        Problem.Io.Corrupt(
          Problem.Io.Format.Json,
          Problem.Where("a Natural Earth layer"),
          Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))
        )
      )
      .flatMap { doc =>
        doc.cursor
          .downField("features")
          .focus
          .flatMap(_.asArray)
          .toRight(
            Problem.Io.Corrupt(
              Problem.Io.Format.GeoJson,
              Problem.Where("a Natural Earth layer"),
              Problem.Io.Damage.Structure(Problem.Io.Flaw.NotFeatureCollection)
            )
          )
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def run(
      download: HttpDownload.Url => Either[Problem, HttpDownload.Text],
      geometry: PlaceGeometry,
      dryRun: GazetteerControllers.DryRun
  )(using WritePermit): Either[Problem, Place.ImportResult] =
    for
      raws <- urls.foldLeft[Either[Problem, List[String]]](Right(Nil))((acc, u) =>
        acc.flatMap(l => download(HttpDownload.Url(u)).map(l :+ _.value))
      )
      geoms <- raws.foldLeft[Either[Problem, List[PlaceDetailRow.Geometry]]](Right(Nil))((acc, r) =>
        acc.flatMap(l => geometries(r).map(l ++ _))
      )
      _ <-
        if geoms.isEmpty then
          Left(
            Problem.Io.Corrupt(
              Problem.Io.Format.GeoJson,
              Problem.Where("the download"),
              Problem.Io.Damage.Structure(Problem.Io.Flaw.NoLandPolygons)
            )
          )
        else Right(())
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
