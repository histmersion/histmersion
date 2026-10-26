package com.histmersion.config

import org.ekrich.config.ConfigFactory
import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}

/** Env-var overrides (`port = ${?HISTMERSION_PORT}`) always arrive as HOCON STRINGS; the derived readers
  * below therefore accept either a number or a numeric string. Lexical-scope givens take priority over
  * the companion-scope defaults for every `derives ConfigReader` in this file. */
private def lenient[A](name: String, strict: ConfigReader[A], parse: String => Option[A]): ConfigReader[A] =
  new ConfigReader[A]:
    def from(v: org.ekrich.config.ConfigValue) =
      strict.from(v).orElse(
        ConfigReader.stringReader.from(v).flatMap(s =>
          parse(s.trim).toRight(ConfigDecodingError.CannotConvert(Nil, s"'$s' is not a $name", None))))

given ConfigReader[Int]     = lenient("int", ConfigReader.intReader, _.toIntOption)
given ConfigReader[Long]    = lenient("long", ConfigReader.longReader, _.toLongOption)
given ConfigReader[Double]  = lenient("double", ConfigReader.doubleReader, _.toDoubleOption)
given ConfigReader[Boolean] = lenient("boolean", ConfigReader.booleanReader, _.toBooleanOption)

/** Application config, modelled as case classes and read from HOCON (sconfig) via Kindlings
  * derivation. Defaults + env overrides live in `reference.conf`; nothing is hardcoded elsewhere. */
/** `requestTimeoutSeconds` bounds one HTTP request end to end. Netty's default is 20 s, which is
  * shorter than a `pg_dump` of the live corpus (~40 s for 860 MB) — the request was answered 503 and
  * the finished dump was never moved into place. Long operations (backup, restore, seed, ingest of
  * a large file) need this to be at least `backups.timeout-seconds`. `shutdownGraceSeconds` is how long a
  * stop waits for the open connections to close — netty counts an idle keep-alive connection as open,
  * so every stop with a client still connected waited the whole time; the test suites set it to 0. */
/** `sourceUrl`: the repository the About window and `histmersion about` link as this program's source. */
final case class ServerConfig(host: String, port: Int, publicUrl: String, requestTimeoutSeconds: Int,
    shutdownGraceSeconds: Int, sourceUrl: String)
    derives ConfigReader
/** `pool.max` sizes the HikariCP pool behind every [[com.histmersion.db.Db]] DataSource. Pooling is not a
  * micro-optimisation here: without it every repo call opened a fresh TCP connection and did the
  * whole startup handshake. */
final case class DbPoolConfig(max: Int) derives ConfigReader
final case class DbConfig(url: String, user: String, password: String, pool: DbPoolConfig)
    derives ConfigReader
/** The OCR sidecar (plan §1.8). `dpi`/`maxImagePx` are passed through to `POST /convert`;
  * `spoolDir` is where the server keeps an uploaded original so `reocr` can re-convert it without a
  * second upload. */
final case class ConverterConfig(
    url: String,
    timeoutSeconds: Int,
    parallelism: Int,
    dpi: Int,
    maxImagePx: Int,
    spoolDir: String,
    /** How long `/api/health` waits for the sidecar before reporting it degraded. A dead converter
      * used to block the health check for the full connect+read budget (~15 s), which is exactly
      * when a health check most needs to answer. */
    healthTimeoutMs: Int,
    /** …and how long that answer is reused, so a monitor polling every second does not open a
      * connection per poll. */
    healthCacheMs: Int,
    /** A conversion may take `pages × this` when that is longer than `timeoutSeconds` — a whole
      * book legitimately runs for an hour. `secondsPerPageQuality` is the docling (quality) path. */
    secondsPerPage: Int,
    secondsPerPageQuality: Int,
    /** A page with no text layer is OCR'd on the fast path too: a scanned 155-page book took over
      * 600 s, more than 3.9 s a page. */
    secondsPerScannedPage: Int
) derives ConfigReader
final case class CorpusConfig(dir: String) derives ConfigReader
/** Where `pg_dump` archives are written (plan §1.10). `pgBinDir` is empty by default — the binaries
  * are looked up on PATH — and points at a directory holding `pg_dump`/`pg_restore` when they are
  * installed off PATH (homebrew's libpq is keg-only). */
/** @param keep how many archives a finished dump leaves in `dir` (the newest); `0` keeps them all. */
final case class BackupsConfig(dir: String, pgBinDir: String, timeoutSeconds: Int, requestWaitSeconds: Int, keep: Int)
    derives ConfigReader

