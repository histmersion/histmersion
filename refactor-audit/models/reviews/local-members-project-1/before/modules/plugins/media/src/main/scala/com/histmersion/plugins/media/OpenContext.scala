package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.{ACursor, Json}

import java.util.Locale

/** Open Context (https://opencontext.org/about/services), no key: its search API's JSON answer
  * (`/query/.json?type=media&cat=oc-gen-image&q=`) with the `attributes` it is asked for — each record's licence
  * (`dc-terms:license`, a Creative Commons URL), creators, subjects and spatial coverage (with their URIs: Pleiades,
  * GeoNames) — then, for a record to store, its media item (`/media/{uuid}.json`) for the files (`oc-gen:preview`
  * stored, `oc-gen:thumbnail` the card's, `oc-gen:fullfile` the original) and its licence read again.
  * `early bce/ce`/`late bce/ce` are signed years already. A record Open Context flags as related to human remains, or
  * whose subject is human remains, is not shown whatever its licence. Pure.
  */
object OpenContext:

  type Attributes = Attributes.T
  object Attributes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlResult = SearchUrlResult.T
  object SearchUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlApi = SearchUrlApi.T
  object SearchUrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlName = SearchUrlName.T
  object SearchUrlName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlRows = SearchUrlRows.T
  object SearchUrlRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemUrlResult = ItemUrlResult.T
  object ItemUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemUrlApi = ItemUrlApi.T
  object ItemUrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemUrlUuid = ItemUrlUuid.T
  object ItemUrlUuid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UuidOfResult = UuidOfResult.T
  object UuidOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UuidOfUri = UuidOfUri.T
  object UuidOfUri extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShortLicenceResult = ShortLicenceResult.T
  object ShortLicenceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShortLicenceLabel = ShortLicenceLabel.T
  object ShortLicenceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShortLicenceId = ShortLicenceId.T
  object ShortLicenceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart1 = RecordsResultPart1.T
  object RecordsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart2Part1 = RecordsResultPart2Part1.T
  object RecordsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResultPart1 = RecordResultPart1.T
  object RecordResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitSegmentResult = UnitSegmentResult.T
  object UnitSegmentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitSegmentSeg = UnitSegmentSeg.T
  object UnitSegmentSeg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumResult = NumResult.T
  object NumResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumK = NumK.T
  object NumK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditResult = CreditResult.T
  object CreditResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditTitle = CreditTitle.T
  object CreditTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditContext = CreditContext.T
  object CreditContext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditCreators = CreditCreators.T
  object CreditCreators extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditYear = CreditYear.T
  object CreditYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditProject = CreditProject.T
  object CreditProject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditCite = CreditCite.T
  object CreditCite extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditLicence = CreditLicence.T
  object CreditLicence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemResultPart1 = ItemResultPart1.T
  object ItemResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Attributes: com.histmersion.plugins.media.OpenContext.Attributes =
    List("dc-terms-license", "dc-terms-spatial", "dc-terms-creator", "dc-terms-subject").mkString(",")

  /** The record fields finer than a country: the context path below its first two levels. */
  val Context = "context"
  val Region = "region"
  val Spatial = "dc-terms:spatial"

  def searchUrl(api: com.histmersion.plugins.media.OpenContext.SearchUrlApi, name: com.histmersion.plugins.media.OpenContext.SearchUrlName, rows: com.histmersion.plugins.media.OpenContext.SearchUrlRows): com.histmersion.plugins.media.OpenContext.SearchUrlResult =
    s"${api.stripSuffix("/")}/query/.json?type=media&cat=oc-gen-image&q=${name.enc}&rows=${rows.max(1)}" +
      s"&attributes=${Attributes.enc}&add-attribute-uris=1&response=${"metadata,uri-meta".enc}"

  def itemUrl(api: com.histmersion.plugins.media.OpenContext.ItemUrlApi, uuid: com.histmersion.plugins.media.OpenContext.ItemUrlUuid): com.histmersion.plugins.media.OpenContext.ItemUrlResult = s"${api.stripSuffix("/")}/media/${uuid.enc}.json"

  private val Uuid = """.*/media/([0-9a-fA-F-]{36})/?$""".r

  /** Pure: the uuid of a media URI (`http://opencontext.org/media/084e8977-…`). */
  def uuidOf(uri: com.histmersion.plugins.media.OpenContext.UuidOfUri): Option[com.histmersion.plugins.media.OpenContext.UuidOfResult] = uri.trim match
    case Uuid(u) => Some(u.toLowerCase(Locale.ROOT))
    case _       => None

  private val HumanRemains = "human remains"
  private val Withheld = "human remains (Open Context flags them; not shown)"

  /** Pure: the short name of a licence label (`Attribution 4.0 International (CC BY 4.0)` → `CC BY 4.0`). */
  def shortLicence(label: com.histmersion.plugins.media.OpenContext.ShortLicenceLabel, id: com.histmersion.plugins.media.OpenContext.ShortLicenceId): com.histmersion.plugins.media.OpenContext.ShortLicenceResult =
    """\(([^()]+)\)\s*$""".r
      .findFirstMatchIn(label)
      .map(_.group(1).trim)
      .getOrElse(if label.trim.nonEmpty then label.trim else id)

  /** Pure: the records of a search answer, in its order. */
  def records(json: Json, licences: LicenceReader): Either[com.histmersion.plugins.media.OpenContext.RecordsResultPart1, List[Either[com.histmersion.plugins.media.OpenContext.RecordsResultPart2Part1, MediaItem]]] =
    val c = json.hcursor
    c.downField("oc-api:has-results").focus.flatMap(_.asArray) match
      case Some(rows) => Right(rows.toList.map(record(_, licences)))
      // no results: Open Context leaves the list out
      case None if c.long("totalResults").isDefined => Right(Nil)
      case None => Left(c.str("error").orElse(c.str("message")).getOrElse("no results in the answer"))

  /** Pure: one search record. Its rights are its own licence (or why it has none). */
  def record(json: Json, licences: LicenceReader): Either[com.histmersion.plugins.media.OpenContext.RecordResultPart1, MediaItem] =
    val c = json.hcursor
    c.str("uri").flatMap(uuidOf).orElse(c.str("href").flatMap(uuidOf)).toRight("no media uri").map { uuid =>
      val title = c.str("label").getOrElse(uuid)
      val context = c.str("context label").getOrElse("")
      val project = c.str("project label")
      val creators = c.strs("Creator")
      val year = c.str("published").map(_.take(4)).filter(_.matches("[0-9]{4}"))
      val cite = c.str("citation uri")
      val page = c.str("href").getOrElse(s"https://opencontext.org/media/$uuid")
      val licUrl = c.strs("License [URI]").headOption
      val licLabel = c.strs("License").headOption.getOrElse("")
      val rights: Either[String, MediaRights] = licUrl match
        case None    => Left("no licence on the record")
        case Some(u) =>
          val lic = licences.readUrl(u)
          if lic.family == Licence.Read.Unknown then Left(s"licence not readable ($u)")
          else
            Right(
              MediaRights(
                Licence(u),
                Some(u),
                Option.when(creators.nonEmpty)(creators.mkString(", ")),
                credit(title, context, creators, year, project, cite, shortLicence(licLabel, lic.id))
              )
            )
      val segs = context.split("/").toList.map(_.trim).filter(_.nonEmpty)
      val (regions, deeper) = segs.splitAt(2)
      // the site and its named parts; excavation units (`I`, `Lot 2631`, `Trench 94`, `2631.1`) name no place
      val geo = deeper.filterNot(unitSegment).reverse.map(Context -> _) ++ regions.reverse.map(Region -> _) ++
        c.strs("Spatial Coverage").map(Spatial -> _)
      val ids = c.strs("Spatial Coverage [URI]").flatMap(GazetteerUri.of).distinct
      val image = c.str("item category").forall(_.equalsIgnoreCase("Image media"))
      val withheld = Option.when(
        c.bool("oc-api:human-remains-related").contains(true) ||
          c.strs("Subject").exists(_.toLowerCase(Locale.ROOT).contains(HumanRemains))
      )(Withheld)
      val period = Period.range(num(c, "early bce/ce"), num(c, "late bce/ce"))
      val thumb = c.str("thumbnail").filter(_ => image)
      // the search names only the thumbnail; the stored rendition is the item's preview ([[item]])
      MediaItem(
        uuid,
        title,
        page,
        thumb,
        rights,
        thumbUrl = thumb,
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
  def unitSegment(seg: com.histmersion.plugins.media.OpenContext.UnitSegmentSeg): com.histmersion.plugins.media.OpenContext.UnitSegmentResult =
    val s = seg.trim
    val first = s.split("\\s+").headOption.getOrElse("").toLowerCase(Locale.ROOT).stripSuffix(".")
    s.exists(_.isDigit) || s.length <= 2 || Roman.matches(s) || UnitWords(first)

  private def num(c: ACursor, k: com.histmersion.plugins.media.OpenContext.NumK): Option[com.histmersion.plugins.media.OpenContext.NumResult] =
    c.downField(k)
      .focus
      .flatMap(j => j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))
      .map(d => math.round(d))

  /** Pure: Open Context's own citation form — creators (year) "label from context". In project. Open Context. ARK. —
    * and the licence's short name.
    */
  def credit(
      title: com.histmersion.plugins.media.OpenContext.CreditTitle,
      context: com.histmersion.plugins.media.OpenContext.CreditContext,
      creators: List[com.histmersion.plugins.media.OpenContext.CreditCreators],
      year: Option[com.histmersion.plugins.media.OpenContext.CreditYear],
      project: Option[com.histmersion.plugins.media.OpenContext.CreditProject],
      cite: Option[com.histmersion.plugins.media.OpenContext.CreditCite],
      licence: com.histmersion.plugins.media.OpenContext.CreditLicence
  ): com.histmersion.plugins.media.OpenContext.CreditResult =
    val who = if creators.isEmpty then "" else creators.mkString(", ") + year.fold("")(y => s" ($y)") + ". "
    val what = s"\"$title${if context.nonEmpty then s" from $context" else ""}\"."
    (List(who + what) ++ project.map(p => s"In $p.") ++ List("Open Context.") ++ cite.map(_ + ".") :+ licence)
      .mkString(" ")

  /** Pure: a record completed from its media item — the preview to store, the thumbnail, the original — with the
    * licence read AGAIN from the item: a missing or different licence refuses it.
    */
  def item(json: Json, rec: MediaItem, licences: LicenceReader): Either[com.histmersion.plugins.media.OpenContext.ItemResultPart1, MediaItem] =
    val c = json.hcursor
    val files = c.downField("oc-gen:has-files").focus.flatMap(_.asArray).toList.flatten.flatMap { f =>
      val h = f.hcursor
      for t <- h.str("type"); u <- h.str("id") yield t -> u
    }
    def file(t: String) = files.find(_._1 == t).map(_._2)
    val stated = c
      .downField("dc-terms:license")
      .focus
      .flatMap(_.asArray)
      .toList
      .flatten
      .flatMap(l => l.hcursor.str("id"))
    val subjects = c
      .downField("dc-terms:subject")
      .focus
      .flatMap(_.asArray)
      .toList
      .flatten
      .flatMap(l => l.hcursor.str("label"))
    if !c.str("category").forall(_ == "oc-gen:image") then Left("not an image")
    else if subjects.exists(_.toLowerCase(Locale.ROOT).contains(HumanRemains)) then Left(Withheld)
    else
      rec.rights.flatMap { r =>
        val searched = licences.readUrl(r.licence.value)
        stated.headOption.map(licences.readUrl) match
          case None                           => Left("no licence on the media item")
          case Some(l) if l.id != searched.id =>
            Left(s"the media item's licence (${l.id}) differs from the search's (${searched.id})")
          case Some(_) =>
            file("oc-gen:preview").orElse(file("oc-gen:thumbnail")).toRight("no preview file on the media item").map {
              preview =>
                rec.copy(
                  imageUrl = Some(preview),
                  thumbUrl = file("oc-gen:thumbnail").orElse(rec.thumbUrl),
                  originalUrl = file("oc-gen:fullfile").orElse(file("oc-gen:ia-fullfille"))
                )
            }
      }
