package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*

import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** `loc`: the Library of Congress JSON API (https://www.loc.gov/apis/json-and-yaml/), no key: the maps format
  * (`/maps/?q=&fo=json`) narrowed to the Geography and Map Division, then each candidate's item (`/item/{id}/?fo=json`)
  * for its rights and files. A map is taken as public domain only when BOTH its rights advisory says "No known
  * restrictions" and its date is no later than `max-year` (1928: US works published before 1929). Its `location` list
  * is where it is of. A 403 is loc.gov's bot challenge to automated clients — reported as such, never worked around.
  */
@atBoundary(BoundaryKind.Json, "reads the loc collection collection's JSON answers into media records")
final class LocCollection(conf: CollectionSettings, thumbPx: LocCollection.ThumbPx)
    extends MuseumCollection(conf, MuseumCollection.Provider("Library of Congress"), MuseumCollection.Key("media.loc")):
  val title = MediaCollection.Title("Library of Congress maps")
  override val description =
    MediaCollection.Description(
      "Historical maps from the Library of Congress, Geography and Map Division: public domain only (rights " +
        "'No known restrictions' and dated by media.loc.max-year), linked as role map to the places their " +
        "locations name"
    )
  val role = MediaCollection.Map
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  override def matchRule = MediaMatchRule.Map(Year(conf.maxYear.value))

  /** loc.gov refuses automated clients with a bot challenge at the moment (a 403): said so, not worked around. */
  private def explain(e: Problem): Problem = e match
    case Problem.Upstream.Http(name, url, status, _, at) if status == Problem.Upstream.Status.Forbidden =>
      Problem.Upstream.Challenged(name, url, status, at)
    case other => other

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def search(
      place: MediaPlace,
      http: Plugin.Http
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    http
      .askJson(LocCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(j => LocCollection.results(j).unreadable)
      .left
      .map(explain)
      .map(
        _.take(conf.candidates.value.max(0)).map(id =>
          http
            .askJson(LocCollection.itemUrl(conf.apiUrl, id))
            .flatMap(j => LocCollection.record(j, conf.apiUrl, conf.maxYear, conf.displayPx, thumbPx).unreadable)
            .left
            .map(e => explain(e).within(Problem.Field(s"item $id")))
        )
      )

object LocCollection:

  /** The plugin's id: what its problems name. */
  val Name = "media-loc"
  type Date = Date.T
  object Date extends Newtype[String, Pure]

  export MediaSettings.ThumbPx

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def searchUrl(
      base: CollectionSettings.ApiUrl,
      name: MediaPlace.Label,
      limit: CollectionSettings.Candidates
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${base.value.stripSuffix("/")}/maps/?q=${name.value.enc}&fo=json&c=${limit.value.max(1)}&fa=${"partof:geography and map division".enc}"
    )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def itemUrl(base: CollectionSettings.ApiUrl, id: MediaItem.Key): Plugin.Http.Url =
    Plugin.Http.Url(s"${base.value.stripSuffix("/")}/item/${id.value.enc}/?fo=json")

  private val ItemId = """.*/item/([^/?#]+)/?.*""".r

  /** Pure: the item id of a result's `id`/`url` (`http://www.loc.gov/item/2007627513/` → `2007627513`). */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def itemId(url: Plugin.Http.Url): Option[MediaItem.Key] = url.value match
    case ItemId(id) => Some(MediaItem.Key(id))
    case _          => None

  /** Pure: the item ids of a search answer, in its order. */
  def results(json: Json): Either[Problem, List[MediaItem.Key]] =
    val c = json.cursor
    c.downField("results").focus.flatMap(_.asArray) match
      case None     => Left(MediaPlugin.missing("results"))
      case Some(rs) =>
        Right(rs.toList.flatMap { r =>
          val h = r.cursor
          (h.str("id").toList ++ h.str("url").toList).flatMap(u => itemId(Plugin.Http.Url(u))).headOption
        }.distinct)

  /** Pure: the years in a date string (`1890`, `[ca. 1880]`, `1906-1910` → its range). */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def yearsOf(date: Date): Option[Period] =
    val ys = """(?<![0-9])([0-9]{4})(?![0-9])""".r.findAllMatchIn(date.value).map(_.group(1).toLong).toList
    ys.headOption.flatMap(_ => Period.span(ys.min, ys.max))

  private val NoKnown = "no known restrictions"

  /** Pure: one item answer. `maxYear` is the public-domain boundary; `displayPx`/`thumbPx` choose among the item's JPEG
    * files (the largest no wider than display, the smallest no narrower than thumb).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def record(
      json: Json,
      base: CollectionSettings.ApiUrl,
      maxYear: CollectionSettings.MaxYear,
      displayPx: CollectionSettings.DisplayPx,
      thumbPx: MediaSettings.ThumbPx
  ): Either[Problem, MediaItem] =
    val item = json.cursor.downField("item")
    if item.focus.isEmpty then Left(MediaPlugin.missing("item"))
    else
      val c = item
      val id = c
        .str("id")
        .flatMap(u => itemId(Plugin.Http.Url(u)))
        .orElse(c.str("url").flatMap(u => itemId(Plugin.Http.Url(u))))
        .orElse(c.str("library_of_congress_control_number").map(MediaItem.Key(_)))
      id.toRight(MediaPlugin.missing("id", "item")).map { key =>
        val title = c.str("title").getOrElse(key.value)
        val advisory = (c.strs("rights_advisory") ++ c.strs("rights")).mkString(" ")
        val date = c.str("date").orElse(c.strs("dates").headOption)
        val period = date.flatMap(d => yearsOf(Date(d)))
        val creator = c.strs("contributor_names").headOption.orElse(c.strs("creator").headOption)
        val page = s"${base.value.stripSuffix("/")}/item/${key.value}/"
        val credit =
          (List(Some(title), creator).flatten :+ "Library of Congress, Geography and Map Division").mkString(", ")
        val files = json.cursor
          .downField("resources")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .headOption
          .toList
          .flatMap(_.cursor.downField("files").focus.flatMap(_.asArray).toList.flatten)
          .flatMap(_.asArray.toList.flatten)
          .flatMap { f =>
            val h = f.cursor
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
        val rights: Either[Problem, MediaRights] =
          if !advisory.toLowerCase(Locale.ROOT).contains(NoKnown) then
            Left(
              MediaPlugin.rejected(
                Name,
                s"$key",
                if advisory.isEmpty then Problem.Plugin.Refusal.NoLicence
                else MediaPlugin.notOpen(Problem.Field("rights_advisory"), Some(advisory))
              )
            )
          else if period.isEmpty then Left(MediaPlugin.rejected(Name, s"$key", Problem.Plugin.Refusal.NoDate))
          else if period.exists(_.end.value > maxYear.value) then
            Left(
              MediaPlugin
                .said(Name, Problem.Plugin.Claim.Rejected, s"made after ${maxYear.value} (may be in copyright)")
            )
          else
            Right(
              MediaRights.publicDomain(
                creator.map(MediaRights.Author(_)),
                MediaRights.Credit(credit),
                Some(MediaRights.LicenceUrl("https://www.loc.gov/legal/understanding-copyright/"))
              )
            )
        MediaItem(
          key,
          MediaItem.Title(title),
          MediaItem.PageUrl(page),
          display.map(MediaItem.ImageUrl(_)),
          rights,
          thumbUrl = thumb.map(MediaItem.ThumbUrl(_)),
          originalUrl = original.map(MediaItem.OriginalUrl(_)),
          geography =
            c.strs("location").map(l => MediaItem.Geography.Field("location") -> MediaItem.Geography.Value(l)),
          period = period
        )
      }
