package com.histmersion
package plugins.media

import com.histmersion.plugin.*
import io.circe.Json

import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure

/** `loc`: the Library of Congress JSON API (https://www.loc.gov/apis/json-and-yaml/), no key: the maps format
  * (`/maps/?q=&fo=json`) narrowed to the Geography and Map Division, then each candidate's item (`/item/{id}/?fo=json`)
  * for its rights and files. A map is taken as public domain only when BOTH its rights advisory says "No known
  * restrictions" and its date is no later than `max-year` (1928: US works published before 1929). Its `location` list
  * is where it is of. A 403 is loc.gov's bot challenge to automated clients — reported as such, never worked around.
  */
final class LocCollection(conf: CollectionSettings, thumbPx: LocCollection.ThumbPx)
    extends MuseumCollection(conf, MuseumCollection.Provider("Library of Congress"), MuseumCollection.Key("media.loc")):
  val title = MediaCollection.TitleResult("Library of Congress maps")
  override val description =
    MediaCollection.DescriptionResult(
      "Historical maps from the Library of Congress, Geography and Map Division: public domain only (rights " +
        "'No known restrictions' and dated by media.loc.max-year), linked as role map to the places their " +
        "locations name"
    )
  val role = MediaCollection.RoleResult(MediaCollection.Map)
  override def matchRule = MediaMatchRule.Map(Year(conf.maxYear.value))

  private def explain(e: String): String =
    if e.contains("HTTP 403") then
      s"$e — loc.gov refuses automated clients with a bot challenge at the moment; not worked around"
    else e

  def search(place: MediaPlace, http: Plugin.Http): Either[MediaCollection.SearchResultPart1, List[Either[MediaCollection.SearchResultPart2Part1, MediaItem]]] =
    http
      .askJson(LocCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(j => LocCollection.results(j).left.map(_.value))
      .left
      .map(explain)
      .map(
        _.take(conf.candidates.value.max(0)).map(id =>
          http
            .askJson(LocCollection.itemUrl(conf.apiUrl, id))
            .flatMap(j => LocCollection.record(j, conf.apiUrl, conf.maxYear, conf.displayPx, thumbPx).left.map(_.value))
            .left
            .map(e => MediaCollection.SearchResultPart2Part1(s"item $id: ${explain(e)}"))
        )
      )
      .left
      .map(MediaCollection.SearchResultPart1(_))

object LocCollection:
  type Date = Date.T
  object Date extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  type ThumbPx = MediaSettings.ThumbPx
  val ThumbPx: MediaSettings.ThumbPx.type = MediaSettings.ThumbPx

  def searchUrl(base: CollectionSettings.ApiUrl, name: MediaPlace.Label, limit: CollectionSettings.Candidates): Plugin.Http.GetUrl =
    Plugin.Http.GetUrl(s"${base.value.stripSuffix("/")}/maps/?q=${name.value.enc}&fo=json&c=${limit.value.max(1)}&fa=${"partof:geography and map division".enc}")

  def itemUrl(base: CollectionSettings.ApiUrl, id: MediaItem.Key): Plugin.Http.GetUrl = Plugin.Http.GetUrl(s"${base.value.stripSuffix("/")}/item/${id.value.enc}/?fo=json")

  private val ItemId = """.*/item/([^/?#]+)/?.*""".r

  /** Pure: the item id of a result's `id`/`url` (`http://www.loc.gov/item/2007627513/` → `2007627513`). */
  def itemId(url: Plugin.Http.GetUrl): Option[MediaItem.Key] = url.value match
    case ItemId(id) => Some(MediaItem.Key(id))
    case _          => None

  /** Pure: the item ids of a search answer, in its order. */
  def results(json: Json): Either[Problem, List[MediaItem.Key]] =
    val c = json.hcursor
    c.downField("results").focus.flatMap(_.asArray) match
      case None     => Left(Problem("no results in the answer"))
      case Some(rs) =>
        Right(rs.toList.flatMap { r =>
          val h = r.hcursor
          (h.str("id").toList ++ h.str("url").toList).flatMap(u => itemId(Plugin.Http.GetUrl(u))).headOption
        }.distinct)

  /** Pure: the years in a date string (`1890`, `[ca. 1880]`, `1906-1910` → its range). */
  def yearsOf(date: Date): Option[Period] =
    val ys = """(?<![0-9])([0-9]{4})(?![0-9])""".r.findAllMatchIn(date.value).map(_.group(1).toLong).toList
    ys.headOption.flatMap(_ => Period.span(ys.min, ys.max))

  private val NoKnown = "no known restrictions"

  /** Pure: one item answer. `maxYear` is the public-domain boundary; `displayPx`/`thumbPx` choose among the item's JPEG
    * files (the largest no wider than display, the smallest no narrower than thumb).
    */
  def record(json: Json, base: CollectionSettings.ApiUrl, maxYear: CollectionSettings.MaxYear, displayPx: CollectionSettings.DisplayPx, thumbPx: MediaSettings.ThumbPx): Either[Problem, MediaItem] =
    val item = json.hcursor.downField("item")
    if item.focus.isEmpty then Left(Problem("no item in the answer"))
    else
      val c = item
      val id = c
        .str("id")
        .flatMap(u => itemId(Plugin.Http.GetUrl(u)))
        .orElse(c.str("url").flatMap(u => itemId(Plugin.Http.GetUrl(u))))
        .orElse(c.str("library_of_congress_control_number").map(MediaItem.Key(_)))
      id.toRight(Problem("no item id")).map { key =>
        val title = c.str("title").getOrElse(key.value)
        val advisory = (c.strs("rights_advisory") ++ c.strs("rights")).mkString(" ")
        val date = c.str("date").orElse(c.strs("dates").headOption)
        val period = date.flatMap(d => yearsOf(Date(d)))
        val creator = c.strs("contributor_names").headOption.orElse(c.strs("creator").headOption)
        val page = s"${base.value.stripSuffix("/")}/item/${key.value}/"
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
              u <- h.str("url")
              if h.str("mimetype").exists(_.equalsIgnoreCase("image/jpeg")) ||
                u.toLowerCase(Locale.ROOT).contains(".jpg")
            yield (u, h.long("width").getOrElse(0L))
          }
        val display =
          files.filter(_._2 <= displayPx.value).sortBy(-_._2).headOption.orElse(files.sortBy(_._2).headOption).map(_._1)
        val thumb = files.filter(_._2 >= thumbPx.value).sortBy(_._2).headOption.map(_._1)
        val original = files.sortBy(-_._2).headOption.map(_._1)
        val rights: Either[MediaItem.RightsPart1, MediaRights] =
          if !advisory.toLowerCase(Locale.ROOT).contains(NoKnown) then
            Left(MediaItem.RightsPart1(if advisory.isEmpty then "no rights statement" else "rights not \"No known restrictions\""))
          else if period.isEmpty then Left(MediaItem.RightsPart1("no date on the map"))
          else if period.exists(_.end.value > maxYear.value) then Left(MediaItem.RightsPart1(s"made after ${maxYear.value} (may be in copyright)"))
          else
            Right(
              MediaRights.publicDomain(creator.map(MediaRights.Author(_)), MediaRights.Credit(credit), Some(MediaRights.LicenceUrl("https://www.loc.gov/legal/understanding-copyright/")))
            )
        MediaItem(
          key,
          MediaItem.Title(title),
          MediaItem.PageUrl(page),
          display.map(MediaItem.ImageUrl(_)),
          rights,
          thumbUrl = thumb.map(MediaItem.ThumbUrl(_)),
          originalUrl = original.map(MediaItem.OriginalUrl(_)),
          geography = c.strs("location").map(l => MediaItem.GeographyPart1("location") -> MediaItem.GeographyPart2(l)),
          period = period
        )
      }