/** `histmersion maintenance vacuum` (#1445): the tables it vacuums when none is named. */
final case class MaintenanceConfig(vacuumTables: String) derives ConfigReader:
  def tables: List[String] = PeriodsConfig.split(vacuumTables)

/** `bundle export|verify|import` (docs/research-merge-import.md): where the server builds a bundle
  * before it is downloaded and keeps an uploaded one while it is read, and how long a finished
  * export is left there for its download before the next export clears it. */
final case class BundlesConfig(dir: String, staleMinutes: Int) derives ConfigReader

/** The Storage part of About (`histmersion about`, `/api/about`, the web About window): how often the
  * server measures its directories (`ttl-seconds`; About answers the last measurement in between),
  * how long a request waits for a measurement under way before it answers "measuring…"
  * (`wait-millis`), the caps of one measurement (`max-millis`, `max-entries` — a walk they stop
  * reports "at least"), how many of the largest tables it lists (`top-tables`) and the free space
  * below which it says what to delete (`low-free-gb`). */
final case class StorageConfig(ttlSeconds: Int, waitMillis: Int, maxMillis: Int, maxEntries: Int, topTables: Int,
    lowFreeGb: Double) derives ConfigReader:
  def lowFreeBytes: Long = (lowFreeGb * 1e9).toLong

/** The source matcher of `merge plan` (docs/research-merge-import.md §5.2): Fellegi–Sunter-style
  * log-weights with hand-set values, and the two thresholds that split a score into auto-merge,
  * needs-decision and new. Tuned here, not in code. */
final case class MergeConfig(
    /** A score at or above this, with no hard flag, merges without asking. */
    autoMerge: Double,
    /** A score at or below this is not the same work. */
    newAtMost: Double,
    /** MinHash functions per OCR body. */
    minhashSize: Int,
    /** Two findings of a merged pair are near-duplicates at this claim-word Jaccard (with overlapping
      * periods): kept apart by default, listed so the user can say `merge`. */
    findingNearAtLeast: Double,
    /** Two findings of a merged pair are at the same place within this many km (§5.4). */
    findingWithinKm: Double,
    weights: MergeWeights,
    /** A place of the bundle with no id here is the same as a place here with its label and kind when
      * the two points are this close (km)… */
    placeWithinKm: Double = 5.0,
    /** …or the two shapes overlap this much (intersection over union). */
    placeOverlapAtLeast: Double = 0.8,
    /** How long `POST /api/merge/apply` waits for the apply (its backup first) before it answers
      * `running` with the job to follow (`GET /api/merge/apply/{job}`). */
    applyWaitSeconds: Int = 20
) derives ConfigReader

final case class MergeWeights(
    titleFingerprint: Double,
    titleMain: Double,
    titleSimilar: Double,
    titleSimilarAtLeast: Double,
    titleTokens: Double,
    titleTokensAtLeast: Double,
    titleDiffers: Double,
    authorsSame: Double,
    authorsFirst: Double,
    authorsOverlap: Double,
    authorsDisjoint: Double,
    yearEqual: Double,
    yearNear: Double,
    yearReprint: Double,
    yearFar: Double,
    venueEqual: Double,
    venueDiffers: Double,
    bodySame: Double,
    bodySameAtLeast: Double,
    bodyEdition: Double,
    bodyEditionAtLeast: Double,
    bodyDiffers: Double,
    bodyDiffersBelow: Double,
    alias: Double
) derives ConfigReader

final case class Limits(
    /** HARD cap on any caller-supplied `limit`, everywhere. */
    maxPage: Int,
    search: Int,
    findings: Int,
    snippetChars: Int,
    chunkChars: Int,
    reindexChars: Long,
    /** Largest accepted `POST /api/ingest` upload, in bytes. */
    uploadBytes: Long,
    /** Largest accepted base64 payload of the MCP `ingest` tool, in bytes of DECODED data. */
    inlineBytes: Long,
    /** Largest single stored asset, in bytes — a runaway figure must not fill the row. */
    assetBytes: Long,
    /** Cap on `fullText=true` / `section=body` without `offset` in `get_source`. */
    fullTextChars: Int,
    /** The most sources of an article's filter its coverage (#958) reads; the counts stay exact. */
    coverageSources: Int,
    /** `passages` (#954): search inside ONE source's body. */
    passages: PassageLimits
) derives ConfigReader:
  /** `ts_headline` counts words, not characters; ~6 chars per word is the usual English average. */
  def snippetWords: Int = math.max(1, snippetChars / 6)
  /** Clamp a caller's `limit` into `[1, maxPage]`; `<= 0` means "the caller gave none" and yields
    * `dflt` (itself capped). ONE definition, so no surface can forget the cap. */
  def page(limit: Option[Int], dflt: Int): Int =
    val cap = math.max(1, maxPage)
    limit.filter(_ > 0).map(_.min(cap)).getOrElse(math.max(1, dflt).min(cap))
