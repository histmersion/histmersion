package com.histmersion
package plugins.gazetteers

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.plugin.*
import io.circe.{Json, JsonObject}

/** Named historical periods from ChronOntology (DAI).
  *
  * Source: https://chronontology.dainst.org License: open access Coverage: ~14K named periods worldwide, from
  * prehistory to modern.
  *
  * Each period has multilingual names, a four-point time span (notBefore/notAfter/at), types (material_culture,
  * political, geological, etc.) and region associations. We map each to a place with kind derived from the period type
  * and the time span as its period.
  */
object ChronOntology extends DownloadedGazetteer:
  val title = GazetteerSource.Title("ChronOntology named periods")
  override val description =
    GazetteerSource.Description("ChronOntology named periods (DAI, open access, ~14K periods)")
  val url = Plugin.Http.Url("https://chronontology.dainst.org/data/period?size=15000&from=0")
  val rights: GazetteerRights =
    GazetteerRights(Licence("open access"), GazetteerRights.Credit("ChronOntology (DAI), open access"))

  def read(text: DownloadedGazetteer.Text): Either[Problem, List[GazetteerPlace]] =
    io.circe.parser
      .parse(text.value)
      .left
      .map(e => Problem.Unclassified(Legacy.Text(s"invalid JSON: ${e.getMessage}")))
      .flatMap { doc =>
        val results = doc.asObject
          .flatMap(_("results"))
          .flatMap(_.asArray)
          .map(_.toList)
          .toRight(Problem.Unclassified(Legacy.Text("expected {results: [...]}")))
        results.map(_.flatMap(r => readResult(r).toOption))
      }

  def readResult(j: Json): Either[Problem, GazetteerPlace] =
    j.asObject.toRight(Problem.Unclassified(Legacy.Text("result is not a JSON object"))).flatMap { wrapper =>
      val res = wrapper("resource").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val id = strField(res, "id").getOrElse("")
      if id.isEmpty then Left(Problem.Unclassified(Legacy.Text("result has no id")))
      else
        val label = bestName(res)
        if label.value.isEmpty then Left(Problem.Unclassified(Legacy.Text(s"period $id has no name")))
        else
          val regions = extractRegions(wrapper)
          val props = JsonObject.fromIterable(
            List("chronontologyId" -> Json.fromString(id)) ++
              regions.headOption.map(r => "region" -> Json.fromString(r)).toList
          )
          Right(
            GazetteerPlace(
              id = GazetteerPlace.Id(s"chrono_$id"),
              label = GazetteerPlace.Label(label.value),
              kind = extractKind(res),
              period = extractPeriod(res),
              properties = Some(GazetteerPlace.Properties(Json.fromJsonObject(props).noSpaces)),
              names = allNames(res).map(n => GazetteerName(n._1, n._2))
            )
          )
    }

  private def bestName(res: JsonObject): GazetteerName.Name =
    val names = res("names").flatMap(_.asObject).getOrElse(JsonObject.empty)
    val en = names("en").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asString)
    GazetteerName.Name(
      en.getOrElse {
        names.toIterable.flatMap((_, v) => v.asArray.flatMap(_.headOption).flatMap(_.asString)).headOption.getOrElse("")
      }
    )

  private def allNames(res: JsonObject): List[(GazetteerName.Name, GazetteerName.Lang)] =
    val names = res("names").flatMap(_.asObject).getOrElse(JsonObject.empty)
    names.toList.flatMap { (lang, arr) =>
      arr.asArray
        .getOrElse(Vector.empty)
        .flatMap(_.asString)
        .map(n => (GazetteerName.Name(n), GazetteerName.Lang(lang)))
    }

  private def extractPeriod(res: JsonObject): Option[Period] =
    val timespans = res("hasTimespan").flatMap(_.asArray).getOrElse(Vector.empty)
    if timespans.isEmpty then None
    else
      val ts = timespans.head.asObject.getOrElse(JsonObject.empty)
      val begin = ts("begin").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val end = ts("end").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val startYear = parseYear(begin, "at").orElse(parseYear(begin, "notBefore"))
      val endYear = parseYear(end, "at").orElse(parseYear(end, "notAfter"))
      (startYear, endYear) match
        case (Some(s), Some(e)) => span(s, e)
        case (Some(s), None)    => span(s, s)
        case (None, Some(e))    => span(e, e)
        case _                  => None

  private def parseYear(obj: JsonObject, key: String): Option[DownloadedGazetteer.Year] =
    obj(key).flatMap(_.asString).flatMap { s =>
      val trimmed = s.trim
      if trimmed.isEmpty || trimmed == "not specified" then None
      else trimmed.toLongOption.map(DownloadedGazetteer.Year(_))
    }

  private def extractKind(res: JsonObject): GazetteerPlace.Kind =
    val types = res("types")
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .flatMap(_.asString)
      .map(_.toLowerCase)
    if types.contains("political") then GazetteerPlace.Kind("polity")
    else if types.contains("geological") || types.contains("climatological") then GazetteerPlace.Kind("feature")
    else GazetteerPlace.Kind("region")

  private def extractRegions(wrapper: JsonObject): List[String] =
    wrapper("regions").flatMap(_.asArray).getOrElse(Vector.empty).toList.flatMap { r =>
      r.asObject.flatMap(o => o("en").flatMap(_.asString).orElse(o("de").flatMap(_.asString)))
    }

  private def strField(o: JsonObject, key: String): Option[String] =
    o(key).flatMap(_.asString).filter(_.nonEmpty)
