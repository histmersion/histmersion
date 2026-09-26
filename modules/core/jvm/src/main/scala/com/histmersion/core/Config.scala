package com.histmersion.core

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
  * a large file) need this to be at least `backups.timeout-seconds`. */
final case class ServerConfig(host: String, port: Int, publicUrl: String, requestTimeoutSeconds: Int)
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
    secondsPerPageQuality: Int
) derives ConfigReader
final case class CorpusConfig(dir: String) derives ConfigReader
/** Where `pg_dump` archives are written (plan §1.10). `pgBinDir` is empty by default — the binaries
  * are looked up on PATH — and points at a directory holding `pg_dump`/`pg_restore` when they are
  * installed off PATH (homebrew's libpq is keg-only). */
/** @param keep how many archives a finished dump leaves in `dir` (the newest); `0` keeps them all. */
final case class BackupsConfig(dir: String, pgBinDir: String, timeoutSeconds: Int, requestWaitSeconds: Int, keep: Int)
    derives ConfigReader

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
    fullTextChars: Int
) derives ConfigReader:
  /** `ts_headline` counts words, not characters; ~6 chars per word is the usual English average. */
  def snippetWords: Int = math.max(1, snippetChars / 6)
  /** Clamp a caller's `limit` into `[1, maxPage]`; `<= 0` means "the caller gave none" and yields
    * `dflt` (itself capped). ONE definition, so no surface can forget the cap. */
  def page(limit: Option[Int], dflt: Int): Int =
    val cap = math.max(1, maxPage)
    limit.filter(_ > 0).map(_.min(cap)).getOrElse(math.max(1, dflt).min(cap))
given ConfigReader[TimeConfig]    = ConfigReader.derived
given ConfigReader[DebounceConfig] = ConfigReader.derived
given ConfigReader[UiConfig]      = ConfigReader.derived

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
final case class CliConfig(serverUrl: String, calibre: CalibreCliConfig) derives ConfigReader

/** The gazetteer (plan §1.6a). `pointToleranceKm` is how close a finding must be to a POINT place
  * (a Pleiades site has no extent) to count as inside it; polygons use exact containment. The limits
  * bound what `/api/places` and the hierarchy walk return, and `seedDir` is where `histmersion places
  * seed` looks for the committed Natural Earth / Pleiades files on the HOST. */
final case class PlacesConfig(
    pointToleranceKm: Double,
    searchLimit: Int,
    childrenLimit: Int,
    treeLimit: Int,
    treeDepth: Int,
    seedDir: String,
    importBatch: Int
) derives ConfigReader

/** API key authentication. When `apiKey` is non-empty the server gates every write method
  * (POST/PUT/PATCH/DELETE) behind `Authorization: Bearer <key>`. GET/HEAD/OPTIONS (the web UI, all
  * read APIs) stay open. Empty = no auth (local-only mode). */
final case class AuthConfig(apiKey: String) derives ConfigReader

/** The native bundle: when `enabled`, the server itself runs the bundled PostgreSQL and
  * OCR converter as child processes (`com.histmersion.server.Supervisor`). `home` is the bundle root holding
  * `pg/` and `converter/` (empty = the directory above the running executable's `bin/`); `dataDir`
  * holds the cluster, logs and backups (empty = the platform's per-user data directory). */
final case class RuntimeConfig(enabled: Boolean, home: String, dataDir: String, pgPort: Int, converterPort: Int)
    derives ConfigReader

final case class AppConfig(
    server: ServerConfig,
    db: DbConfig,
    converter: ConverterConfig,
    corpus: CorpusConfig,
    backups: BackupsConfig,
    limits: Limits,
    ui: UiConfig,
    cli: CliConfig,
    query: QueryConfig,
    lint: LintConfig,
    places: PlacesConfig,
    acquire: AcquireConfig,
    auth: AuthConfig,
    runtime: RuntimeConfig
) derives ConfigReader

object AppConfig:
  /** Load config. Must run on a platform thread (main), not an Ox virtual thread: the env read uses
    * GraalVM StackValue under native-image. */
  def load(): AppConfig = load(ConfigFactory.systemEnvironment())

  /** Load with an explicit environment (tests). */
  def load(env: org.ekrich.config.Config): AppConfig =
    // Parse reference.conf and resolve ${?ENV} overrides from the given environment only — deliberately
    // NOT ConfigFactory.load(), which folds ALL system properties into HOCON (dotted keys like
    // `io.netty.allocator.type` then collide and blow up sconfig under native-image).
    val root = ConfigFactory
      .parseResources("reference.conf")
      .withFallback(env)
      .resolve()
      .getConfig("histmersion")
      .root
    ConfigReader[AppConfig].from(root) match
      case Right(cfg) => cfg
      case Left(err)  => throw new RuntimeException(s"Histmersion config error: $err")