/** `passages` (#954): the defaults and caps of a caller's `limit` (passages shown) and `context`
  * (characters around a match), how many matches one call counts before it stops, and how long a
  * caller's regex may run over one body before it is refused. */
final case class PassageLimits(limit: Int, maxLimit: Int, context: Int, maxContext: Int, maxMatches: Int,
    regexTimeoutMs: Int) derives ConfigReader:
  /** `limit` into `[1, maxLimit]`; absent or `<= 0` is the default. */
  def limitOf(requested: Option[Int]): Int =
    requested.filter(_ > 0).getOrElse(limit).max(1).min(math.max(1, maxLimit))
  /** `context` into `[0, maxContext]`; absent or negative is the default. */
  def contextOf(requested: Option[Int]): Int =
    requested.filter(_ >= 0).getOrElse(context).max(0).min(math.max(0, maxContext))

/** The timeline's span (`ui.time`): the earliest and latest year it reaches, and the years it opens on. */
final case class UiTimeSettings(min: Long, max: Long, defaultFrom: Long, defaultTo: Long) derives ConfigReader

/** `ui.debounce`: the quiet periods before the page acts on typing and state changes; `previewMs` is the one
  * before a HOVERED suggestion's preview reloads the lists. */
final case class UiDebounceSettings(suggestMs: Int, searchMs: Int, reloadMs: Int, previewMs: Int) derives ConfigReader

/** `ui.events`: the events on the map in playback — how long a local and a regional event fade (seconds of
  * playback), the most one playback returns, and how far an area outline is simplified (degrees). */
final case class UiEventsSettings(localFadeSeconds: Double, regionalFadeSeconds: Double, mapLimit: Int,
    outlineToleranceDeg: Double) derives ConfigReader

/** The `ui` block. The page reads it through `/api/config`, where the server adds what is no setting of
  * this block: whether writes need a key, whether agents run, the plugins and the base map's language. */
final case class UiSettings(
    tiles: String,
    tilesMaxZoom: Int,
    markerRadius: Int,
    viewportDebounceMs: Int,
    pageSize: Int,
    facetSuggestions: Int,
    treeDepth: Int,
    treeSamples: Int,
    maxBuckets: Int,
    debounce: UiDebounceSettings,
    time: UiTimeSettings,
    narrowMaxWidthPx: Int,
    narrowMaxHeightPx: Int,
    tabletMaxWidthPx: Int,
    events: UiEventsSettings,
    countersPollSeconds: Int = 30,
    jobPollMs: Int = 1000,
    tilesTone: String = "light",
    docsUrl: String = "",
    baseMapSource: String = "auto"
) derives ConfigReader

/** `histmersion query` (plan §5.2 stream 2.8): the read-only SQL escape hatch's safety rails.
  * `timeoutMs` becomes `SET LOCAL statement_timeout` inside the READ ONLY transaction, `maxRows`
  * caps the rows returned (one extra is fetched to detect truncation) and `maxCellChars` caps one
  * cell — `select ocr_markdown from sources` is otherwise 84 MB of JSON. */
/** `user`/`password` are the DEDICATED read-only login (`histmersion_query`, created by migration V011),
  * not the application role — see the three layers documented on [[Query]]. */
final case class QueryConfig(user: String, password: String, timeoutMs: Int, maxRows: Int,
    maxCellChars: Int) derives ConfigReader

/** `histmersion lint`: how much of each rule's offending set is LISTED (the count is always complete) and
  * how many citekey prefix candidates a dangling edge target is offered. */
final case class LintConfig(maxItems: Int, suggestions: Int) derives ConfigReader

/** `calibre` is the host-side Calibre integration (see [[CalibreCliConfig]]); the server never uses it. */
final case class CliConfig(serverUrl: String, calibre: CalibreCliConfig, plugins: CliPluginsConfig) derives ConfigReader

/** `histmersion plugin …` (docs/research-plugins.md §7.2): the plugins of the Histmersion installed on
  * THIS machine. `installDir` empty = the install this CLI binary belongs to (the directory above its
  * `bin/`), else the platform default; `mavenRepos` is comma-separated (an env override is one string),
  * tried in order for `plugin add group:artifact:version`; `curl` downloads (`plugin add <url>`). */
final case class CliPluginsConfig(installDir: String, mavenRepos: String, curl: String) derives ConfigReader:
  def repos: List[String] = mavenRepos.split(",").toList.map(_.trim).filter(_.nonEmpty)

