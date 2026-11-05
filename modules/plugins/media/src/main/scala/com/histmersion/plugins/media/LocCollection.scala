package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.Json

import java.util.Locale

/** `loc`: the Library of Congress JSON API (https://www.loc.gov/apis/json-and-yaml/), no key: the maps format
  * (`/maps/?q=&fo=json`) narrowed to the Geography and Map Division, then each candidate's item (`/item/{id}/?fo=json`)
  * for its rights and files. A map is taken as public domain only when BOTH its rights advisory says "No known
  * restrictions" and its date is no later than `max-year` (1928: US works published before 1929). Its `location` list
  * is where it is of. A 403 is loc.gov's bot challenge to automated clients — reported as such, never worked around.
  */
final class LocCollection(conf: CollectionSettings, thumbPx: Int)
    extends MuseumCollection(conf, "Library of Congress", "media.loc"):
  val title = "Library of Congress maps"
  override val description =
    "Historical maps from the Library of Congress, Geography and Map Division: public domain only (rights " +
      "'No known restrictions' and dated by media.loc.max-year), linked as role map to the places their " +
      "locations name"
  val role = MediaCollection.Map
  override def matchRule = MediaMatchRule.Map(Year(conf.maxYear))

  private def explain(e: com.histmersion.plugins.media.LocCollection.ExplainE): com.histmersion.plugins.media.LocCollection.ExplainResult =
    if e.contains("HTTP 403") then
      s"$e — loc.gov refuses automated clients with a bot challenge at the moment; not worked around"
    else e

  def search(place: MediaPlace, http: Plugin.Http): Either[com.histmersion.plugins.media.LocCollection.SearchResultPart1, List[Either[com.histmersion.plugins.media.LocCollection.SearchResultPart2Part1, MediaItem]]] =
    http
      .askJson(LocCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(LocCollection.results)
      .left
      .map(explain)
      .map(
        _.take(conf.candidates.max(0)).map(id =>
          http
            .askJson(LocCollection.itemUrl(conf.apiUrl, id))
            .flatMap(LocCollection.record(_, conf.apiUrl, conf.maxYear, conf.displayPx, thumbPx))
            .left
            .map(e => s"item $id: ${explain(e)}")
        )
      )

object LocCollection:

  type ExplainResult = ExplainResult.T
  object ExplainResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExplainE = ExplainE.T
  object ExplainE extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart2Part1 = SearchResultPart2Part1.T
  object SearchResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlResult = SearchUrlResult.T
  object SearchUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlBase = SearchUrlBase.T
  object SearchUrlBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlName = SearchUrlName.T
  object SearchUrlName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlLimit = SearchUrlLimit.T
  object SearchUrlLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemUrlResult = ItemUrlResult.T
  object ItemUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemUrlBase = ItemUrlBase.T
  object ItemUrlBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemUrlId = ItemUrlId.T
  object ItemUrlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemIdResult = ItemIdResult.T
  object ItemIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemIdUrl = ItemIdUrl.T
  object ItemIdUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResultsResultPart1 = ResultsResultPart1.T
  object ResultsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResultsResultPart2 = ResultsResultPart2.T
  object ResultsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearsOfDate = YearsOfDate.T
  object YearsOfDate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResultPart1 = RecordResultPart1.T
  object RecordResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordBase = RecordBase.T
  object RecordBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordMaxYear = RecordMaxYear.T
  object RecordMaxYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordDisplayPx = RecordDisplayPx.T
  object RecordDisplayPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordThumbPx = RecordThumbPx.T
  object RecordThumbPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def searchUrl(base: com.histmersion.plugins.media.LocCollection.SearchUrlBase, name: com.histmersion.plugins.media.LocCollection.SearchUrlName, limit: com.histmersion.plugins.media.LocCollection.SearchUrlLimit): com.histmersion.plugins.media.LocCollection.SearchUrlResult =
    s"${base.stripSuffix("/")}/maps/?q=${name.enc}&fo=json&c=${limit.max(1)}&fa=${"partof:geography and map division".enc}"

  def itemUrl(base: com.histmersion.plugins.media.LocCollection.ItemUrlBase, id: com.histmersion.plugins.media.LocCollection.ItemUrlId): com.histmersion.plugins.media.LocCollection.ItemUrlResult = s"${base.stripSuffix("/")}/item/${id.enc}/?fo=json"

  private val ItemId = """.*/item/([^/?#]+)/?.*""".r

  /** Pure: the item id of a result's `id`/`url` (`http://www.loc.gov/item/2007627513/` → `2007627513`). */
  def itemId(url: com.histmersion.plugins.media.LocCollection.ItemIdUrl): Option[com.histmersion.plugins.media.LocCollection.ItemIdResult] = url match
    case ItemId(id) => Some(id)
    case _          => None

  /** Pure: the item ids of a search answer, in its order. */
  def results(json: Json): Either[com.histmersion.plugins.media.LocCollection.ResultsResultPart1, List[com.histmersion.plugins.media.LocCollection.ResultsResultPart2]] =
    val c = json.hcursor
    c.downField("results").focus.flatMap(_.asArray) match
      case None     => Left("no results in the answer")
      case Some(rs) =>
        Right(rs.toList.flatMap { r =>
          val h = r.hcursor
          (h.str("id").toList ++ h.str("url").toList).flatMap(itemId).headOption
        }.distinct)

  /** Pure: the years in a date string (`1890`, `[ca. 1880]`, `1906-1910` → its range). */
  def yearsOf(date: com.histmersion.plugins.media.LocCollection.YearsOfDate): Option[Period] =
    val ys = """(?<![0-9])([0-9]{4})(?![0-9])""".r.findAllMatchIn(date).map(_.group(1).toLong).toList
    ys.headOption.flatMap(_ => Period.span(ys.min, ys.max))

  private val NoKnown = "no known restrictions"

  /** Pure: one item answer. `maxYear` is the public-domain boundary; `displayPx`/`thumbPx` choose among the item's JPEG
    * files (the largest no wider than display, the smallest no narrower than thumb).
    */
  def record(json: Json, base: com.histmersion.plugins.media.LocCollection.RecordBase, maxYear: com.histmersion.plugins.media.LocCollection.RecordMaxYear, displayPx: com.histmersion.plugins.media.LocCollection.RecordDisplayPx, thumbPx: com.histmersion.plugins.media.LocCollection.RecordThumbPx): Either[com.histmersion.plugins.media.LocCollection.RecordResultPart1, MediaItem] =
    val item = json.hcursor.downField("item")
    if item.focus.isEmpty then Left("no item in the answer")
    else
      val c = item
      val id = c
        .str("id")
        .flatMap(itemId)
        .orElse(c.str("url").flatMap(itemId))
        .orElse(c.str("library_of_congress_control_number"))
      id.toRight("no item id").map { key =>
        val title = c.str("title").getOrElse(key)
        val advisory = (c.strs("rights_advisory") ++ c.strs("rights")).mkString(" ")
        val date = c.str("date").orElse(c.strs("dates").headOption)
        val period = date.flatMap(yearsOf)
        val creator = c.strs("contributor_names").headOption.orElse(c.strs("creator").headOption)
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
              u <- h.str("url")
              if h.str("mimetype").exists(_.equalsIgnoreCase("image/jpeg")) ||
                u.toLowerCase(Locale.ROOT).contains(".jpg")
            yield (u, h.long("width").getOrElse(0L))
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
            Right(
              MediaRights.publicDomain(creator, credit, Some("https://www.loc.gov/legal/understanding-copyright/"))
            )
        MediaItem(
          key,
          title,
          page,
          display,
          rights,
          thumbUrl = thumb,
          originalUrl = original,
          geography = c.strs("location").map("location" -> _),
          period = period
        )
      }
