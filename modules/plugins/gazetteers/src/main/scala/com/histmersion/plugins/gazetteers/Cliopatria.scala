package com.histmersion
package plugins.gazetteers
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.{Json, JsonObject}

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

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
  type Ordinal = Ordinal.T
  object Ordinal extends Newtype[Int, Pure]

  val title = GazetteerSource.Title("Seshat Cliopatria polity polygons")
  override val description =
    GazetteerSource.Description("Seshat Cliopatria polity polygons (CC BY 4.0, ~13K features from 3400 BCE)")
  val url =
    Plugin.Http.Url("https://github.com/Seshat-Global-History-Databank/cliopatria/raw/main/cliopatria.geojson.zip")
  val rights: GazetteerRights = GazetteerRights(
    Licence("CC BY 4.0"),
    GazetteerRights.Credit("Cliopatria (Seshat Databank), CC BY 4.0"),
    Some(GazetteerRights.LicenceUrl("https://creativecommons.org/licenses/by/4.0/"))
  )

  private val Polity = GazetteerPlace.Kind("polity")
  private val PartOf = GazetteerRelation.Kind(GazetteerRelation.PartOf)

  override def fetch(http: Plugin.Http): Either[Problem, List[GazetteerPlace]] =
    download(http).flatMap(read).map(items => parentConcepts(items) ++ items)

  def read(text: DownloadedGazetteer.Text): Either[Problem, List[GazetteerPlace]] =
    RawJson
      .parse(text.value)
      .left
      .map(e =>
        Problem.Io.Corrupt(
          Problem.Io.Format.Json,
          Problem.Where("the Cliopatria polities"),
          Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))
        )
      )
      .flatMap { doc =>
        val features = doc.asObject
          .flatMap(_("features"))
          .flatMap(_.asArray)
          .map(_.toList)
          .orElse(doc.asArray.map(_.toList))
          .toRight(Problem.Invalid.Missing.here(Problem.Field("features")))
        features.map(_.zipWithIndex.flatMap((f, i) => readFeature(f, Ordinal(i)).toOption))
      }

  def readFeature(j: Json, ordinal: Ordinal): Either[Problem, GazetteerPlace] =
    j.asObject
      .toRight(Problem.Invalid.Missing(Problem.Field("object"), Vector(Problem.Field(s"feature #$ordinal"))))
      .flatMap { f =>
        val props = f("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
        val rawName = str(props, "Name").fold("")(_.value)
        if rawName.isEmpty then
          Left(Problem.Invalid.Missing(Problem.Field("Name"), Vector(Problem.Field(s"feature #$ordinal"))))
        else
          val from = numYear(props, "FromYear")
          val to = numYear(props, "ToYear")
          val slug = slugify(GazetteerName.Name(rawName))
          val sliceId = (from, to) match
            case (Some(a), Some(b)) => s"cliopatria_${slug}_${a}_$b"
            case _                  => s"cliopatria_${slug}_$ordinal"
          val parentId = s"cliopatria_$slug"
          val period = for a <- from; b <- to; p <- span(a, b) yield p
          val geom = f("geometry").filterNot(_.isNull).map(g => GazetteerPlace.Geometry(g.noSpaces))

          val wikidata = str(props, "Wikidata").filter(_.value.nonEmpty)
          val wikipedia = str(props, "Wikipedia").filter(_.value.nonEmpty)
          val memberOf = str(props, "MemberOf").filter(_.value.nonEmpty)
          val seshatId = str(props, "SeshatID").filter(_.value.nonEmpty)
          val leftover = JsonObject.fromIterable(
            wikidata.map(w => "wikidata" -> Json.fromString(w.value)).toList ++
              wikipedia.map(w => "wikipedia" -> Json.fromString(w.value)).toList ++
              seshatId.map(s => "seshatId" -> Json.fromString(s.value)).toList ++
              memberOf.map(m => "memberOf" -> Json.fromString(m.value)).toList
          )
          val relations = List(
            GazetteerRelation(GazetteerRelation.ChildId(sliceId), GazetteerRelation.ParentId(parentId), PartOf, period)
          ) ++
            memberOf.map(m =>
              GazetteerRelation(
                GazetteerRelation.ChildId(sliceId),
                GazetteerRelation.ParentId(s"cliopatria_${slugify(GazetteerName.Name(m.value))}"),
                PartOf,
                period
              )
            )
          Right(
            GazetteerPlace(
              id = GazetteerPlace.Id(sliceId),
              label = GazetteerPlace.Label(rawName),
              kind = Polity,
              geometry = geom,
              period = period,
              properties =
                Some(leftover).filter(_.nonEmpty).map(l => GazetteerPlace.Properties(Json.fromJsonObject(l).noSpaces)),
              names = List(GazetteerName(GazetteerName.Name(rawName))),
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
      .filter(_.value.startsWith("cliopatria_"))
      .filterNot(id => features.exists(_.id.value == id.value))
      .map { pid =>
        val label = pid.value.stripPrefix("cliopatria_").replace("_", " ")
        val dated = spans.getOrElse(pid, Nil)
        val period =
          if dated.isEmpty then None
          else
            span(
              DownloadedGazetteer.Year(dated.map(_.start.value.toLong).min),
              DownloadedGazetteer.Year(dated.map(_.end.value.toLong).max)
            )
        GazetteerPlace(
          id = GazetteerPlace.Id(pid.value),
          label = GazetteerPlace.Label(label),
          kind = Polity,
          period = period
        )
      }

  private def numYear(o: JsonObject, key: String): Option[DownloadedGazetteer.Year] =
    o(key)
      .flatMap(j => j.asNumber.flatMap(_.asLong).orElse(j.asString.flatMap(_.toLongOption)))
      .map(DownloadedGazetteer.Year(_))
