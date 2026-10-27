package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Configuration for the acquisition & assessment toolset (plan §5.2, stream 2.6).
  *
  * Every endpoint, key, threshold and mirror lives in `reference.conf` under `histmersion.acquire` with a
  * `${?HISTMERSION_ACQUIRE_*}` override — no URL, no e-mail and no magic number appears in the code. The
  * contact e-mail is used ONLY in the `mailto`/`email` parameters Crossref, OpenAlex and Unpaywall
  * define for it — never in a header, never anywhere else ([[HttpPolicy]], #1946: it used to ride in
  * the `User-Agent` of every acquisition request too).
  */

/** A keyless upstream service. */
final case class ServiceConfig(url: String) derives ConfigReader

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
final case class KeyedServiceConfig(url: String, apiKey: String) derives ConfigReader:
  def key: Option[String] = Option(apiKey).map(_.trim).filter(_.nonEmpty)

/** CORE (api.core.ac.uk): its endpoint, the plain key (`api-key`; the `oa-core` plugin's secret
  * reference wins over it), and the budget both CORE uses share — the acquisition chain's `core` step
  * and the new-source search's `core` index. A personal key allows 1,000 tokens a day and 25 a minute;
  * a simple query costs 1 token.
  *
  * @param perMinute             calls in any 60 s, at most
  * @param dailyTokens           tokens a UTC day, at most, both uses together
  * @param searchDailyTokens     of those, what the searches may spend: the rest stays for PDF lookups
  * @param lookupCost            tokens one DOI lookup costs (CORE may change it)
  * @param searchCost            tokens one search costs
  * @param exhaustedPauseSeconds the pause after a 429 or `X-RateLimit-Remaining: 0` without a retry time
  * @param maxWaitSeconds        how long a call waits for its slot under `perMinute`; longer, and it is
  *                              not made (the chain goes on, the sweep leaves CORE out) */
final case class CoreServiceConfig(url: String, apiKey: String, perMinute: Int, dailyTokens: Int,
    searchDailyTokens: Int, lookupCost: Int, searchCost: Int, exhaustedPauseSeconds: Int, maxWaitSeconds: Int)
    derives ConfigReader:
  def key: Option[String] = Option(apiKey).map(_.trim).filter(_.nonEmpty)

/** Retraction Watch: the CSV Crossref publishes, mirrored into the `retraction_watch` table. */
final case class RetractionWatchConfig(csvUrl: String) derives ConfigReader

/** Repository landing pages (#1580, [[RepositoryLanding]]): whether the fetch resolves them one step,
  * how many it reads per work, and the host and platform lists that decide what is a repository (each
  * comma-separated; a host matches itself and its subdomains). `denyHosts` — publishers, and sites
  * that are not repositories — wins over every other signal; `denyHostsExtra` is added to it, so a
  * user's config can deny more hosts without restating the shipped list. */
final case class LandingConfig(
    enabled: Boolean,
    maxPages: Int,
    allowHosts: String,
    denyHosts: String,
    denyHostsExtra: String,
    academicHostWords: String,
    generators: String,
    denyGenerators: String,
    denyHostWords: String,
    /** Open-access publishers whose open locations are read (#2550, [[RepositoryLanding.oaPublisherPage]]). */
    oaPublisherHosts: String,
    /** Publishers that refuse scripts: their gold open-access works go to the acquisition list as a download
      * in the browser ([[BrowserDownload]]). */
    browserDownloadHosts: String = ""
) derives ConfigReader:
  def refusingHosts: List[String] = LandingConfig.split(browserDownloadHosts)

object LandingConfig:
  def split(s: String): List[String] =
    Option(s).toList.flatMap(_.split(",")).map(_.trim.toLowerCase).filter(_.nonEmpty)

/** Which certificate authorities an acquisition request trusts beyond the JDK's own (#1568). Both are
  * opt-in and ADD roots; neither turns verification off ([[HttpPolicy.trust]]).
  *
  * @param systemTrust also trust the operating system's roots — on macOS the system keychains (read
  *                    with `security`, which works in the native image; the JDK's `KeychainStore-ROOT`
  *                    does not), on Linux the distribution's CA bundle
  * @param extraCaFile a PEM file of further roots (`~` expanded); "" = none */
final case class TlsConfig(systemTrust: Boolean, extraCaFile: String) derives ConfigReader:
  def extraCa: Option[String] =
    Option(extraCaFile).map(_.trim).filter(_.nonEmpty)
      .map(p => if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p)

  /** Anything beyond the JDK's roots asked for? */
  def custom: Boolean = systemTrust || extraCa.isDefined

