package com.histmersion.plugins.gazetteers

import com.histmersion.plugin.*
import io.circe.{Json, JsonObject}

/** Polity polygons from the Seshat Cliopatria dataset.
  *
  * Source: https://github.com/Seshat-Global-History-Databank/cliopatria License: CC BY 4.0 (the repository's
  * LICENSE.md) Coverage: ~13,700 features, 3400 BCE to present, worldwide polity boundaries.
  *
  * Each Cliopatria feature is a time-slice of a polity — the same polity name appears multiple times with different
  * `FromYear`/`ToYear` ranges and (often) different geometry. We preserve this as individual place entries keyed by
  * `cliopatria_{slug}_{from}_{to}`, each with its own geometry and period. A `part_of` relation links each slice to a
  * parent concept `cliopatria_{slug}` (with no geometry, just the label), so the hierarchy query finds all slices. The
  * parents come first in [[fetch]]'s answer, so the slices' relations find them.
  */
object Cliopatria extends DownloadedGazetteer:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = Year.T
  object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ReadFeatureOrdinal = ReadFeatureOrdinal.T
  object ReadFeatureOrdinal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val title = "Seshat Cliopatria polity polygons"
  override val description = "Seshat Cliopatria polity polygons (CC BY 4.0, ~13K features from 3400 BCE)"
  val url = "https://github.com/Seshat-Global-History-Databank/cliopatria/raw/main/cliopatria.geojson.zip"
  val rights: GazetteerRights = GazetteerRights(
    Licence("CC BY 4.0"),
    "Cliopatria (Seshat Databank), CC BY 4.0",
    Some("https://creativecommons.org/licenses/by/4.0/")
  )

  private val Polity = "polity"

  override def fetch(http: Plugin.Http): Either[String, List[GazetteerPlace]] =
    download(http).flatMap(read).map(items => parentConcepts(items) ++ items)

  def read(text: com.histmersion.plugins.gazetteers.Cliopatria.Text): Either[com.histmersion.plugins.gazetteers.Cliopatria.Problem, List[GazetteerPlace]] =
    io.circe.parser.parse(text).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      val features = doc.asObject
        .flatMap(_("features"))
        .flatMap(_.asArray)
        .map(_.toList)
        .orElse(doc.asArray.map(_.toList))
        .toRight("not a GeoJSON FeatureCollection")
      features.map(_.zipWithIndex.flatMap((f, i) => readFeature(f, i).toOption))
    }

  def readFeature(j: Json, ordinal: com.histmersion.plugins.gazetteers.Cliopatria.ReadFeatureOrdinal): Either[com.histmersion.plugins.gazetteers.Cliopatria.Problem, GazetteerPlace] =
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
          case _                  => s"cliopatria_${slug}_$ordinal"
        val parentId = s"cliopatria_$slug"
        val period = for a <- from; b <- to; p <- span(a, b) yield p
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
        val relations = List(GazetteerRelation(sliceId, parentId, GazetteerRelation.PartOf, period)) ++
          memberOf.map(m => GazetteerRelation(sliceId, s"cliopatria_${slugify(m)}", GazetteerRelation.PartOf, period))
        Right(
          GazetteerPlace(
            id = sliceId,
            label = rawName,
            kind = Polity,
            geometry = geom,
            period = period,
            properties = Some(leftover).filter(_.nonEmpty).map(Json.fromJsonObject(_).noSpaces),
            names = List(GazetteerName(rawName)),
            relations = relations
          )
        )
    }

  /** Build parent-concept entries (no geometry) from the distinct polity names. These serve as the grouping nodes so
    * `places children cliopatria_sumerian_city_states` returns all time-slices. A concept spans its slices: from the
    * earliest start to the latest end of the dated slices that name it (as polity or as `MemberOf`). Without that
    * period it was "valid at every year" — 1 604 undated polities in every time-filtered view until 2026-10-09 (V076
    * dated the stored ones).
    */
  def parentConcepts(features: List[GazetteerPlace]): List[GazetteerPlace] =
    val spans = features.flatMap(_.relations).groupMapReduce(_.parentId)(_.period.toList)(_ ++ _)
    features
      .flatMap(_.relations.map(_.parentId))
      .distinct
      .filter(_.startsWith("cliopatria_"))
      .filterNot(id => features.exists(_.id == id))
      .map { pid =>
        val label = pid.stripPrefix("cliopatria_").replace("_", " ")
        val dated = spans.getOrElse(pid, Nil)
        val period =
          if dated.isEmpty then None
          else span(dated.map(_.start.value.toLong).min, dated.map(_.end.value.toLong).max)
        GazetteerPlace(id = pid, label = label, kind = Polity, period = period)
      }

  private def numYear(o: JsonObject, key: com.histmersion.plugins.gazetteers.Cliopatria.Key): Option[com.histmersion.plugins.gazetteers.Cliopatria.Year] =
    o(key).flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.toLongOption)))