/** The gazetteer (plan §1.6a). `pointToleranceKm` is how close a finding must be to a POINT place
  * (a Pleiades site has no extent) to count as inside it; polygons use exact containment. The limits
  * bound what `/api/places` and the hierarchy walk return, and `seedDir` is where `histmersion places
  * seed` looks for the committed Natural Earth / Pleiades files on the HOST. The geocoder (#981,
  * `db.Geocoder`) treats a point shared by `geocodePlaceholderMin` or more places as a placeholder,
  * and the `finding-geocode` repair moves only points that end up more than `geocodeRepairKm` away.
  * `modernFrom` is the year from which a time-filtered view shows present-day places
  * ([[PlaceTimeBasis.Modern]]: the Natural Earth cities, undated GeoNames/Wikidata places).
  * `placeholderAreaShare`: a finding whose point is a placeholder belongs to the areas covering at
  * least this share of the place its name resolves to (`db.PlaceRepo.insertAssignments`).
  * `geoMaxLimit` caps the map's area and site feed (`/api/places/geo`) apart from `limits.max-page`:
  * over a whole range the area layer draws every state alive in it (1 744 for -3000..2000), each a
  * stored simplified outline (V090). */
final case class PlacesConfig(
    pointToleranceKm: Double,
    searchLimit: Int,
    childrenLimit: Int,
    treeLimit: Int,
    treeDepth: Int,
    seedDir: String,
    importBatch: Int,
    geocodePlaceholderMin: Int,
    geocodeRepairKm: Double,
    modernFrom: Long,
    sources: PlaceSourcesConfig,
    placeholderAreaShare: Double,
    geoMaxLimit: Int
) derives ConfigReader:
  /** `/api/places/geo`'s `limit` into `[1, geoMaxLimit]`; absent or `<= 0` is `dflt` (itself capped). */
  def geoLimit(requested: Option[Int], dflt: Int): Int =
    val cap = math.max(1, geoMaxLimit)
    requested.filter(_ > 0).getOrElse(dflt).max(1).min(cap)

/** The gazetteer's external sources (#1064): where the pinned GeoNames / Wikidata extracts and the
  * LPF drop folder are (empty = under the data directory, [[UserPaths.dataDir]]), the manifest that
  * pins the extracts (relative to [[InitConfig.dataDir]]), records per transaction, the largest LPF
  * FeatureCollection read whole, how long a download of a missing extract may take, and the window
  * of years before a known end that stands for an UNKNOWN start of a GeoNames or Wikidata place
  * ([[PlaceDating.EndOnly]]), and the kinds whose point-only GeoNames/Wikidata records are not kept
  * as places ([[Gazetteer.dropsPointArea]]) with the batch and time budget of their removal. */
final case class PlaceSourcesConfig(
    dir: String,
    lpfDir: String,
    manifest: String,
    batch: Int,
    maxLpfMb: Int,
    downloadTimeoutSeconds: Int,
    openStartWindowYears: Int,
    pointAreaKinds: String,
    pointAreaFixBatch: Int,
    pointAreaFixSeconds: Int
) derives ConfigReader:
  /** `point-area-kinds` as a set ([[Gazetteer.dropsPointArea]]); empty keeps every record. */
  def pointAreaKindSet: Set[String] = PeriodsConfig.split(pointAreaKinds).map(_.toLowerCase(java.util.Locale.ROOT)).toSet

/** Named periods (#719): where `periods fetch periodo` downloads the PeriodO dump, the authorities it
  * imports by default (comma-separated PeriodO ids) and the bundled preset seed under
  * [[InitConfig.dataDir]]. */
final case class PeriodsConfig(periodoUrl: String, periodoCollections: String, seedFile: String)
    derives ConfigReader:
  def collections: List[String] = PeriodsConfig.split(periodoCollections)

object PeriodsConfig:
  private[config] def split(s: String): List[String] =
    Option(s).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct

/** First-run data (#720). With `auto`, a starting server imports the bundled gazetteer
  * (`<dataDir>/places`) and period presets (`<dataDir>/periods`) into whichever of those tables is
  * EMPTY, as a background job; `histmersion init` runs the same on demand, and `--fetch` adds the
  * network sources in `fetchSources` (comma-separated: gazetteer importers, `land`, `periodo`). */
final case class InitConfig(auto: Boolean, dataDir: String, fetchSources: String) derives ConfigReader:
  def sources: List[String] = PeriodsConfig.split(fetchSources)

