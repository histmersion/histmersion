package com.histmersion
package plugins.gazetteers
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.{Json, JsonObject}

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

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
  type Ordinal = Ordinal.T
  object Ordinal extends Newtype[Int, Pure]

  val title = GazetteerSource.Title("AWMC classical-world regional names and features")
  override val description =
    GazetteerSource.Description("AWMC classical-world regional names and features (ODbL, ~2600 features)")
  val url = Plugin.Http.Url(
    "https://raw.githubusercontent.com/AWMC/geodata/master/Cultural-Data/regional_name_linework/regional_names_linework.geojson"
  )
  val rights: GazetteerRights = GazetteerRights(
    Licence("ODbL"),
    GazetteerRights.Credit("Ancient World Mapping Center (UNC), ODbL"),
    Some(GazetteerRights.LicenceUrl("https://opendatacommons.org/licenses/odbl/1-0/"))
  )

  def read(text: DownloadedGazetteer.Text): Either[Problem, List[GazetteerPlace]] =
    RawJson
      .parse(text.value)
      .left
      .map(e => Problem.Unclassified(Legacy.Text(s"invalid JSON: $e")))
      .flatMap { doc =>
        val features = doc.asObject
          .flatMap(_("features"))
          .flatMap(_.asArray)
          .map(_.toList)
          .orElse(doc.asArray.map(_.toList))
          .toRight(Problem.Unclassified(Legacy.Text("not a GeoJSON FeatureCollection")))
        features.map(_.zipWithIndex.flatMap((f, i) => readFeature(f, Ordinal(i)).toOption))
      }

  def readFeature(j: Json, ordinal: Ordinal): Either[Problem, GazetteerPlace] =
    j.asObject.toRight(Problem.Unclassified(Legacy.Text(s"feature #$ordinal is not a JSON object"))).flatMap { f =>
      val props = f("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
      def text(keys: String*): Option[String] = str(props, keys*).map(_.value)
      val title = text("TITLE", "title", "name", "NAME").getOrElse("")
      if title.isEmpty then Left(Problem.Unclassified(Legacy.Text(s"feature #$ordinal has no TITLE")))
      else
        val pType = text("P_TYPE", "TYPE", "type").getOrElse("region")
        val pid = text("PID")
        val slug = slugify(GazetteerName.Name(title)).value
        val id = pid.map(p => s"awmc_pleiades_$p").getOrElse(s"awmc_$slug")
        val geom = f("geometry").filterNot(_.isNull).map(_.noSpaces)

        val leftover = JsonObject.fromIterable(
          pid.map("pleiadesId" -> Json.fromString(_)).toList ++
            List("awmcType" -> Json.fromString(pType)) ++
            text("NOTES", "notes").map("notes" -> Json.fromString(_)).toList ++
            text("CREATOR", "creator").map("creator" -> Json.fromString(_)).toList
        )
        Right(
          GazetteerPlace(
            id = GazetteerPlace.Id(id),
            label = GazetteerPlace.Label(title),
            kind = kindOf(GazetteerPlace.Kind(pType)),
            geometry = geom.map(g => GazetteerPlace.Geometry(g)),
            properties =
              Some(leftover).filter(_.nonEmpty).map(o => GazetteerPlace.Properties(Json.fromJsonObject(o).noSpaces)),
            names = List(GazetteerName(GazetteerName.Name(title)))
          )
        )
    }

  /** The map's kind of an AWMC type: a mountain, plain, island or labelled feature is a `feature`, anything else a
    * `region`.
    */
  def kindOf(pType: GazetteerPlace.Kind): GazetteerPlace.Kind = pType.value.toLowerCase match
    case "region"                        => GazetteerPlace.Kind("region")
    case "people, tribe" | "people"      => GazetteerPlace.Kind("region")
    case "mountain" | "plain" | "island" => GazetteerPlace.Kind("feature")
    case "labeled feature"               => GazetteerPlace.Kind("feature")
    case _                               => GazetteerPlace.Kind("region")
