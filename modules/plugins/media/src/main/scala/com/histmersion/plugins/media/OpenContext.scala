package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.*

import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure

/** Open Context (https://opencontext.org/about/services), no key: its search API's JSON answer
  * (`/query/.json?type=media&cat=oc-gen-image&q=`) with the `attributes` it is asked for — each record's licence
  * (`dc-terms:license`, a Creative Commons URL), creators, subjects and spatial coverage (with their URIs: Pleiades,
  * GeoNames) — then, for a record to store, its media item (`/media/{uuid}.json`) for the files (`oc-gen:preview`
  * stored, `oc-gen:thumbnail` the card's, `oc-gen:fullfile` the original) and its licence read again.
  * `early bce/ce`/`late bce/ce` are signed years already. A record Open Context flags as related to human remains, or
  * whose subject is human remains, is not shown whatever its licence. Pure.
  */
object OpenContext:
  type Cite = Cite.T
  object Cite extends Newtype[String, Pure]
  type ContextLabel = ContextLabel.T
  object ContextLabel extends Newtype[String, Pure]
  type Project = Project.T
  object Project extends Newtype[String, Pure]
  type Segment = Segment.T
  object Segment extends Newtype[String, Pure]
  type Uuid = Uuid.T
  object Uuid extends Newtype[String, Pure]
  type Year = Year.T
  object Year extends Newtype[String, Pure]

  type Attributes = AttributesValue
  type AttributesValue = AttributesValue.T
  object AttributesValue extends Newtype[String, Pure]

  val Attributes: Attributes =
    AttributesValue(List("dc-terms-license", "dc-terms-spatial", "dc-terms-creator", "dc-terms-subject").mkString(","))

  /** The record fields finer than a country: the context path below its first two levels. */
  val Context: MediaItem.Geography.Field = MediaItem.Geography.Field("context")
  val Region: MediaItem.Geography.Field = MediaItem.Geography.Field("region")
  val Spatial: MediaItem.Geography.Field = MediaItem.Geography.Field("dc-terms:spatial")

  def searchUrl(
      api: CollectionSettings.ApiUrl,
      name: MediaPlace.Label,
      rows: CollectionSettings.Candidates
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${api.value.stripSuffix("/")}/query/.json?type=media&cat=oc-gen-image&q=${name.value.enc}&rows=${rows.value.max(1)}" +
        s"&attributes=${Attributes.value.enc}&add-attribute-uris=1&response=${"metadata,uri-meta".enc}"
    )

  def itemUrl(api: CollectionSettings.ApiUrl, uuid: Uuid): Plugin.Http.Url =
    Plugin.Http.Url(s"${api.value.stripSuffix("/")}/media/${uuid.value.enc}.json")

  private val UuidPattern = """.*/media/([0-9a-fA-F-]{36})/?$""".r

  /** Pure: the uuid of a media URI (`http://opencontext.org/media/084e8977-…`). */
  def uuidOf(uri: Plugin.Http.Url): Option[Uuid] = uri.value.trim match
    case UuidPattern(u) => Some(Uuid(u.toLowerCase(Locale.ROOT)))
    case _              => None

  private val HumanRemains = "human remains"
  private val Withheld = "human remains (Open Context flags them; not shown)"

  /** Pure: the short name of a licence label (`Attribution 4.0 International (CC BY 4.0)` → `CC BY 4.0`). */
  def shortLicence(label: Licence, id: Licence.Read.Id): Licence =
    """\(([^()]+)\)\s*$""".r
      .findFirstMatchIn(label.value)
      .map(m => Licence(m.group(1).trim))
      .getOrElse(if label.value.trim.nonEmpty then Licence(label.value.trim) else Licence(id.value))

  /** Pure: the records of a search answer, in its order. */
  def records(json: Json, licences: LicenceReader): Either[Problem, List[Either[Problem, MediaItem]]] =
    val c = json.cursor
    c.downField("oc-api:has-results").focus.flatMap(_.asArray) match
      case Some(rows) => Right(rows.toList.map(record(_, licences)))
      // no results: Open Context leaves the list out
      case None if c.long("totalResults").isDefined => Right(Nil)
      case None                                     =>
        Left(
          Problem.Unclassified(
            Legacy.Text(c.str("error").orElse(c.str("message")).getOrElse("no results in the answer"))
          )
        )

  /** Pure: one search record. Its rights are its own licence (or why it has none). */
  def record(json: Json, licences: LicenceReader): Either[Problem, MediaItem] =
    val c = json.cursor
    c.str("uri")
      .flatMap(u => uuidOf(Plugin.Http.Url(u)))
      .orElse(c.str("href").flatMap(u => uuidOf(Plugin.Http.Url(u))))
      .toRight(Problem.Unclassified(Legacy.Text("no media uri")))
      .map { uuid =>
        val title = c.str("label").getOrElse(uuid.value)
        val context = c.str("context label").getOrElse("")
        val project = c.str("project label")
        val creators = c.strs("Creator")
        val year = c.str("published").map(_.take(4)).filter(_.matches("[0-9]{4}"))
        val cite = c.str("citation uri")
        val page = c.str("href").getOrElse(s"https://opencontext.org/media/$uuid")
        val licUrl = c.strs("License [URI]").headOption
        val licLabel = c.strs("License").headOption.getOrElse("")
        val rights: Either[Problem, MediaRights] = licUrl match
          case None    => Left(Problem.Unclassified(Legacy.Text("no licence on the record")))
          case Some(u) =>
            val lic = licences.readUrl(LicenceReader.ReadUrlUrl(u))
            if lic.family.value == Licence.Read.Unknown then
              Left(Problem.Unclassified(Legacy.Text(s"licence not readable ($u)")))
            else
              Right(
                MediaRights(
                  Licence(u),
                  Some(MediaRights.LicenceUrl(u)),
                  Option.when(creators.nonEmpty)(MediaRights.Author(creators.mkString(", "))),
                  credit(
                    MediaItem.Title(title),
                    ContextLabel(context),
                    creators.map(MediaRights.Author(_)),
                    year.map(Year(_)),
                    project.map(Project(_)),
                    cite.map(Cite(_)),
                    shortLicence(Licence(licLabel), lic.id)
                  )
                )
              )
        val segs = context.split("/").toList.map(_.trim).filter(_.nonEmpty)
        val (regions, deeper) = segs.splitAt(2)
        // the site and its named parts; excavation units (`I`, `Lot 2631`, `Trench 94`, `2631.1`) name no place
        val geo =
          deeper.filterNot(d => unitSegment(Segment(d))).reverse.map(d => Context -> MediaItem.Geography.Value(d)) ++
            regions.reverse.map(d => Region -> MediaItem.Geography.Value(d)) ++
            c.strs("Spatial Coverage").map(d => Spatial -> MediaItem.Geography.Value(d))
        val ids = c.strs("Spatial Coverage [URI]").flatMap(u => GazetteerUri.of(GazetteerPlace.Id(u))).distinct
        val image = c.str("item category").forall(_.equalsIgnoreCase("Image media"))
        val withheld = Option.when(
          c.bool("oc-api:human-remains-related").contains(true) ||
            c.strs("Subject").exists(_.toLowerCase(Locale.ROOT).contains(HumanRemains))
        )(MediaItem.Withheld(Withheld))
        val period = Period.range(c.num("early bce/ce"), c.num("late bce/ce"))
        val thumb = c.str("thumbnail").filter(_ => image)
        // the search names only the thumbnail; the stored rendition is the item's preview ([[item]])
        MediaItem(
          MediaItem.Key(uuid.value),
          MediaItem.Title(title),
          MediaItem.PageUrl(page),
          thumb.map(MediaItem.ImageUrl(_)),
          rights,
          thumbUrl = thumb.map(MediaItem.ThumbUrl(_)),
          geography = geo,
          period = period,
          ids = ids,
          withheld = withheld
        )
      }

  private val UnitWords = Set(
    "trench",
    "locus",
    "loci",
    "area",
    "square",
    "unit",
    "lot",
    "feature",
    "seq",
    "sequence",
    "operation",
    "op",
    "context",
    "room",
    "level",
    "phase",
    "stratum",
    "basket",
    "bag",
    "field",
    "sector",
    "zone",
    "quadrant",
    "sondage",
    "grid",
    "pit",
    "test",
    "find",
    "object",
    "sample",
    "layer",
    "pottery",
    "coin",
    "bone",
    "survey",
    "surface",
    "stray"
  )
  private val Roman = "^[IVXLCDM]+$".r

  /** Pure (#2571): is a context-path segment an excavation unit or an item, not a place — a numbered or coded one
    * (`2631.1`, `Trench 42`, `A`, `I`: the live map has a place called "I"), or one named by a unit word (`Locus none
    * provided`, `Area`, `Survey`, `Stray Finds`)?
    */
  def unitSegment(seg: Segment): Boolean =
    val s = seg.value.trim
    val first = s.split("\\s+").headOption.getOrElse("").toLowerCase(Locale.ROOT).stripSuffix(".")
    s.exists(_.isDigit) || s.length <= 2 || Roman.matches(s) || UnitWords(first)

  extension (c: Cursor) private def num(k: String): Option[Long] = c.downField(k).double.map(math.round)

  /** Pure: Open Context's own citation form — creators (year) "label from context". In project. Open Context. ARK. —
    * and the licence's short name.
    */
  def credit(
      title: MediaItem.Title,
      context: ContextLabel,
      creators: List[MediaRights.Author],
      year: Option[Year],
      project: Option[Project],
      cite: Option[Cite],
      licence: Licence
  ): MediaRights.Credit =
    val who = if creators.isEmpty then "" else creators.mkString(", ") + year.fold("")(y => s" ($y)") + ". "
    val what = s"\"$title${if context.value.nonEmpty then s" from $context" else ""}\"."
    MediaRights.Credit(
      (List(who + what) ++ project.map(p => s"In $p.") ++ List("Open Context.") ++ cite.map(
        _.value + "."
      ) :+ licence.value)
        .mkString(" ")
    )

  /** Pure: a record completed from its media item — the preview to store, the thumbnail, the original — with the
    * licence read AGAIN from the item: a missing or different licence refuses it.
    */
  def item(json: Json, rec: MediaItem, licences: LicenceReader): Either[Problem, MediaItem] =
    val c = json.cursor
    val files = c.downField("oc-gen:has-files").focus.flatMap(_.asArray).toList.flatten.flatMap { f =>
      val h = f.cursor
      for t <- h.str("type"); u <- h.str("id") yield t -> u
    }
    def file(t: String) = files.find(_._1 == t).map(_._2)
    val stated = c
      .downField("dc-terms:license")
      .focus
      .flatMap(_.asArray)
      .toList
      .flatten
      .flatMap(l => l.cursor.str("id"))
    val subjects = c
      .downField("dc-terms:subject")
      .focus
      .flatMap(_.asArray)
      .toList
      .flatten
      .flatMap(l => l.cursor.str("label"))
    if !c.str("category").forall(_ == "oc-gen:image") then Left(Problem.Unclassified(Legacy.Text("not an image")))
    else if subjects.exists(_.toLowerCase(Locale.ROOT).contains(HumanRemains)) then
      Left(Problem.Unclassified(Legacy.Text(Withheld)))
    else
      rec.rights.flatMap { r =>
        val searched = licences.readUrl(LicenceReader.ReadUrlUrl(r.licence.value))
        stated.headOption.map(u => licences.readUrl(LicenceReader.ReadUrlUrl(u))) match
          case None                           => Left(Problem.Unclassified(Legacy.Text("no licence on the media item")))
          case Some(l) if l.id != searched.id =>
            Left(
              Problem.Unclassified(
                Legacy.Text(s"the media item's licence (${l.id}) differs from the search's (${searched.id})")
              )
            )
          case Some(_) =>
            file("oc-gen:preview")
              .orElse(file("oc-gen:thumbnail"))
              .toRight(Problem.Unclassified(Legacy.Text("no preview file on the media item")))
              .map { preview =>
                rec.copy(
                  imageUrl = Some(MediaItem.ImageUrl(preview)),
                  thumbUrl = file("oc-gen:thumbnail").map(MediaItem.ThumbUrl(_)).orElse(rec.thumbUrl),
                  originalUrl =
                    file("oc-gen:fullfile").orElse(file("oc-gen:ia-fullfille")).map(MediaItem.OriginalUrl(_))
                )
              }
      }