/** API key authentication. When `apiKey` is non-empty the server gates every write method
  * (POST/PUT/PATCH/DELETE) behind `Authorization: Bearer <key>`. GET/HEAD/OPTIONS (the web UI, all
  * read APIs) stay open. Empty = no auth (local-only mode). */
final case class AuthConfig(apiKey: String) derives ConfigReader

/** Reloading the config in place (docs/research-plugins.md §3.5): `histmersion config reload`, or —
  * with `watch` — by itself when the config file changes, `watchDebounceMs` after the last change.
  * `drainSeconds` is how long a reload waits for the previous generation's last users (an in-flight
  * request, a job step) before it reports them as still draining. */
/** The terrain base map (docs/research-terrain-basemap.md): where its two PMTiles extracts live
  * (empty = the XDG cache directory, [[UserPaths.cacheDir]]), the region fetched, the free disk a
  * fetch must leave, how politely the range requests go, the sources (the Protomaps vector build,
  * Mapterhorn's elevation, Natural Earth's physical names, ETOPO1's sea floor), and how far the map
  * zooms in on its two vector styles (`mapMaxZoom`). */
final case class BasemapConfig(
    dir: String,
    bbox: String,
    minFreeGb: Double,
    requestGapMs: Int,
    maxRequestMb: Int,
    overfetch: Double,
    timeoutSeconds: Int,
    retries: Int,
    tileCacheSeconds: Int,
    vector: BasemapVectorConfig,
    terrain: BasemapTerrainConfig,
    labels: BasemapLabelsConfig,
    bathymetry: BasemapBathymetryConfig,
    mapMaxZoom: Int
) derives ConfigReader

/** `drop`: the vector layers (or their kinds) never stored — [[Basemap.parseDrop]]; everything else is
  * kept and the style decides what is drawn. `onDemand`: the map outside the extract (or deeper), read as a
  * page needs it (#3760). */
final case class BasemapVectorConfig(buildsUrl: String, url: String, build: String, maxZoom: Int,
    drop: String, attribution: String, onDemand: BasemapOnDemandConfig) derives ConfigReader

/** `onDemand`: the deeper relief read from `url` as a page needs it ([[BasemapOnDemandConfig]]);
  * `focusRegions`: "name minLon,minLat,maxLon,maxLat" — the regions `fetch --focus <name>` keeps to that depth. */
final case class BasemapTerrainConfig(url: String, maxZoom: Int, attribution: String, onDemand: BasemapOnDemandConfig,
    focusRegions: List[String]) derives ConfigReader

/** A layer read tile by tile when a page shows an area the download does not hold (the relief deeper than
  * the extract; the map and the sea floor outside it, #3760), cached to `cacheGb`. */
final case class BasemapOnDemandConfig(enabled: Boolean, maxZoom: Int, cacheGb: Double, concurrency: Int,
    requestsPerSecond: Double, retryAfterSeconds: Int) derives ConfigReader

/** The physical names (seas, ranges, deserts, lakes, rivers, peaks …): Natural Earth's label layers,
  * `url` with `{file}` for each of `files`, cut to the region and written as a small vector archive of
  * zooms 0…`maxZoom`; `language` is the names a style shows unless it says otherwise. */
final case class BasemapLabelsConfig(url: String, files: List[String], maxZoom: Int, language: String,
    attribution: String) derives ConfigReader

/** Where the sea floor's extract comes from (`basemap.bathymetry.source`). */
enum SeaFloorSource(val wire: String):
  /** NOAA's ETOPO1 (1 arc-minute, ~1.8 km) through Mapzen's terrain tiles: one GET a tile. */
  case Etopo1 extends SeaFloorSource("etopo1")
  /** The GEBCO grid (15 arc-seconds, ~460 m): its GeoTIFFs read by range and made into tiles by the fetch. */
  case Gebco extends SeaFloorSource("gebco")

object SeaFloorSource:
  def parse(s: String): Either[String, SeaFloorSource] =
    values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
      .toRight(s"'$s' is not a sea-floor source (${values.map(_.wire).mkString(", ")})")

given ConfigReader[SeaFloorSource] = ConfigReader.stringReader.emap(SeaFloorSource.parse)

/** The sea floor: terrarium tiles at `url` (`{z}/{x}/{y}`), zooms 0…`maxZoom` of the region, one GET a
  * tile, `requestGapMs` apart; `onDemand`: the tiles outside the region, read as a page needs them (#3760).
  * `source` picks what the extract is made from: those tiles (ETOPO1), or the GEBCO grid (`gebco`). */
final case class BasemapBathymetryConfig(url: String, maxZoom: Int, requestGapMs: Int, attribution: String,
    onDemand: BasemapOnDemandConfig, source: SeaFloorSource, gebco: BasemapGebcoConfig) derives ConfigReader:
  def extractMaxZoom: Int = source match
    case SeaFloorSource.Etopo1 => maxZoom
    case SeaFloorSource.Gebco  => gebco.maxZoom

