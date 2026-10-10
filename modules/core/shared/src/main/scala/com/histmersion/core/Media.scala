package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

object AssetKind:
  val Figure = "figure"
  val Photo  = "photo"
  val Map    = "map"
  val Plan   = "plan"
  val Scan   = "scan"
  val all: List[String] = List(Figure, Photo, Map, Plan, Scan)

object AssetRelation:
  val Depicts     = "depicts"
  val TakenAt     = "taken_at"
  val Illustrates = "illustrates"
  val PageOf      = "page_of"
  val all: List[String] = List(Depicts, TakenAt, Illustrates, PageOf)

object AssetLinkTarget:
  val Event   = "event"
  val Finding = "finding"
  val Place   = "place"
  val Source  = "source"
  val Article = "article"
  val all: List[String] = List(Event, Finding, Place, Source, Article)

final case class AssetPatch(
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
)

object AssetPatch:
  given Schema[AssetPatch] = KindlingsSchema.derived[AssetPatch].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[AssetPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[AssetPatch])

final case class AssetDetail(
    name: String,
    mime: String,
    bytes: Int,
    sha256: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject

object AssetDetail:
  given Schema[AssetDetail] = KindlingsSchema.derived[AssetDetail].schema

final case class AssetLink(
    id: Long,
    citekey: String,
    assetName: String,
    relation: String,
    targetType: String,
    targetId: String,
    xywh: Option[String] = None,
    notes: Option[String] = None
) derives KindlingsCodecAsObject

object AssetLink:
  given Schema[AssetLink] = KindlingsSchema.derived[AssetLink].schema

final case class AssetLinkPut(
    citekey: String,
    assetName: String,
    relation: String,
    targetType: String,
    targetId: String,
    xywh: Option[String] = None,
    notes: Option[String] = None
) derives KindlingsCodecAsObject

object AssetLinkPut:
  given Schema[AssetLinkPut] = KindlingsSchema.derived[AssetLinkPut].schema

final case class AssetLinkList(
    total: Int,
    links: List[AssetLink]
) derives KindlingsCodecAsObject

object AssetLinkList:
  given Schema[AssetLinkList] = KindlingsSchema.derived[AssetLinkList].schema

final case class AssetLinkDeleted(
    id: Long,
    deleted: Boolean
) derives KindlingsCodecAsObject

object AssetLinkDeleted:
  given Schema[AssetLinkDeleted] = KindlingsSchema.derived[AssetLinkDeleted].schema

/** One geolocated picture on the media layer: a source's figure (`citekey` set) or a provider's
  * picture of a place or an event (`provider` set, `citekey` empty) — then with its licence, credit
  * line and the local thumbnail URL the marker's popup shows. */
final case class MediaGeoHit(
    citekey: Option[String],
    assetName: String,
    kind: Option[String],
    lat: Double,
    lon: Double,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    caption: Option[String],
    uid: String = "",
    provider: Option[String] = None,
    license: Option[String] = None,
    credit: Option[String] = None,
    thumbUrl: Option[String] = None
) derives KindlingsCodecAsObject

object MediaGeoHit:
  given Schema[MediaGeoHit] = KindlingsSchema.derived[MediaGeoHit].schema

// ── media from outside providers (#1446) ────────────────────────────────────────────────────────

/** Where a picture comes from. A source's figures are `source`; every outside provider has its own
  * name, which is also its config block (`media.commons`) and the media layer's provider filter. */
object MediaProvider:
  val Source  = "source"
  val Commons = "wikimedia-commons"
  val all: List[String] = List(Source, Commons)

  /** What to call a provider on a card or in a menu. */
  def title(p: String): String = p match
    case Source  => "source figures"
    case Commons => "Wikimedia Commons"
    case other   => other

/** What a provider's picture is OF, for its place or event: the item's own image, a locator map, a
  * plan, an aerial view, a route map. The card shows the first by this order. */
object MediaRole:
  val Image      = "image"
  val LocatorMap = "locator-map"
  val Plan       = "plan"
  val AerialView = "aerial-view"
  val RouteMap   = "route-map"
  val all: List[String] = List(Image, AerialView, Plan, LocatorMap, RouteMap)

  /** Pure: the rank of a role on a card (unknown roles last). */
  def order(role: String): Int = all.indexOf(role) match
    case -1 => all.size
    case i  => i

/** `P18:image,P242:locator-map` (config) → `List(P18 -> image, P242 -> locator-map)`. An entry that
  * is not `P<digits>:<known role>` is dropped, as is a second entry for the same property. */
object MediaRoles:
  def parse(s: String): List[(String, String)] =
    Option(s).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).flatMap { e =>
      e.split(":", 2).map(_.trim) match
        case Array(p, r) if p.matches("P[1-9][0-9]*") && MediaRole.all.contains(r) => Some(p -> r)
        case _                                                                     => None
    }.distinctBy(_._1)

/** The thumbnail a place or event card shows, with what its licence requires beside it: the credit
  * line, the licence (SPDX id or `public-domain`) and where to read it, and the page the picture
  * comes from. `url` is this server's own (`/media/{uid}/thumb`) — a hover never reaches outside. */
final case class MediaThumb(
    uid: String,
    role: String,
    url: String,
    credit: String,
    license: Option[String] = None,
    licenseUrl: Option[String] = None,
    sourceUrl: Option[String] = None,
    width: Option[Int] = None,
    height: Option[Int] = None
) derives KindlingsCodecAsObject

