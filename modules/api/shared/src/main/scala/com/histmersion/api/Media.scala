package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

object AssetKind:
  val Figure = "figure"
  val Photo = "photo"
  val Map = "map"
  val Plan = "plan"
  val Scan = "scan"
  val all: List[String] = List(Figure, Photo, Map, Plan, Scan)

object AssetRelation:
  val Depicts = "depicts"
  val TakenAt = "taken_at"
  val Illustrates = "illustrates"
  val PageOf = "page_of"
  val all: List[String] = List(Depicts, TakenAt, Illustrates, PageOf)

object AssetLinkTarget:
  val Event = "event"
  val Finding = "finding"
  val Place = "place"
  val Source = "source"
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
    links: List[AssetLink],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject:
  def page: OffsetPage = OffsetPage(total, offset, links.size)

object AssetLinkList:
  given Schema[AssetLinkList] = KindlingsSchema.derived[AssetLinkList].schema
  def of(total: Int, rows: List[AssetLink], offset: Int): AssetLinkList =
    AssetLinkList(total, rows, offset, OffsetPage(total, offset, rows.size).next)

final case class AssetLinkDeleted(
    id: Long,
    deleted: Boolean
) derives KindlingsCodecAsObject

object AssetLinkDeleted:
  given Schema[AssetLinkDeleted] = KindlingsSchema.derived[AssetLinkDeleted].schema

/** One geolocated picture on the media layer: a source's figure (`citekey` set) or a provider's picture of a place or
  * an event (`provider` set, `citekey` empty) — then with its licence, credit line and the local thumbnail URL the
  * marker's popup shows. `fileUrl` is the stored picture at its full stored size (the capped rendition of a provider's
  * picture: this server's own, [[MediaThumb.filePath]]); `sourceUrl` the provider's page of it, `licenseUrl` its
  * licence's text, `author` who made it — what a full-size view shows beside it.
  */
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
    thumbUrl: Option[String] = None,
    fileUrl: Option[String] = None,
    sourceUrl: Option[String] = None,
    licenseUrl: Option[String] = None,
    author: Option[String] = None
) derives KindlingsCodecAsObject

object MediaGeoHit:
  given Schema[MediaGeoHit] = KindlingsSchema.derived[MediaGeoHit].schema

// ── media from outside providers (#1446) ────────────────────────────────────────────────────────

/** Where a picture comes from. A source's figures are `source`; every outside provider has its own name, which is also
  * its config block (`media.commons`) and the media layer's provider filter.
  */