/** The GEBCO grid as a sea-floor source: `url`, one GeoTIFF of the grid's tiling (`tileDegrees` square) with
  * `{north}`, `{south}`, `{west}`, `{east}`; the extract's zooms 0…`maxZoom`; its credit line. */
final case class BasemapGebcoConfig(url: String, tileDegrees: Int, maxZoom: Int, attribution: String) derives ConfigReader

final case class ConfigReloadConfig(watch: Boolean, watchDebounceMs: Int, drainSeconds: Int) derives ConfigReader

/** The native bundle: when `enabled`, the server itself runs the bundled PostgreSQL and
  * OCR converter as child processes (`com.histmersion.server.Supervisor`). `home` is the bundle root holding
  * `pg/` and `converter/` (empty = the directory above the running executable's `bin/`); `dataDir`
  * holds the cluster, logs and backups (empty = the platform's per-user data directory). */
final case class RuntimeConfig(enabled: Boolean, home: String, dataDir: String, pgPort: Int, converterPort: Int,
    postgres: PgTuning)
    derives ConfigReader

/** Memory settings the bundled Postgres is started with (`-c`, so an existing cluster gets them on its
  * next start; nothing is written to its `postgresql.conf`). Each is a Postgres size (`"2GB"`,
  * `"512MB"`), `"auto"` (sized from the machine's RAM, see `Supervisor.pgSettings`) or `""` (Postgres'
  * own default). initdb's defaults — 128 MB of shared buffers, 4 MB per sort — are sized for a
  * machine with no RAM to spare; this one holds a 15 GB gazetteer it searches as you type. */
final case class PgTuning(sharedBuffers: String, effectiveCacheSize: String, workMem: String) derives ConfigReader

/** Media from outside providers (#1446): the polite-client settings, the stored sizes and, per
  * provider, its API and the licence families it may be stored under ([[MediaLicence]]). */
final case class MediaConfig(
    userAgent: String,
    timeoutSeconds: Int,
    requestIntervalMs: Int,
    maxRetries: Int,
    maxRetryAfterSeconds: Int,
    defaultLimit: Int,
    maxLimit: Int,
    displayPx: Int,
    thumbPx: Int,
    maxBytes: Long,
    perProperty: Int,
    importBatch: Int,
    sameSiteKm: Double,
    wikidata: MediaWikidataConfig,
    commons: MediaCommonsConfig,
    covers: MediaCoversConfig,
    illustrations: MediaIllustrationsConfig,
    depicts: MediaDepictsConfig,
    met: MediaCollectionConfig,
    cleveland: MediaCollectionConfig,
    aic: MediaCollectionConfig,
    loc: MediaCollectionConfig,
    pleiades: MediaCollectionConfig,
    openContext: MediaCollectionConfig,
    smithsonian: MediaCollectionConfig,
    europeana: MediaCollectionConfig,
    collections: MediaPluginCollectionsConfig,
    samples: Int
) derives ConfigReader:
  /** `--limit`, clamped to `1 … max-limit`; absent or ≤ 0 = `default-limit`. */
  def limit(asked: Option[Int]): Int =
    asked.filter(_ > 0).getOrElse(defaultLimit).max(1).min(maxLimit.max(1))

final case class MediaWikidataConfig(apiUrl: String, batch: Int, maxlag: Int, placeProperties: String,
    eventProperties: String) derives ConfigReader

final case class MediaCommonsConfig(apiUrl: String, batch: Int, maxlag: Int, licences: String) derives ConfigReader

/** Pictures that depict an event (`media.depicts`): Commons files whose structured data says
  * `P180 depicts` the event's item, and the images of the works its item lists as `P1299 depicted by`. */
final case class MediaDepictsConfig(perEvent: Int, searchLimit: Int, depictedBy: Boolean, licences: String)
    derives ConfigReader

/** One open-access collection (`media.met`, `.cleveland`, `.aic`, `.loc`, `.pleiades`, `.open-context`,
  * `.smithsonian`, `.europeana`): its API, its own pace,
  * how many records are looked at and kept per place, and the licence families it may be stored
  * under. `image-url` is the IIIF server where a collection has one; `max-year` the public-domain
  * boundary for maps. */
