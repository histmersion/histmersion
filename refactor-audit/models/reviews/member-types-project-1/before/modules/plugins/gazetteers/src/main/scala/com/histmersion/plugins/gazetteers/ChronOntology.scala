package com.histmersion.plugins.gazetteers

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

  val title = "ChronOntology named periods"
  override val description = "ChronOntology named periods (DAI, open access, ~14K periods)"
  val url = "https://chronontology.dainst.org/data/period?size=15000&from=0"
  val rights: GazetteerRights = GazetteerRights(Licence("open access"), "ChronOntology (DAI), open access")

  def read(text: String): Either[String, List[GazetteerPlace]] =
    io.circe.parser.parse(text).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      val results = doc.asObject
        .flatMap(_("results"))
        .flatMap(_.asArray)
        .map(_.toList)
        .toRight("expected {results: [...]}")
      results.map(_.flatMap(r => readResult(r).toOption))
    }

  def readResult(j: Json): Either[String, GazetteerPlace] =
    j.asObject.toRight("result is not a JSON object").flatMap { wrapper =>
      val res = wrapper("resource").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val id = strField(res, "id").getOrElse("")
      if id.isEmpty then Left("result has no id")
      else
        val label = bestName(res)
        if label.isEmpty then Left(s"period $id has no name")
        else
          val regions = extractRegions(wrapper)
          val props = JsonObject.fromIterable(
            List("chronontologyId" -> Json.fromString(id)) ++
              regions.headOption.map(r => "region" -> Json.fromString(r)).toList
          )
          Right(
            GazetteerPlace(
              id = s"chrono_$id",
              label = label,
              kind = extractKind(res),
              period = extractPeriod(res),
              properties = Some(Json.fromJsonObject(props).noSpaces),
              names = allNames(res).map(n => GazetteerName(n._1, n._2))
            )
          )
    }

  private def bestName(res: JsonObject): String =
    val names = res("names").flatMap(_.asObject).getOrElse(JsonObject.empty)
    val en = names("en").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asString)
    en.getOrElse {
      names.toIterable.flatMap((_, v) => v.asArray.flatMap(_.headOption).flatMap(_.asString)).headOption.getOrElse("")
    }

  private def allNames(res: JsonObject): List[(String, String)] =
    val names = res("names").flatMap(_.asObject).getOrElse(JsonObject.empty)
    names.toList.flatMap { (lang, arr) =>
      arr.asArray.getOrElse(Vector.empty).flatMap(_.asString).map(n => (n, lang))
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

  private def parseYear(obj: JsonObject, key: String): Option[Long] =
    obj(key).flatMap(_.asString).flatMap { s =>
      val trimmed = s.trim
      if trimmed.isEmpty || trimmed == "not specified" then None
      else trimmed.toLongOption
    }

  private def extractKind(res: JsonObject): String =
    val types = res("types")
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .flatMap(_.asString)
      .map(_.toLowerCase)
    if types.contains("political") then "polity"
    else if types.contains("geological") || types.contains("climatological") then "feature"
    else "region"

  private def extractRegions(wrapper: JsonObject): List[String] =
    wrapper("regions").flatMap(_.asArray).getOrElse(Vector.empty).toList.flatMap { r =>
      r.asObject.flatMap(o => o("en").flatMap(_.asString).orElse(o("de").flatMap(_.asString)))
    }

  private def strField(o: JsonObject, key: String): Option[String] =
    o(key).flatMap(_.asString).filter(_.nonEmpty)