object MediaProvider:
  val Source = "source"
  val Commons = "wikimedia-commons"

  /** A book's cover from the user's own Calibre library (read host-side by the CLI, uploaded). */
  val Calibre = "calibre"

  /** A book's cover from the Open Library Covers API, by ISBN or OCLC number. */
  val OpenLibrary = "open-library"

  /** Open Access objects of museum collections (CC0), tied to a place by find-spot and dates. */
  val Met = "met-museum"
  val Cleveland = "cleveland-museum"
  val ArtInstitute = "art-institute-chicago"

  /** Public-domain historical maps of the Library of Congress's Geography and Map Division. */
  val LibraryOfCongress = "library-of-congress"

  /** Not an asset provider: what the depictions import (Commons files that depict an event) records its checks and
    * reports under — its pictures are `wikimedia-commons` assets.
    */
  val CommonsDepicts = "wikimedia-commons-depicts"

  /** Open Context's media (photographs and drawings of excavated sites and finds), each under its own record's Creative
    * Commons licence, tied to a place by a shared gazetteer id or its site's name.
    */
  val OpenContext = "open-context"

  /** Not an asset provider: what the Pleiades import (Commons files a Pleiades place record links) records its checks
    * and reports under — its pictures are `wikimedia-commons` assets.
    */
  val Pleiades = "pleiades"

  /** Smithsonian Open Access objects whose image is CC0 (an api.data.gov key; off by default). */
  val Smithsonian = "smithsonian"

  /** Europeana's openly licensed images (CC0, PDM, CC BY, CC BY-SA; a personal key; off by default). */
  val Europeana = "europeana"
  val all: List[String] = List(
    Source,
    Commons,
    Calibre,
    OpenLibrary,
    Met,
    Cleveland,
    ArtInstitute,
    LibraryOfCongress,
    OpenContext,
    Smithsonian,
    Europeana
  )

  /** The museum collections: an object's picture, tied to a place by find-spot and dates. */
  val museums: Set[String] = Set(Met, Cleveland, ArtInstitute)

  /** Covers: bibliographic pictures of the user's own books, shown in the local UI only. They carry no reuse licence
    * (the publisher's), so the licence lint leaves them alone, and a bundle never carries them (it carries a source's
    * figures only).
    */
  val covers: Set[String] = Set(Calibre, OpenLibrary)

  /** What to call a provider on a card or in a menu. */
  def title(p: String): String = p match
    case Source            => "source figures"
    case Commons           => "Wikimedia Commons"
    case Calibre           => "Calibre library"
    case OpenLibrary       => "Open Library"
    case Met               => "The Metropolitan Museum of Art"
    case Cleveland         => "The Cleveland Museum of Art"
    case ArtInstitute      => "The Art Institute of Chicago"
    case LibraryOfCongress => "Library of Congress"
    case CommonsDepicts    => "Wikimedia Commons (depictions)"
    case OpenContext       => "Open Context"
    case Pleiades          => "Pleiades (linked Commons files)"
    case Smithsonian       => "Smithsonian Institution"
    case Europeana         => "Europeana"
    case other             => other

/** The thumbnail a place or event card shows, with what its licence requires beside it: the credit line, the licence
  * (SPDX id or `public-domain`) and where to read it, and the page the picture comes from. `url` is this server's own
  * (`/media/{uid}/thumb`) — a hover never reaches outside.
  */
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
  def filePath(uid: String): String = s"/media/$uid/file"

/** A place or event a provider's picture is linked to, in its role, from which statement of which item (`P18` of
  * `Q132564`).
  */
final case class MediaTarget(
    targetType: String,
    targetId: String,
    role: String,
    property: Option[String] = None,
    item: Option[String] = None,
    /** Why a collection's object or map is tied to this place: the record's field that names it and the dates compared
      * (`excavation = Mycenae; 1400–1200 BCE within 1600–1100 BCE`).
      */
    basis: Option[String] = None
) derives KindlingsCodecAsObject

object MediaTarget:
  given Schema[MediaTarget] = KindlingsSchema.derived[MediaTarget].schema

/** One stored picture, whichever its origin: a source's figure (`citekey`) or a provider's (`provider`, `providerKey`),
  * with its licence and credit and what it is linked to.
  */
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

final case class MediaList(
    total: Int,
    items: List[MediaItem],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject:
  def page: OffsetPage = OffsetPage(total, offset, items.size)
object MediaList:
  given Schema[MediaList] = KindlingsSchema.derived[MediaList].schema
  def of(total: Int, rows: List[MediaItem], offset: Int): MediaList =
    MediaList(total, rows, offset, OffsetPage(total, offset, rows.size).next)

/** A count by key (a provider, a licence, a role, a reason); `allowed = false` marks a licence the provider's allowlist
  * refuses.
  */
final case class MediaCount(key: String, n: Int, allowed: Boolean = true) derives KindlingsCodecAsObject
object MediaCount:
  given Schema[MediaCount] = KindlingsSchema.derived[MediaCount].schema

  /** Pure: count `keys`, most frequent first, then by key. */
  def of(keys: List[String], allowed: String => Boolean = _ => true): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(k, v.size, allowed(k)))
      .sortBy(c => (-c.n, c.key))

