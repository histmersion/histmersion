package histdb.server.importers

import histdb.core.*
import io.circe.{Json, JsonObject}

/** Imports classical-world geographic features from the Ancient World Mapping Center (AWMC).
  *
  * Source: https://github.com/AWMC/geodata
  * License: ODbL (Open Database License)
  * Coverage: Classical antiquity — provinces, roads, coastlines.
  *
  * The AWMC dataset is split across multiple GeoJSON files by category (provinces, roads, etc.).
  * Each file is a standard GeoJSON FeatureCollection with properties including `name` and
  * `description`. Unlike Cliopatria, AWMC features generally do NOT carry temporal ranges.
  *
  * This importer handles the `roman_empire_ce_117_provinces.geojson` and similar province files. */
object AwmcImporter extends GazetteerImporter:

  val name = "awmc"
  val description = "AWMC classical-world provinces and features (ODbL)"
  val fetchUrl = "https://raw.githubusercontent.com/AWMC/geodata/master/Cultural-Data/political_shading/roman_empire_ce_117_provinces.geojson"

  private val Source = "Ancient World Mapping Center (UNC), ODbL"

  def transform(rawJson: String): Either[String, List[PlaceInput]] =
    io.circe.parser.parse(rawJson).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      val features = doc.asObject.flatMap(_("features")).flatMap(_.asArray).map(_.toList)
        .orElse(doc.asArray.map(_.toList))
        .toRight("not a GeoJSON FeatureCollection")
      features.map(_.zipWithIndex.flatMap((f, i) => readFeature(f, i).toOption))
    }

  private def readFeature(j: Json, ordinal: Int): Either[String, PlaceInput] =
    j.asObject.toRight(s"feature #$ordinal is not a JSON object").flatMap { f =>
      val props = f("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val rawName = str(props, "name", "NAME", "title", "ADMIN").getOrElse("")
      if rawName.isEmpty then Left(s"feature #$ordinal has no name")
      else
        val slug = slugify(rawName)
        val id = s"awmc_$slug"
        val geom = f("geometry").filterNot(_.isNull).map(_.noSpaces)
        val desc = str(props, "description")
        val leftover = JsonObject.fromIterable(
          desc.map("description" -> Json.fromString(_)).toList
        )
        val place = Place(
          id = id,
          label = rawName,
          kind = PlaceKind.Region,
          geometry = geom,
          source = Some(Source),
          properties = Some(leftover).filter(_.nonEmpty).map(Json.fromJsonObject(_).noSpaces)
        )
        Right(PlaceInput(place, List(PlaceName(rawName))))
    }

  private def str(o: JsonObject, keys: String*): Option[String] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString).filter(_.nonEmpty)).nextOption

  private def slugify(name: String): String =
    name.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "")
