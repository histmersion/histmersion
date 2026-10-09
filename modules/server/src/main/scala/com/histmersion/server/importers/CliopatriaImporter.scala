package com.histmersion.server.importers

import com.histmersion.core.*
import io.circe.{Json, JsonObject}

/** Imports polity polygons from the Seshat Cliopatria dataset.
  *
  * Source: https://github.com/Seshat-Global-History-Databank/cliopatria
  * License: CC BY-NC-SA
  * Coverage: ~13,700 features, 3400 BCE to present, worldwide polity boundaries.
  *
  * Each Cliopatria feature is a time-slice of a polity — the same polity name appears multiple times
  * with different `FromYear`/`ToYear` ranges and (often) different geometry. We preserve this as
  * individual place entries keyed by `cliopatria_{slug}_{from}_{to}`, each with its own geometry
  * and period. A `part_of` relation links each slice to a parent concept `cliopatria_{slug}` (with
  * no geometry, just the label), so the hierarchy query finds all slices. */
object CliopatriaImporter extends GazetteerImporter:

  val name = "cliopatria"
  val description = "Seshat Cliopatria polity polygons (CC BY-NC-SA, ~13K features from 3400 BCE)"
  val fetchUrl = "https://github.com/Seshat-Global-History-Databank/cliopatria/raw/main/cliopatria.geojson.zip"

  private val Source = "Cliopatria (Seshat Databank), CC BY-NC-SA"

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
      val rawName = str(props, "Name").getOrElse("")
      if rawName.isEmpty then Left(s"feature #$ordinal has no Name")
      else
        val from = numYear(props, "FromYear")
        val to = numYear(props, "ToYear")
        val slug = slugify(rawName)
        val sliceId = (from, to) match
          case (Some(a), Some(b)) => s"cliopatria_${slug}_${a}_$b"
          case _ => s"cliopatria_${slug}_$ordinal"
        val parentId = s"cliopatria_$slug"
        val period = for a <- from; b <- to yield Period(a, b)
        val geom = f("geometry").filterNot(_.isNull).map(_.noSpaces)

        val wikidata = str(props, "Wikidata").filter(_.nonEmpty)
        val wikipedia = str(props, "Wikipedia").filter(_.nonEmpty)
        val memberOf = str(props, "MemberOf").filter(_.nonEmpty)
        val seshatId = str(props, "SeshatID").filter(_.nonEmpty)
        val leftover = JsonObject.fromIterable(
          wikidata.map("wikidata" -> Json.fromString(_)).toList ++
          wikipedia.map("wikipedia" -> Json.fromString(_)).toList ++
          seshatId.map("seshatId" -> Json.fromString(_)).toList ++
          memberOf.map("memberOf" -> Json.fromString(_)).toList
        )

        val place = Place(
          id = sliceId,
          label = rawName,
          kind = PlaceKind.Polity,
          geometry = geom,
          period = period,
          source = Some(Source),
          properties = Some(leftover).filter(_.nonEmpty).map(Json.fromJsonObject(_).noSpaces)
        )
        val names = List(PlaceName(rawName))
        val relations = List(PlaceRelation(sliceId, parentId, PlaceRelation.PartOf, period)) ++
          memberOf.map(m => PlaceRelation(sliceId, s"cliopatria_${slugify(m)}", PlaceRelation.PartOf, period))
        Right(PlaceInput(place, names, relations))
    }

  /** Build parent-concept entries (no geometry) from the distinct polity names. These serve as the
    * grouping nodes so `places children cliopatria_sumerian_city_states` returns all time-slices.
    * A concept spans its slices: from the earliest start to the latest end of the dated slices that
    * name it (as polity or as `MemberOf`). Without that period it was "valid at every year" — 1 604
    * undated polities in every time-filtered view until 2026-10-09 (V075 dated the stored ones). */
  def parentConcepts(features: List[PlaceInput]): List[PlaceInput] =
    val spans = features.flatMap(_.relations).groupMapReduce(_.parentId)(_.period.toList)(_ ++ _)
    features.flatMap(_.relations.map(_.parentId)).distinct
      .filter(_.startsWith("cliopatria_"))
      .filterNot(id => features.exists(_.place.id == id))
      .map { pid =>
        val label  = pid.stripPrefix("cliopatria_").replace("_", " ")
        val dated  = spans.getOrElse(pid, Nil)
        val period = Option.when(dated.nonEmpty)(Period(dated.map(_.start).min, dated.map(_.end).max))
        PlaceInput(Place(id = pid, label = label, kind = PlaceKind.Polity, period = period, source = Some(Source)))
      }

  private def str(o: JsonObject, keys: String*): Option[String] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString).filter(_.nonEmpty)).nextOption

  private def numYear(o: JsonObject, key: String): Option[Long] =
    o(key).flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.toLongOption)))

  private def slugify(name: String): String =
    name.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "")