/** One provider `media import <name>` can run (`GET /api/media/providers`): the registered media collections of the
  * plugins in this server — the built-in ones and any a third-party plugin added.
  *
  * @param name
  *   what `media import` is asked for
  * @param title
  *   what its jobs are called
  * @param over
  *   what it asks about: `places`, `events`, `places and events`
  * @param description
  *   what it brings and on what terms
  * @param plugin
  *   the plugin that registered it (`histmersion.plugins.<plugin>.enabled` switches it)
  * @param builtin
  *   Histmersion's own provider (its settings are `media.<provider>.*`)
  * @param licences
  *   the licence families its pictures may be stored under (a plugin's: the host's `media.collections` allowlist for
  *   it)
  */
final case class MediaProviderInfo(
    name: String,
    title: String,
    over: String,
    description: String,
    plugin: String,
    builtin: Boolean,
    licences: String
) derives KindlingsCodecAsObject
object MediaProviderInfo:
  given Schema[MediaProviderInfo] = KindlingsSchema.derived[MediaProviderInfo].schema

/** `POST /api/media/import`: ask a provider about places and events — `wikidata` (the default), `depicts`, `met`,
  * `cleveland`, `aic`, `loc`, `pleiades`, `opencontext`, `smithsonian` or `europeana` (docs/media-providers.md).
  * `dryRun` (the default) reads the provider's statements and licences and stores NOTHING; `place`/`event` narrow it to
  * one; `refresh` asks again about ones already asked (and re-reads the licences of what is stored).
  */
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

/** What an import found and did. The counts per licence come BEFORE anything is stored — a dry run is exactly this
  * report with `stored = updated = linked = 0`.
  *
  * @param targets
  *   places and events asked about
  * @param items
  *   Wikidata items read
  * @param statements
  *   picture statements found on them (P18, P242, …), after the per-property cap
  * @param files
  *   distinct files those statements name
  * @param byLicence
  *   files per licence (SPDX id or `public-domain`), `allowed = false` for the refused
  * @param rejected
  *   files not stored, per reason (licence not allowed, no author to credit, missing…)
  * @param revoked
  *   stored pictures whose licence a re-read no longer allows (kept; listed to review)
  */
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
    failed: List[String] = Nil,
    /** Places and events with a Wikidata item that this provider has still not been asked about — what the next run (or
      * the rest of this job) would take, most-cited sites first.
      */
    pending: Int = 0,
    /** Batches a job ran (each recorded before the next, so a stopped job resumes after it). */
    batches: Int = 0,
    /** A collection import's matches (a dry run's, for a person to judge): which record would be tied to which place,
      * and why.
      */
    samples: List[MediaSample] = Nil
) derives KindlingsCodecAsObject

/** One record a collection import ties to a place or event, with the reason. */
final case class MediaSample(
    targetType: String,
    targetId: String,
    targetLabel: String,
    key: String,
    title: String,
    license: String,
    basis: String,
    url: String
) derives KindlingsCodecAsObject

object MediaSample:
  given Schema[MediaSample] = KindlingsSchema.derived[MediaSample].schema

object MediaImportReport:
  given Schema[MediaImportReport] = KindlingsSchema.derived[MediaImportReport].schema

  /** Pure: two batches' reports as one — the counts added, the lists joined, the later `pending`. */
  def combine(a: MediaImportReport, b: MediaImportReport): MediaImportReport =
    def add(x: List[MediaCount], y: List[MediaCount]) =
      (x ++ y)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, cs.map(_.n).sum, cs.forall(_.allowed)))
        .sortBy(c => (-c.n, c.key))
    MediaImportReport(
      b.provider,
      b.dryRun,
      a.targets + b.targets,
      a.items + b.items,
      a.statements + b.statements,
      a.files + b.files,
      add(a.byLicence, b.byLicence),
      add(a.byRole, b.byRole),
      add(a.rejected, b.rejected),
      a.stored + b.stored,
      a.updated + b.updated,
      a.linked + b.linked,
      a.categories + b.categories,
      a.revoked ++ b.revoked,
      a.failed ++ b.failed,
      b.pending,
      a.batches + b.batches,
      (a.samples ++ b.samples).take(SampleCap)
    )

  /** The most samples a report carries (a job's summary is not a listing). */
  val SampleCap = 50