/** Tunables that used to be `discover:` defaults and inline thresholds in `assess.py`. */
/** Deterministic new-source search (#3761): the `indexes` a goal's queries are read from, how far back a
  * watch reads the first time, how many results a watch reads, how many of the goal's sources (and of
  * their authors) are watched, and arXiv's API. */
final case class CandidatesConfig(indexes: List[String], lookbackDays: Int, perWatch: Int, sources: Int,
    authorsPerSource: Int, arxivApiUrl: String) derives ConfigReader

/** The acquisition list's legal re-check: on or off, the days an item's check holds, the items tried a
  * day and a pass, the minutes between passes, the share (percent) of the autopilot's shared daily
  * legal ingest allowance it may take, and the hours after which a check that got no answer is due again. */
final case class RecheckConfig(enabled: Boolean, everyDays: Int, perDay: Int, perPass: Int, passMinutes: Int,
    sharePercent: Int, retryHours: Int) derives ConfigReader

final case class AcquireDefaults(
    searchLimit: Int,      // discover search page size
    minYear: Int,          // discover: oldest publication year considered
    followupsLimit: Int,   // followups: max citing papers fetched
    archiveRows: Int,      // archive.org search rows
    wellCitedAt: Int,      // citations at/above which a source is "current" rather than "low-impact"
    lowCitedAt: Int,       // citations below which an OLD source is "low-impact"
    staleBefore: Int,      // publication year before which the low-citation rule applies
    titleMatchAt: Double   // an import PROPOSES an open acquisition item whose text scores this or more (#970)
) derives ConfigReader

/** @param spoolDir server-side directory the fetched PDFs are written to before the ingest job picks
  *                 them up; empty = the JVM's temp directory. */
final case class AcquireConfig(
    contactEmail: String,
    openalex: ServiceConfig,
    crossref: ServiceConfig,
    unpaywall: ServiceConfig,
    core: CoreServiceConfig,
    semanticScholar: KeyedServiceConfig,
    europepmc: ServiceConfig,
    pmcDataset: ServiceConfig,
    publicUserAgent: String,
    zenodo: ServiceConfig,
    datacite: ServiceConfig,
    doiHandles: ServiceConfig,
    landing: LandingConfig,
    arxiv: ServiceConfig,
    archiveOrg: ServiceConfig,
    wikipedia: ServiceConfig,
    retractionWatch: RetractionWatchConfig,
    chain: String,
    allowUnauthorised: Boolean,
    userAgent: String,
    timeoutSeconds: Int,
    spoolDir: String,
    defaults: AcquireDefaults,
    tls: TlsConfig,
    candidates: CandidatesConfig,
    recheck: RecheckConfig
) derives ConfigReader:

  /** The contact e-mail, when one is configured. It is a per-install setting (never in the repo), so
    * a blank default means "anonymous": no polite pool, and no Unpaywall, which refuses requests
    * without an e-mail. */
  def contact: Option[String] = Option(contactEmail).map(_.trim).filter(_.nonEmpty)

  /** The `mailto` value; empty without a contact, and `Url` drops an empty parameter. */
  def mailto: String = contact.getOrElse("")

  /** The acquisition chain's order: the source-fetcher ids `chain` lists (comma-separated); fetchers
    * it does not name run after these, in plugin registration order. */
  def chainIds: List[String] = LandingConfig.split(chain)

/** Host-side Calibre integration (the ONE local integration the CLI is allowed — Calibre lives on
  * the user's machine, not in the container).
  *
  * @param libraryDir   the Calibre library root (`metadata.db` lives here); `~` is expanded
  * @param calibredb    the `calibredb` executable — preferred reader/writer
  * @param sqlite       the `sqlite3` executable — read-only fallback when the Calibre GUI holds the DB
  * @param originalsDir optional host directory holding the fetched originals as `<citekey>/paper.*`,
  *                     an override for `calibre push`/`sync`, which otherwise download the
  *                     original the server stored
  */
final case class CalibreCliConfig(libraryDir: String, calibredb: String, sqlite: String, originalsDir: String)
    derives ConfigReader:

  private def expand(p: String): String =
    if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p

  def library: String   = expand(libraryDir)
  def originals: Option[String] = Option(originalsDir).map(expand).map(_.trim).filter(_.nonEmpty)
