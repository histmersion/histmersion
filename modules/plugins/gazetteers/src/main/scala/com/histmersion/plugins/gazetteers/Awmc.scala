package com.histmersion.plugins.gazetteers

import com.histmersion.plugin.*
import io.circe.{Json, JsonObject}

/** Classical-world geographic features from the Ancient World Mapping Center (AWMC).
  *
  * Source: https://github.com/AWMC/geodata License: ODbL (Open Database License) Coverage: Classical antiquity — 2620
  * regional names with Pleiades IDs, plus political extents.
  *
  * Primary dataset: `regional_names_linework.geojson` — named regions, peoples, mountains, etc. with TITLE, TYPE, PID
  * (Pleiades ID). Geometry is MultiLineString (label placement lines), not polygons — but the Pleiades link lets us
  * match to sites with real geometry.
  */
object Awmc extends DownloadedGazetteer:

  type ReadResultPart1 = ReadResultPart1.T
  object ReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadText = ReadText.T
  object ReadText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFeatureResultPart1 = ReadFeatureResultPart1.T
  object ReadFeatureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFeatureOrdinal = ReadFeatureOrdinal.T
  object ReadFeatureOrdinal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfResult = KindOfResult.T
  object KindOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfPType = KindOfPType.T
  object KindOfPType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val title = "AWMC classical-world regional names and features"
  override val description = "AWMC classical-world regional names and features (ODbL, ~2600 features)"
  val url =
    "https://raw.githubusercontent.com/AWMC/geodata/master/Cultural-Data/regional_name_linework/regional_names_linework.geojson"
  val rights: GazetteerRights = GazetteerRights(
    Licence("ODbL"),
    "Ancient World Mapping Center (UNC), ODbL",
    Some("https://opendatacommons.org/licenses/odbl/1-0/")
  )

  def read(text: com.histmersion.plugins.gazetteers.Awmc.ReadText): Either[com.histmersion.plugins.gazetteers.Awmc.ReadResultPart1, List[GazetteerPlace]] =
    io.circe.parser.parse(text).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      val features = doc.asObject
        .flatMap(_("features"))
        .flatMap(_.asArray)
        .map(_.toList)
        .orElse(doc.asArray.map(_.toList))
        .toRight("not a GeoJSON FeatureCollection")
      features.map(_.zipWithIndex.flatMap((f, i) => readFeature(f, i).toOption))
    }

  def readFeature(j: Json, ordinal: com.histmersion.plugins.gazetteers.Awmc.ReadFeatureOrdinal): Either[com.histmersion.plugins.gazetteers.Awmc.ReadFeatureResultPart1, GazetteerPlace] =
    j.asObject.toRight(s"feature #$ordinal is not a JSON object").flatMap { f =>
      val props = f("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val title = str(props, "TITLE", "title", "name", "NAME").getOrElse("")
      if title.isEmpty then Left(s"feature #$ordinal has no TITLE")
      else
        val pType = str(props, "P_TYPE", "TYPE", "type").getOrElse("region")
        val pid = str(props, "PID")
        val slug = slugify(title)
        val id = pid.map(p => s"awmc_pleiades_$p").getOrElse(s"awmc_$slug")
        val geom = f("geometry").filterNot(_.isNull).map(_.noSpaces)

        val leftover = JsonObject.fromIterable(
          pid.map("pleiadesId" -> Json.fromString(_)).toList ++
            List("awmcType" -> Json.fromString(pType)) ++
            str(props, "NOTES", "notes").map("notes" -> Json.fromString(_)).toList ++
            str(props, "CREATOR", "creator").map("creator" -> Json.fromString(_)).toList
        )
        Right(
          GazetteerPlace(
            id = id,
            label = title,
            kind = kindOf(pType),
            geometry = geom,
            properties = Some(leftover).filter(_.nonEmpty).map(Json.fromJsonObject(_).noSpaces),
            names = List(GazetteerName(title))
          )
        )
    }

  /** The map's kind of an AWMC type: a mountain, plain, island or labelled feature is a `feature`, anything else a
    * `region`.
    */
  def kindOf(pType: com.histmersion.plugins.gazetteers.Awmc.KindOfPType): com.histmersion.plugins.gazetteers.Awmc.KindOfResult = pType.toLowerCase match
    case "region"                        => "region"
    case "people, tribe" | "people"      => "region"
    case "mountain" | "plain" | "island" => "feature"
    case "labeled feature"               => "feature"
    case _                               => "region"