// ── pictures of sources: covers and illustrations (#1446) ──────────────────────────────────────

/** `POST /api/media/covers` (a dry run: what would be done, from the database alone) and `POST /api/media/covers/job`
  * (doing it, as a job): Open Library covers for the sources whose record holds an ISBN or OCLC number and no cover
  * yet, and an illustration — one of its own figures (the domain's `FigurePick`) — for every source that has figures
  * and no illustration yet. Calibre's covers are the CLI's part (`histmersion media covers`): the library is on the
  * user's machine. `citekey` narrows it to one source; `refresh` redoes the ones that already have theirs.
  */
final case class MediaCoversRequest(
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    citekey: Option[String] = None,
    refresh: Boolean = false,
    covers: Boolean = true,
    illustrations: Boolean = true,
    /** With `citekey`: an ISBN to ask Open Library by when the record holds none (the Calibre entry's, which
      * `histmersion media covers` passes when Calibre has no cover file).
      */
    isbn: Option[String] = None
) derives KindlingsCodecAsObject

object MediaCoversRequest:
  given Schema[MediaCoversRequest] = KindlingsSchema.derived[MediaCoversRequest].schema

/** One source's picture, done or (in a dry run) to do: its role (`cover`, `illustration`), where it comes from
  * (`calibre`, `open-library`, `source` — one of its own figures) and what (a figure's name, an ISBN, a Calibre id).
  */
final case class MediaSourceLine(citekey: String, role: String, provider: String, detail: String)
    derives KindlingsCodecAsObject

object MediaSourceLine:
  given Schema[MediaSourceLine] = KindlingsSchema.derived[MediaSourceLine].schema

/** What a covers/illustrations run did (or would do): the sources looked at, the covers and illustrations linked, each
  * one, why others had none (per reason), and what failed.
  */
final case class MediaCoversReport(
    dryRun: Boolean,
    sources: Int = 0,
    covers: Int = 0,
    illustrations: Int = 0,
    lines: List[MediaSourceLine] = Nil,
    skipped: List[MediaCount] = Nil,
    failed: List[String] = Nil
) derives KindlingsCodecAsObject

object MediaCoversReport:
  given Schema[MediaCoversReport] = KindlingsSchema.derived[MediaCoversReport].schema

  /** Pure: two runs' reports as one. */
  def combine(a: MediaCoversReport, b: MediaCoversReport): MediaCoversReport =
    MediaCoversReport(
      a.dryRun && b.dryRun,
      a.sources + b.sources,
      a.covers + b.covers,
      a.illustrations + b.illustrations,
      a.lines ++ b.lines,
      (a.skipped ++ b.skipped)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, cs.map(_.n).sum))
        .sortBy(c => (-c.n, c.key)),
      a.failed ++ b.failed
    )

/** `PUT /api/media/sources/{citekey}/cover`: a cover the CLI read from the local Calibre library (the body is the
  * image), stored as the source's cover under `provider`/`key` (the Calibre id).
  */
final case class SourceCoverParams(
    citekey: String,
    provider: String = MediaProvider.Calibre,
    key: String,
    mime: Option[String] = None
)

/** What the media layer can be filtered by: pictures per provider and per licence (`none` = no licence recorded).
  */
final case class MediaFacets(providers: List[MediaCount], licences: List[MediaCount]) derives KindlingsCodecAsObject
object MediaFacets:
  given Schema[MediaFacets] = KindlingsSchema.derived[MediaFacets].schema

/** `POST /api/media/fill-licences`: a source's figures take the source's licence where the source records one and the
  * figure has none. Without `apply` it only counts.
  */
final case class FigureLicenceFill(candidates: Int, updated: Int, applied: Boolean, bySource: List[MediaCount] = Nil)
    derives KindlingsCodecAsObject
object FigureLicenceFill:
  given Schema[FigureLicenceFill] = KindlingsSchema.derived[FigureLicenceFill].schema