object MediaThumb:
  given Schema[MediaThumb] = KindlingsSchema.derived[MediaThumb].schema
  /** The local URLs of a stored picture's thumbnail and rendition. */
  def thumbPath(uid: String): String = s"/media/$uid/thumb"
  def filePath(uid: String): String  = s"/media/$uid/file"

/** A place or event a provider's picture is linked to, in its role, from which statement of which
  * item (`P18` of `Q132564`). */
final case class MediaTarget(
    targetType: String,
    targetId: String,
    role: String,
    property: Option[String] = None,
    item: Option[String] = None
) derives KindlingsCodecAsObject

object MediaTarget:
  given Schema[MediaTarget] = KindlingsSchema.derived[MediaTarget].schema

/** One stored picture, whichever its origin: a source's figure (`citekey`) or a provider's
  * (`provider`, `providerKey`), with its licence and credit and what it is linked to. */
final case class MediaItem(
    uid: String,
    name: String,
    mime: String,
    bytes: Int,
    citekey: Option[String] = None,
    provider: Option[String] = None,
    providerKey: Option[String] = None,
    width: Option[Int] = None,
    height: Option[Int] = None,
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    licenseUrl: Option[String] = None,
    author: Option[String] = None,
    credit: Option[String] = None,
    sourceUrl: Option[String] = None,
    originalUrl: Option[String] = None,
    retrievedAt: Option[String] = None,
    thumbUrl: Option[String] = None,
    fileUrl: String = "",
    targets: List[MediaTarget] = Nil
) derives KindlingsCodecAsObject

object MediaItem:
  given Schema[MediaItem] = KindlingsSchema.derived[MediaItem].schema

final case class MediaList(total: Int, items: List[MediaItem]) derives KindlingsCodecAsObject
object MediaList:
  given Schema[MediaList] = KindlingsSchema.derived[MediaList].schema

/** A count by key (a provider, a licence, a role, a reason); `allowed = false` marks a licence the
  * provider's allowlist refuses. */
final case class MediaCount(key: String, n: Int, allowed: Boolean = true) derives KindlingsCodecAsObject
object MediaCount:
  given Schema[MediaCount] = KindlingsSchema.derived[MediaCount].schema
  /** Pure: count `keys`, most frequent first, then by key. */
  def of(keys: List[String], allowed: String => Boolean = _ => true): List[MediaCount] =
    keys.groupBy(identity).toList.map((k, v) => MediaCount(k, v.size, allowed(k)))
      .sortBy(c => (-c.n, c.key))

/** `POST /api/media/import`: ask a provider about places and events. `dryRun` (the default) reads
  * the provider's statements and licences and stores NOTHING; `place`/`event` narrow it to one;
  * `refresh` asks again about ones already asked (and re-reads the licences of what is stored). */
final case class MediaImportRequest(
    provider: String = "wikidata",
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    place: Option[String] = None,
    event: Option[String] = None,
    refresh: Boolean = false
) derives KindlingsCodecAsObject

object MediaImportRequest:
  given Schema[MediaImportRequest] = KindlingsSchema.derived[MediaImportRequest].schema

/** What an import found and did. The counts per licence come BEFORE anything is stored — a dry run
  * is exactly this report with `stored = updated = linked = 0`.
  *
  * @param targets    places and events asked about
  * @param items      Wikidata items read
  * @param statements picture statements found on them (P18, P242, …), after the per-property cap
  * @param files      distinct files those statements name
  * @param byLicence  files per licence (SPDX id or `public-domain`), `allowed = false` for the refused
  * @param rejected   files not stored, per reason (licence not allowed, no author to credit, missing…)
  * @param revoked    stored pictures whose licence a re-read no longer allows (kept; listed to review) */
final case class MediaImportReport(
    provider: String,
    dryRun: Boolean,
    targets: Int = 0,
    items: Int = 0,
    statements: Int = 0,
    files: Int = 0,
    byLicence: List[MediaCount] = Nil,
    byRole: List[MediaCount] = Nil,
    rejected: List[MediaCount] = Nil,
    stored: Int = 0,
    updated: Int = 0,
    linked: Int = 0,
    categories: Int = 0,
    revoked: List[String] = Nil,
    failed: List[String] = Nil
) derives KindlingsCodecAsObject

object MediaImportReport:
  given Schema[MediaImportReport] = KindlingsSchema.derived[MediaImportReport].schema

/** What the media layer can be filtered by: pictures per provider and per licence (`none` = no
  * licence recorded). */
final case class MediaFacets(providers: List[MediaCount], licences: List[MediaCount]) derives KindlingsCodecAsObject
object MediaFacets:
  given Schema[MediaFacets] = KindlingsSchema.derived[MediaFacets].schema

/** `POST /api/media/fill-licences`: a source's figures take the source's licence where the source
  * records one and the figure has none. Without `apply` it only counts. */
final case class FigureLicenceFill(candidates: Int, updated: Int, applied: Boolean, bySource: List[MediaCount] = Nil)
    derives KindlingsCodecAsObject
object FigureLicenceFill:
  given Schema[FigureLicenceFill] = KindlingsSchema.derived[FigureLicenceFill].schema
