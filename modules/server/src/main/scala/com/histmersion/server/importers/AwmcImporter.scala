package com.histmersion.server.importers

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import io.circe.{Json, JsonObject}

/** Imports classical-world geographic features from the Ancient World Mapping Center (AWMC).
  *
  * Source: https://github.com/AWMC/geodata
  * License: ODbL (Open Database License)
  * Coverage: Classical antiquity — 2620 regional names with Pleiades IDs, plus political extents.
  *
  * Primary dataset: `regional_names_linework.geojson` — named regions, peoples, mountains, etc.
  * with TITLE, TYPE, PID (Pleiades ID). Geometry is MultiLineString (label placement lines),
  * not polygons — but the Pleiades link lets us match to sites with real geometry. */
object AwmcImporter extends GazetteerImporter:

  val name = "awmc"
  val description = "AWMC classical-world regional names and features (ODbL, ~2600 features)"
  val fetchUrl = "https://raw.githubusercontent.com/AWMC/geodata/master/Cultural-Data/regional_name_linework/regional_names_linework.geojson"

  private val Source = "Ancient World Mapping Center (UNC), ODbL"

  def transform(rawJson: String): Either[String, List[PlaceInput]] =
    io.circe.parser.parse(rawJson).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      val features = doc.asObject.flatMap(_("features")).flatMap(_.asArray).map(_.toList)
        .orElse(doc.asArray.map(_.toList))
        .toRight("not a GeoJSON FeatureCollection")
      features.map(_.zipWithIndex.flatMap((f, i) => readFeature(f, i).toOption))
    }

  private[importers] def readFeature(j: Json, ordinal: Int): Either[String, PlaceInput] =
    j.asObject.toRight(s"feature #$ordinal is not a JSON object").flatMap { f =>
      val props = f("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val title = str(props, "TITLE", "title", "name", "NAME").getOrElse("")
      if title.isEmpty then Left(s"feature #$ordinal has no TITLE")
      else
        val pType = str(props, "P_TYPE", "TYPE", "type").getOrElse("region")
        val pid = str(props, "PID")
        val slug = slugify(title)
        val id = pid.map(p => s"awmc_pleiades_$p").getOrElse(s"awmc_$slug")
        val kind = mapKind(pType)
        val geom = f("geometry").filterNot(_.isNull).map(_.noSpaces)

        val leftover = JsonObject.fromIterable(
          pid.map("pleiadesId" -> Json.fromString(_)).toList ++
          List("awmcType" -> Json.fromString(pType)) ++
          str(props, "NOTES", "notes").map("notes" -> Json.fromString(_)).toList ++
          str(props, "CREATOR", "creator").map("creator" -> Json.fromString(_)).toList
        )

        val place = Place(
          id = id,
          label = title,
          kind = kind,
          geometry = geom,
          source = Some(Source),
          properties = Some(leftover).filter(_.nonEmpty).map(Json.fromJsonObject(_).noSpaces)
        )
        Right(PlaceInput(place, List(PlaceName(title))))
    }

  private def mapKind(pType: String): String = pType.toLowerCase match
    case "region"                        => PlaceKind.Region
    case "people, tribe" | "people"      => PlaceKind.Region
    case "mountain" | "plain" | "island" => PlaceKind.Feature
    case "labeled feature"               => PlaceKind.Feature
    case _                               => PlaceKind.Region

  private def str(o: JsonObject, keys: String*): Option[String] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString).filter(_.nonEmpty)).nextOption()

  private def slugify(name: String): String =
    name.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "")
