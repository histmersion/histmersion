package com.histmersion.domain

import com.histmersion.values.Period

import io.circe.Json

import java.util.Locale

/** The Library of Congress JSON API (https://www.loc.gov/apis/json-and-yaml/), no key: the maps format
  * (`/maps/?q=&fo=json`) narrowed to the Geography and Map Division, then each candidate's item (`/item/{id}/?fo=json`)
  * for its rights and files. A map is taken as public domain only when BOTH its rights advisory says "No known
  * restrictions" and its date is no later than `max-year` (1928: US works published before 1929). Its `location` list
  * is where it is of. Pure.
  */
object LibraryOfCongress:

  def searchUrl(base: String, name: String, limit: Int): String =
    s"${base.stripSuffix("/")}/maps/?q=${Read.enc(name)}&fo=json&c=${limit.max(1)}&fa=${Read.enc("partof:geography and map division")}"

  def itemUrl(base: String, id: String): String = s"${base.stripSuffix("/")}/item/${Read.enc(id)}/?fo=json"

  private val ItemId = """.*/item/([^/?#]+)/?.*""".r

  /** Pure: the item id of a result's `id`/`url` (`http://www.loc.gov/item/2007627513/` → `2007627513`). */
  def itemId(url: String): Option[String] = url match
    case ItemId(id) => Some(id)
    case _          => None

  /** Pure: the item ids of a search answer, in its order. */
  def results(json: Json): Either[String, List[String]] =
    val c = json.hcursor
    c.downField("results").focus.flatMap(_.asArray) match
      case None     => Left("no results in the answer")
      case Some(rs) =>
        Right(rs.toList.flatMap { r =>
          val h = r.hcursor
          (Read.str(h, "id").toList ++ Read.str(h, "url").toList).flatMap(itemId).headOption
        }.distinct)

  /** Pure: the first year in a date string (`1890`, `[ca. 1880]`, `1906-1910` → its range). */
  def yearsOf(date: String): Option[Period] =
    val ys = """(?<![0-9])([0-9]{4})(?![0-9])""".r.findAllMatchIn(date).map(_.group(1).toLong).toList
    ys.headOption.flatMap(_ => DeepTime.span(ys.min, ys.max))

  private val NoKnown = "no known restrictions"

  /** Pure: one item answer. `maxYear` is the public-domain boundary; `displayPx`/`thumbPx` choose among the item's JPEG
    * files (the largest no wider than display, the smallest no narrower than thumb).
    */
  def record(json: Json, base: String, maxYear: Int, displayPx: Int, thumbPx: Int): Either[String, MediaRecord] =
    val item = json.hcursor.downField("item")
    if item.focus.isEmpty then Left("no item in the answer")
    else
      val c = item
      val id = Read
        .str(c, "id")
        .flatMap(itemId)
        .orElse(Read.str(c, "url").flatMap(itemId))
        .orElse(Read.str(c, "library_of_congress_control_number"))
      id.toRight("no item id").map { key =>
        val title = Read.str(c, "title").getOrElse(key)
        val advisory = (Read.strs(c, "rights_advisory") ++ Read.strs(c, "rights")).mkString(" ")
        val date = Read.str(c, "date").orElse(Read.strs(c, "dates").headOption)
        val period = date.flatMap(yearsOf)
        val creator = Read.strs(c, "contributor_names").headOption.orElse(Read.strs(c, "creator").headOption)
        val page = s"${base.stripSuffix("/")}/item/$key/"
        val credit =
          (List(Some(title), creator).flatten :+ "Library of Congress, Geography and Map Division").mkString(", ")
        val files = json.hcursor
          .downField("resources")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .headOption
          .toList
          .flatMap(_.hcursor.downField("files").focus.flatMap(_.asArray).toList.flatten)
          .flatMap(_.asArray.toList.flatten)
          .flatMap { f =>
            val h = f.hcursor
            for
              u <- Read.str(h, "url")
              if Read
                .str(h, "mimetype")
                .exists(_.equalsIgnoreCase("image/jpeg")) || u.toLowerCase(Locale.ROOT).contains(".jpg")
            yield (u, Read.long(h, "width").getOrElse(0L))
          }
        val display =
          files.filter(_._2 <= displayPx).sortBy(-_._2).headOption.orElse(files.sortBy(_._2).headOption).map(_._1)
        val thumb = files.filter(_._2 >= thumbPx).sortBy(_._2).headOption.map(_._1)
        val original = files.sortBy(-_._2).headOption.map(_._1)
        val rights =
          if !advisory.toLowerCase(Locale.ROOT).contains(NoKnown) then
            Left(if advisory.isEmpty then "no rights statement" else "rights not \"No known restrictions\"")
          else if period.isEmpty then Left("no date on the map")
          else if period.exists(_.end.value > maxYear) then Left(s"made after $maxYear (may be in copyright)")
          else
            Right(MediaRights.publicDomain(creator, credit, Some("https://www.loc.gov/legal/understanding-copyright/")))
        val geo = Read.strs(c, "location").map("location" -> _)
        MediaRecord(key, title, page, display, thumb, original, rights, geo, period)
      }