final case class MediaCollectionConfig(enabled: Boolean, apiUrl: String, imageUrl: String, candidates: Int,
    perPlace: Int, requestIntervalMs: Int, displayPx: Int, maxYear: Int, licences: String,
    /** Seconds to wait, one step per 403 of a request, before asking again (#2561); "" = a 403 is final. */
    refusalBackoffSeconds: String = "") derives ConfigReader:
  /** [[refusalBackoffSeconds]] as milliseconds; an entry that is not a positive number is dropped. */
  def refusalBackoffMs: List[Long] =
    Option(refusalBackoffSeconds).toList.flatMap(_.split(",")).map(_.trim).flatMap(_.toLongOption).filter(_ > 0).map(_ * 1000)

/** The media collections PLUGINS register (`media.collections`, docs/research-plugins.md §6): the licence
  * families their pictures may be stored under. The host judges each record's stated licence against
  * this — never against anything the plugin says — so a plugin cannot widen it; the user can, per
  * collection (`per-collection.<id>`, which replaces `licences` for that one). The built-in collections
  * keep their own `media.<provider>.licences`. */
final case class MediaPluginCollectionsConfig(licences: String, perCollection: Map[String, String]) derives ConfigReader:
  /** The allowlist as written for `collection` (what a report quotes). */
  def licencesFor(collection: String): String = perCollection.getOrElse(collection, licences)

/** Book covers (`media.covers`): the Open Library Covers API and its own, slower pace. */
final case class MediaCoversConfig(openLibraryUrl: String, openLibraryPage: String, requestIntervalMs: Int,
    size: String, thumbSize: String) derives ConfigReader

/** How a paper's illustration is chosen among its figures (`media.illustrations`). */
final case class MediaIllustrationsConfig(minSide: Int, minArea: Int, maxAspect: Double, maxRefs: Int,
    earlyPages: Int, pageScanMin: Int) derives ConfigReader

/** Events from catalogues (#1449): `events` in reference.conf. The HTTP politeness (User-Agent,
  * retries, Retry-After cap) is `media`'s; each catalogue has its own pace. */
final case class EventsConfig(
    timeoutSeconds: Int,
    minYear: Long,
    maxYear: Long,
    region: String,
    mergeKm: Double,
    mergeYears: Long,
    defaultLimit: Int,
    /** Records `events rederive` writes in one transaction. */
    rederiveBatch: Int,
    gvp: EventsGvpConfig,
    noaa: EventsNoaaConfig,
    wikidata: EventsWikidataConfig,
    links: EventsLinksConfig,
    leads: EventsLeadsConfig,
    siteTypes: SiteTypesConfig
) derives ConfigReader

/** `floorYear`: the earliest year a GVP dating may reach (the list is of HOLOCENE eruptions: a ± that
  * runs before it is held there). */
final case class EventsGvpConfig(url: String, pageSize: Int, windowYears: Long, requestIntervalMs: Int, regionMinVei: Int,
    outsideMinVei: Int, floorYear: Long) derives ConfigReader
final case class EventsNoaaConfig(url: String, pageSize: Int, requestIntervalMs: Int, datasets: String,
    regionMinMagnitude: Double, outsideMinMagnitude: Double) derives ConfigReader
final case class EventsWikidataConfig(sparqlUrl: String, apiUrl: String, requestIntervalMs: Int, apiIntervalMs: Int,
    classes: String, languages: String, batch: Int) derives ConfigReader:
  def languageList: List[String] = languages.split(",").map(_.trim).filter(_.nonEmpty).toList
final case class EventsLinksConfig(km: Double, years: Long, nameWeight: Double, findingWeight: Double, minScore: Double,
    minNameChars: Int, maxCandidates: Int, sourcesPerName: Int, nearFindings: Int, batch: Int) derives ConfigReader
final case class EventsLeadsConfig(km: Double, years: Long, articleKm: Double, largeVei: Int, largeMagnitude: Double,
    articleLimit: Int) derives ConfigReader
/** Refining Wikidata sites' types from their items' classes (`events.site-types`). */
final case class SiteTypesConfig(batch: Int, superclassDepth: Int) derives ConfigReader

final case class AppConfig(
    server: ServerConfig,
    db: DbConfig,
    converter: ConverterConfig,
    corpus: CorpusConfig,
    backups: BackupsConfig,
    maintenance: MaintenanceConfig,
    bundles: BundlesConfig,
    storage: StorageConfig,
    merge: MergeConfig,
    limits: Limits,
    ui: UiSettings,
    cli: CliConfig,
    query: QueryConfig,
    lint: LintConfig,
    places: PlacesConfig,
    periods: PeriodsConfig,
    init: InitConfig,
    acquire: AcquireConfig,
    auth: AuthConfig,
    runtime: RuntimeConfig,
    agents: AgentsConfig,
    media: MediaConfig,
    events: EventsConfig,
    config: ConfigReloadConfig,
    basemap: BasemapConfig
) derives ConfigReader

object AppConfig:
  /** reference.conf with the environment's overrides — no user file. What tests and `about`-less
    * tools read. Must run on a platform thread (main), not an Ox virtual thread: the env read uses
    * GraalVM StackValue under native-image. */
  def load(): AppConfig = load(ConfigFactory.systemEnvironment())

  /** Load with an explicit environment (tests). */
  def load(env: org.ekrich.config.Config): AppConfig =
    decode(ConfigLayers.merged(ConfigLayers.reference, ConfigFactory.empty, env)) match
      case Right(cfg) => cfg
      case Left(err)  => throw new RuntimeException(s"Histmersion config error: $err")

  /** The server's and the CLI's load: reference.conf, then the user config file
    * ([[UserPaths.configFile]]), then the environment — env > file > reference.conf. A missing file
    * is no error; one that does not parse or does not type-check is, naming the file and the key.
    * Main thread only, like [[load]]. */
  def loadWithUserFile(): Either[String, LoadedConfig] =
    val env = ConfigFactory.systemEnvironment()
    loadWithUserFile(env, UserPaths.configFile(lookup(env)))

  /** The same with an explicit environment and file (tests; `file = None`: no location). */
  def loadWithUserFile(env: org.ekrich.config.Config, file: Option[java.nio.file.Path]): Either[String, LoadedConfig] =
    // Parse reference.conf and resolve ${?ENV} overrides from the given environment only — deliberately
    // NOT ConfigFactory.load(), which folds ALL system properties into HOCON (dotted keys like
    // `io.netty.allocator.type` then collide and blow up sconfig under native-image).
    val reference = ConfigLayers.reference
    // sconfig's parse errors already start with the file (their origin): not twice
    def inFile(p: java.nio.file.Path)(msg: String) =
      s"Histmersion config file $p: ${msg.stripPrefix(s"$p: ")}"
    file match
      case None =>
        val merged = ConfigLayers.merged(reference, ConfigFactory.empty, env)
        decode(merged).left.map(e => s"Histmersion config error: $e")
          .map(LoadedConfig(_, UserConfigFile(None, loaded = false), env = env, merged = ConfigReload.settings(merged)))
      case Some(p) =>
        ConfigLayers.parse(p).left.map(inFile(p)).flatMap {
          case None =>
            val merged = ConfigLayers.merged(reference, ConfigFactory.empty, env)
            decode(merged).left.map(e => s"Histmersion config error: $e")
              .map(LoadedConfig(_, UserConfigFile(Some(p), loaded = false), env = env,
                merged = ConfigReload.settings(merged)))
          case Some(user) =>
            for
              // the settings and the KIND of each from the defaults alone: an env value is always a
              // string, and would make every key it overrides look like a string setting
              known   <- resolved(reference).left.map(inFile(p))
              keys    <- ConfigLayers.check(user, known).left.map(inFile(p))
              // the file's OWN values are checked, not what the environment leaves of them: a mistyped
              // setting that an env var happens to shadow today is still a broken file
              asFiled <- resolved(user.withFallback(reference).withFallback(env)).left.map(inFile(p))
              _       <- ConfigLayers.typeCheck(keys.settings, asFiled, known).left.map(inFile(p))
              merged  <- resolved(ConfigLayers.merged(reference, user, env)).left.map(inFile(p))
              cfg     <- decode(merged).left.map(inFile(p))
            yield LoadedConfig(cfg, UserConfigFile(Some(p), loaded = true, keys.unknown), user, env,
              ConfigReload.settings(merged))
        }

  /** An environment `Config` as a lookup (its keys are variable names, not paths). */
  def lookup(env: org.ekrich.config.Config): String => Option[String] =
    name => Option(env.root.get(name)).map(_.unwrapped).collect { case s: String => s; case o if o != null => o.toString }

  private def resolved(c: => org.ekrich.config.Config): Either[String, org.ekrich.config.Config] =
    try Right(c.resolve())
    catch case e: org.ekrich.config.ConfigException => Left(e.getMessage)

  /** Decode the settings of a merged tree (`LoadedConfig.merged`, or one [[ConfigReload.effective]]
    * made of it). */
  def fromMerged(c: org.ekrich.config.Config): Either[String, AppConfig] = decode(c)

  private def decode(c: => org.ekrich.config.Config): Either[String, AppConfig] =
    try ConfigReader[AppConfig].from(c.getConfig("histmersion").root).left.map(ConfigLayers.describe)
    catch case e: org.ekrich.config.ConfigException => Left(e.getMessage)
